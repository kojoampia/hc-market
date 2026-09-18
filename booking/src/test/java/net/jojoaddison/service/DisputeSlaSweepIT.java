package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.DisputeStatusChange;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import net.jojoaddison.repository.DisputeSlaRepository;
import net.jojoaddison.repository.DisputeStatusChangeRepository;
import net.jojoaddison.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.annotation.Transactional;

/**
 * The overdue-dispute sweep — {@code decisions.md} D96, backlog NEW-52.
 *
 * <p>The load-bearing assertion here is a <strong>negative</strong> one: this sweep reports and must
 * change nothing. A dispute's status is the desk's to move and its history table is append-only
 * evidence of acts somebody took (D34/D39), so a sweep that helpfully stamped an "overdue" transition
 * would be writing an audit row for an act nobody performed — and a deadline passing is not an act.
 *
 * <p>{@code @Transactional} rolls each case back so these disputes do not join the estate every other
 * IT in this JVM shares.
 */
@IntegrationTest
@Transactional
class DisputeSlaSweepIT {

    private static final String OVERDUE = "d-overdue1";
    private static final String OVERDUE_WORSE = "d-overdue2";
    private static final String IN_TIME = "d-intime01";

    @Autowired
    private DisputeSlaSweep sweep;

    @Autowired
    private DisputeSlaRepository overdue;

    @Autowired
    private DisputeRepository disputes;

    @Autowired
    private DisputeStatusChangeRepository history;

    @Autowired
    private EntityManager em;

    private Instant now;

    @BeforeEach
    void threeDisputes() {
        now = Instant.now();
        save(OVERDUE, DisputeStatus.OPEN, now.minus(3, ChronoUnit.DAYS));
        save(OVERDUE_WORSE, DisputeStatus.UNDER_REVIEW, now.minus(11, ChronoUnit.DAYS));
        save(IN_TIME, DisputeStatus.OPEN, now.plus(2, ChronoUnit.DAYS));
        em.flush();
    }

    private Dispute save(String reference, DisputeStatus status, Instant dueBy) {
        return disputes.saveAndFlush(
            new Dispute()
                .reference(reference)
                .bookingReference("b-" + reference)
                .raisedBy(CancelledBy.CUSTOMER)
                .raisedByLogin("ama.customer")
                .professionalRef("p1")
                .reason("the session did not happen as agreed")
                .status(status)
                .raisedAt(dueBy.minus(5, ChronoUnit.DAYS))
                .dueBy(dueBy)
                .currency("GHS")
        );
    }

    /**
     * It finds the late ones, worst first, and leaves the one still in time alone.
     *
     * <p>{@code UNDER_REVIEW} is included deliberately: a dispute that has been <em>looked at</em> and
     * not decided has still missed the promise. Red-first: a query filtering on {@code OPEN} alone
     * fails here reporting 1.
     */
    @Test
    @DisplayName("both unresolved statuses count as overdue, oldest deadline first")
    void reportsTheLateOnesWorstFirst() {
        DisputeSlaSweep.Overdue reported = sweep.sweepAsAt(now);

        assertThat(reported.count()).isEqualTo(2);
        assertThat(reported.references()).containsExactly(OVERDUE_WORSE, OVERDUE).doesNotContain(IN_TIME);
    }

    /**
     * IT NOTIFIES AND MUTATES NOTHING. NEW-52's requirement for this half.
     *
     * <p>Both halves are asserted, because they fail differently: a status change is visible on the
     * dispute, and a history row is visible only in a table nothing else here reads. The history count
     * is taken before and after rather than asserted as zero, since the fixture writes none and a
     * later fixture might.
     */
    @Test
    @DisplayName("the sweep changes no dispute and writes no audit row")
    void reportingIsNotMutating() {
        long historyBefore = history.count();
        List<DisputeStatus> before = disputes.findAll().stream().map(Dispute::getStatus).toList();

        sweep.sweepAsAt(now);
        em.flush();
        em.clear();

        assertThat(disputes.findAll().stream().map(Dispute::getStatus))
            .as("a deadline passing is not a transition")
            .containsExactlyInAnyOrderElementsOf(before);
        assertThat(history.count()).as("D34/D39: that table records acts, and this is not one").isEqualTo(historyBefore);
        assertThat(disputes.findAll().stream().map(Dispute::getResolution)).as("nothing resolved itself").containsOnlyNulls();
    }

    /** Nothing overdue is a quiet answer and an empty list, not a null and not an error. */
    @Test
    @DisplayName("an estate with nothing overdue reports zero")
    void nothingOverdueIsZero() {
        DisputeSlaSweep.Overdue reported = sweep.sweepAsAt(now.minus(30, ChronoUnit.DAYS));

        assertThat(reported.count()).isZero();
        assertThat(reported.references()).isEmpty();
    }

    /**
     * The boundary: exactly on the deadline is NOT yet overdue.
     *
     * <p>{@code dueBy < :now}, so a dispute has until its deadline rather than up to the moment
     * before it. Asserted because the equal case has to be decided somewhere, and a promise should not
     * be reported as broken on the instant it comes due.
     */
    @Test
    @DisplayName("a dispute exactly on its deadline is not yet overdue")
    void theDeadlineItselfIsNotLate() {
        Instant due = now.minus(3, ChronoUnit.DAYS);

        assertThat(overdue.overdueAt(due)).extracting(Dispute::getReference).doesNotContain(OVERDUE);
        assertThat(overdue.overdueAt(due.plusMillis(1))).extracting(Dispute::getReference).contains(OVERDUE);
    }

    /** On by default, unlike the retention sweep — the asymmetry is the decision. */
    @Test
    @DisplayName("an estate that configures nothing does report overdue disputes")
    void onByDefault() {
        var registrar = new ScheduledTaskRegistrar();
        new DisputeSlaSweep(overdue, new DisputeSlaProperties()).configureTasks(registrar);

        assertThat(registrar.getCronTaskList()).hasSize(1);
        assertThat(registrar.getCronTaskList().get(0).getExpression()).isEqualTo(DisputeSlaProperties.DEFAULT_OVERDUE_SWEEP_CRON);
    }

    /** And it can be silenced by an estate digging out from under a backlog of them. */
    @Test
    @DisplayName("a disabled sweep registers nothing")
    void offMeansNoTask() {
        var registrar = new ScheduledTaskRegistrar();
        DisputeSlaProperties off = new DisputeSlaProperties();
        off.setOverdueSweepEnabled("false");

        new DisputeSlaSweep(overdue, off).configureTasks(registrar);

        assertThat(registrar.getCronTaskList()).isEmpty();
    }

    /**
     * An unreadable switch refuses rather than being guessed.
     *
     * <p>Milder harm than the retention sweep's twin — a wrong value here costs a warning that does or
     * does not appear — and refused for D57's reason anyway: a value somebody set and this estate
     * silently ignored is worse than a startup that says so.
     */
    @Test
    @DisplayName("an unreadable switch or cron refuses startup")
    void unreadableValuesRefuse() {
        DisputeSlaProperties typo = new DisputeSlaProperties();
        typo.setOverdueSweepEnabled("ture");
        assertThatThrownBy(typo::overdueSweepEnabled).isInstanceOf(IllegalStateException.class).hasMessageContaining("ture");

        DisputeSlaProperties badCron = new DisputeSlaProperties();
        badCron.setOverdueSweepCron("mornings");
        assertThatThrownBy(badCron::overdueSweepCron).isInstanceOf(IllegalStateException.class).hasMessageContaining("six-field cron");
    }

    /**
     * The two sweeps are two beans, and that is asserted rather than assumed.
     *
     * <p>NEW-52 says in as many words: <em>do not build them as one thing because they share a
     * trigger</em>. A later refactor folding the retention sweep's task into this class would pass
     * every behavioural assertion in both files — they would all still be true — so the separation
     * needs an assertion of its own, and this is the cheapest honest one: two distinct
     * {@code SchedulingConfigurer}s, neither of which is the other.
     */
    @Test
    @DisplayName("the two sweeps are separate beans with separate configuration")
    void twoSweepsNotOne() {
        assertThat(DisputeSlaSweep.class).isNotEqualTo(RetentionSweep.class);
        assertThat(DisputeSlaSweep.class.getDeclaredFields())
            .as("this sweep reads the dispute properties and must not reach for the privacy policy")
            .noneMatch(f -> f.getType().equals(PrivacyProperties.class));
        assertThat(RetentionSweep.class.getDeclaredFields())
            .as("and the retention sweep must not reach for the dispute properties")
            .noneMatch(f -> f.getType().equals(DisputeSlaProperties.class));
    }
}
