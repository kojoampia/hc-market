package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.repository.ErasedSubjectRepository;
import net.jojoaddison.repository.RetentionSweepRepository;
import net.jojoaddison.web.rest.BookingResourceIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The retention sweep against a real database — {@code decisions.md} D96, backlog NEW-52.
 *
 * <h2>Why the boundary cases are ITs rather than unit tests</h2>
 *
 * <p>Because the thing that decides who is erased is a JPQL query with two correlated subqueries, and
 * a mock of the repository would assert the shape of a call rather than the answer. The eligibility
 * rule is the design — <em>a customer is kept if any instant on any of their bookings is inside the
 * window</em> — and it exists in the query and nowhere else.
 *
 * <h2>The mode is passed in, not configured</h2>
 *
 * <p>{@link RetentionSweep#sweepAsAt(Instant, boolean)} takes the cutoff and the dry-run flag as
 * parameters, so these cases put the boundary exactly on an instant without moving a clock, waiting
 * six years, or restarting a context per case with a different {@code @TestPropertySource}. What the
 * <em>defaults</em> are is {@code TheRetentionSweepIsOffUntilTwoDecisionsUnitTest}'s subject, where no
 * config file can make the answer vacuous; the two halves are deliberately not tested in one place.
 *
 * <p>{@code @Transactional} on the class rolls each case back, which is what keeps these from
 * polluting the estate of bookings every other IT in this JVM shares.
 */
@IntegrationTest
@Transactional
class RetentionSweepIT {

    /** Six years, counsel's ratified financial period — the window this sweep applies. */
    private static final int FINANCIAL_DAYS = 2190;

    private static final String LONG_GONE = "kofi.longgone";
    private static final String STILL_RECENT = "adwoa.stillhere";

    @Autowired
    private RetentionSweep sweep;

    @Autowired
    private RetentionSweepRepository candidates;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ErasedSubjectRepository register;

    @Autowired
    private SubjectPseudonym pseudonyms;

    @Autowired
    private EntityManager em;

    private Instant cutoff;

    /**
     * Two customers on either side of the window, and one of them has a second booking.
     *
     * <p>{@link #STILL_RECENT}'s two bookings are the case the eligibility rule exists for: one of
     * them is older than {@link #LONG_GONE}'s, so a sweep that selected <em>bookings</em> rather than
     * customers would erase this person's last-week booking along with it.
     */
    @BeforeEach
    void twoCustomersEitherSideOfTheWindow() {
        cutoff = Instant.now().minus(FINANCIAL_DAYS, ChronoUnit.DAYS);

        save(LONG_GONE, cutoff.minus(40, ChronoUnit.DAYS), cutoff.minus(39, ChronoUnit.DAYS));
        save(STILL_RECENT, cutoff.minus(100, ChronoUnit.DAYS), cutoff.minus(99, ChronoUnit.DAYS));
        save(STILL_RECENT, cutoff.plus(2, ChronoUnit.DAYS), null);
        em.flush();
    }

    private Booking save(String login, Instant raisedAt, Instant completedAt) {
        return bookings.saveAndFlush(
            BookingResourceIT.createEntity(em)
                .customerLogin(login)
                .customerName("Test Person")
                .visitAddress("14 Nii Boi Ave, Accra")
                .raisedAt(raisedAt)
                .respondedAt(null)
                .completedAt(completedAt)
                .cancelledAt(null)
        );
    }

    /**
     * A DRY RUN DELETES NOTHING AND RETURNS A COUNT. The requirement NEW-52 states in as many words.
     *
     * <p>Red-first: with the dry-run branch removed from {@code sweepAsAt} this fails on the register
     * assertion — a row appears for the alias — and on {@code erased()}, which reports 1 for a run
     * that was asked to change nothing.
     */
    @Test
    @DisplayName("a dry run reports what would go and erases nothing")
    void aDryRunChangesNothing() {
        RetentionSweep.Swept swept = sweep.sweepAsAt(cutoff, true);

        assertThat(swept.selected()).as("the count an operator needs before deciding").isEqualTo(1);
        assertThat(swept.erased()).as("a dry run erases nothing, by definition").isZero();

        // The row is untouched: the login is still the login, and the address is still there.
        Booking untouched = bookings.findAll().stream().filter(b -> LONG_GONE.equals(b.getCustomerLogin())).findFirst().orElseThrow();
        assertThat(untouched.getCustomerName()).isEqualTo("Test Person");
        assertThat(untouched.getVisitAddress()).isNotNull();
        assertThat(register.existsById(pseudonyms.of(LONG_GONE))).as("nothing was recorded, because nothing happened").isFalse();
    }

    /**
     * A REAL RUN ERASES, AND IS RECORDED ON THE REGISTER. NEW-52's third requirement, and D39's rule.
     *
     * <p>The register assertion is the one that would be quietly lost: the sweep gets it for free by
     * calling the same {@code eraseCustomer} the desk calls, so a well-meant "faster" sweep that
     * redacted rows directly would pass every other assertion here and leave the estate with no record
     * that an irreversible act had happened on a timer.
     */
    @Test
    @DisplayName("a real run erases the customer, counts what it changed, and registers the erasure")
    void aRealRunErasesAndRecords() {
        RetentionSweep.Swept swept = sweep.sweepAsAt(cutoff, false);

        assertThat(swept.selected()).isEqualTo(1);
        assertThat(swept.erased()).as("rows that CHANGED, never rows that matched — D39").isEqualTo(1);

        String alias = pseudonyms.of(LONG_GONE);
        assertThat(register.existsById(alias)).as("D39: an irreversible act leaves a durable record").isTrue();
        assertThat(bookings.findAll().stream().map(Booking::getCustomerLogin)).doesNotContain(LONG_GONE).contains(alias);

        Booking erased = bookings.findAll().stream().filter(b -> alias.equals(b.getCustomerLogin())).findFirst().orElseThrow();
        assertThat(erased.getCustomerName()).isEqualTo("[erased]");
        assertThat(erased.getVisitAddress()).isNull();
        assertThat(erased.getReference()).as("everything the rest of the estate keys on survives").isNotBlank();
    }

    /**
     * THE BOUNDARY. Past the window goes; just inside it stays.
     *
     * <p>This is the assertion the whole design turns on, and the one a plausible query gets wrong:
     * {@link #STILL_RECENT} has a booking <em>older</em> than the erased customer's, so a sweep that
     * selected old bookings and erased their customers would take this person too — and the receipt
     * would say 2, which is the count that reads as "data was still exposed" in the other direction.
     */
    @Test
    @DisplayName("a customer with any booking inside the window is left alone, however old their others are")
    void oneRecentBookingKeepsTheWholeCustomer() {
        assertThat(candidates.customersWithNoActivitySince(cutoff)).containsExactly(LONG_GONE);

        sweep.sweepAsAt(cutoff, false);

        assertThat(bookings.findAll().stream().map(Booking::getCustomerLogin))
            .as("their recent booking protects their old one")
            .contains(STILL_RECENT);
        assertThat(register.existsById(pseudonyms.of(STILL_RECENT))).isFalse();
    }

    /**
     * Exactly on the cutoff is INSIDE the window.
     *
     * <p>The query says {@code >= :cutoff} keeps a person, so a booking whose only instant is the
     * cutoff itself survives. Which side of the boundary the equal case falls on matters less than it
     * being decided and asserted — an off-by-one in an irreversible sweep is a day of somebody's
     * records — and the inclusive reading is the safe one.
     */
    @Test
    @DisplayName("activity exactly on the cutoff counts as inside the window")
    void theEqualCaseIsKept() {
        save("esi.ontheline", cutoff, null);
        em.flush();

        assertThat(candidates.customersWithNoActivitySince(cutoff)).doesNotContain("esi.ontheline");
    }

    /**
     * An erasure already done is not done again.
     *
     * <p>Without the register exclusion in the query the alias — which inherits the row's six-year-old
     * instants — is selected on the next run and erased in its turn, deriving an HMAC <em>of the
     * alias</em>: a second pseudonym for the same person, every night, destroying the stable grouping
     * D34/D35 built it for. Red-first: dropping that subquery makes this fail with a second alias
     * present and the register holding two rows for one person.
     */
    @Test
    @DisplayName("an already-erased subject is not swept again into a second alias")
    void anErasedSubjectIsNotReErased() {
        sweep.sweepAsAt(cutoff, false);
        String alias = pseudonyms.of(LONG_GONE);

        assertThat(candidates.customersWithNoActivitySince(cutoff)).as("the alias must not come back round").isEmpty();

        RetentionSweep.Swept second = sweep.sweepAsAt(cutoff, false);
        assertThat(second.selected()).isZero();
        assertThat(second.erased()).isZero();
        assertThat(bookings.findAll().stream().map(Booking::getCustomerLogin)).contains(alias).doesNotContain(pseudonyms.of(alias));
    }

    /**
     * An enabled sweep with no window refuses rather than erasing everybody.
     *
     * <p>{@code financial-days} is blankable, and an absent window would make the cutoff {@code now},
     * which selects the entire customer base on the first run. Asserted through the registration path
     * because that is where the check belongs — at startup, not at 02:30.
     */
    @Test
    @DisplayName("an enabled sweep with no financial period refuses instead of computing a cutoff of now")
    void anEnabledSweepNeedsAWindow() {
        PrivacyProperties bare = new PrivacyProperties();
        bare.getRetention().setSweepEnabled("true");
        RetentionSweep unconfigured = new RetentionSweep(candidates, null, bare);

        assertThatThrownBy(() -> unconfigured.configureTasks(new org.springframework.scheduling.config.ScheduledTaskRegistrar()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("financial-days")
            .hasMessageContaining("sweep-enabled=false");
    }

    /**
     * A disabled sweep registers no task at all.
     *
     * <p>"Off" is the absence of a task rather than a task that returns early, which is the second
     * reason {@code SchedulingConfigurer} was chosen over {@code @Scheduled} — an annotation cannot
     * decline to register. Red-first: fails with one task registered if the early return is removed.
     */
    @Test
    @DisplayName("a disabled sweep registers nothing with the scheduler")
    void offMeansNoTask() {
        var registrar = new org.springframework.scheduling.config.ScheduledTaskRegistrar();
        PrivacyProperties off = new PrivacyProperties();
        off.getRetention().setFinancialDays(FINANCIAL_DAYS);

        new RetentionSweep(candidates, null, off).configureTasks(registrar);

        assertThat(registrar.getCronTaskList()).as("nothing scheduled, not something scheduled that does nothing").isEmpty();
    }

    /** And an enabled one does register, on the configured expression. */
    @Test
    @DisplayName("an enabled sweep registers one cron task on the configured expression")
    void onMeansOneTask() {
        var registrar = new org.springframework.scheduling.config.ScheduledTaskRegistrar();
        PrivacyProperties on = new PrivacyProperties();
        on.getRetention().setFinancialDays(FINANCIAL_DAYS);
        on.getRetention().setSweepEnabled("true");
        on.getRetention().setSweepCron("0 15 3 * * ?");

        new RetentionSweep(candidates, null, on).configureTasks(registrar);

        assertThat(registrar.getCronTaskList()).hasSize(1);
        assertThat(registrar.getCronTaskList().get(0).getExpression()).isEqualTo("0 15 3 * * ?");
    }
}
