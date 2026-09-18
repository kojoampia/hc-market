package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jojoaddison.repository.RetentionSweepRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.annotation.Transactional;

/**
 * One customer's erasure failing does not abandon the others — {@code decisions.md} D96, and a review
 * finding against the first cut of it.
 *
 * <h2>Why this is a plain unit test and why {@code RetentionSweepIT} could not do it</h2>
 *
 * <p><strong>{@code RetentionSweepIT} is structurally incapable of asserting this property, which is
 * why it is here.</strong> That class is {@code @Transactional}, and
 * {@link ErasureWorkflow#eraseCustomer(String)} is {@code @Transactional} with the default
 * {@code REQUIRED} propagation — so inside the IT it <em>joins the test's</em> transaction rather than
 * opening one of its own. The one-transaction-per-customer behaviour the whole design rests on is
 * therefore invisible from there: every assertion in that file would stay green whether the sweep
 * committed per customer or in one lump.
 *
 * <p>So this uses no Spring context at all. With no transaction manager anywhere near it, the only
 * thing under test is {@link RetentionSweep}'s own control flow, which is exactly the subject.
 *
 * <h2>What would go wrong without it</h2>
 *
 * <p>The failure this guards is an edit {@link RetentionSweep}'s own javadoc forbids in as many words
 * — adding {@code @Transactional} to that class. An erasure failing on the third of ten customers
 * would then roll all ten back at commit, while {@code Swept.erased} reported nine and the WARN line
 * claimed nine customers erased <em>and registered</em>. The {@code erased_subject} rows would go with
 * the rollback. That is a count-as-record wrong in D39's worst direction — a receipt asserting an
 * irreversible act that did not happen — and before this test it was asserted by nothing.
 *
 * <p>Hence the second case, which pins the annotation directly rather than its consequences.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneFailedErasureDoesNotAbandonTheSweepTest {

    private static final Instant CUTOFF = Instant.parse("2020-01-01T00:00:00Z");

    private static final List<String> FIVE = List.of("one", "two", "three", "four", "five");

    /** The middle one, so a sweep that stops early leaves work on both sides of the failure. */
    private static final String POISON = "three";

    @Mock
    private RetentionSweepRepository candidates;

    @Mock
    private PrivacyProperties privacy;

    /**
     * Four of five are erased and the sweep keeps going past the failure.
     *
     * <p>Red-first: against a {@code sweep} whose loop does not catch — or catches and
     * {@code break}s — this fails reporting {@code erased} as 2 and the asked-for list as the first
     * three logins, because the exception escapes on {@code "three"} and {@code "four"} and
     * {@code "five"} are never attempted.
     *
     * <p>Both halves are asserted deliberately. The count alone would pass a sweep that swallowed the
     * exception and skipped the rest; the asked-for list alone would pass one that attempted everybody
     * and counted the failure as a success. Together they say: it tried all five and counted four.
     */
    @Test
    @DisplayName("an erasure that throws is counted as not done, and the rest are still erased")
    void oneFailureDoesNotStopTheRest() {
        List<String> asked = new ArrayList<>();
        ErasureWorkflow erasure = mock(ErasureWorkflow.class);
        when(erasure.eraseCustomer(anyString())).thenAnswer(call -> {
            String login = call.getArgument(0);
            asked.add(login);
            if (POISON.equals(login)) {
                // What a rolled-back per-customer transaction actually surfaces as. The type is not
                // the point — RetentionSweep catches RuntimeException — but a DataAccessException
                // subclass is the honest stand-in for the real failure.
                throw new org.springframework.dao.DataIntegrityViolationException("the database refused this one");
            }
            return new ErasureWorkflow.Erased(1, 0, 0, 0);
        });
        when(erasure.pseudonym(anyString())).thenReturn("erased-deadbeefdeadbeef");
        when(candidates.customersWithNoActivitySince(CUTOFF)).thenReturn(FIVE);

        RetentionSweep.Swept swept = new RetentionSweep(candidates, erasure, privacy).sweepAsAt(CUTOFF, false);

        assertThat(asked).as("every customer is attempted, including the two after the failure").isEqualTo(FIVE);
        assertThat(swept.selected()).isEqualTo(5);
        assertThat(swept.erased()).as("D39: rows that CHANGED — the failed one is not one of them").isEqualTo(4);
    }

    /**
     * And the failure is reported rather than swallowed silently.
     *
     * <p>Asserted through the alias derivation rather than by reading the log: the sweep asks
     * {@code pseudonym} only on the failure path, so being asked once is evidence that the failure was
     * reported and that the line named the alias instead of the login it is erasing.
     */
    @Test
    @DisplayName("the failure is logged by alias, never by login")
    void theFailureIsReportedByAlias() {
        ErasureWorkflow erasure = mock(ErasureWorkflow.class);
        when(erasure.eraseCustomer(anyString())).thenThrow(new IllegalStateException("no"));
        when(erasure.pseudonym(anyString())).thenReturn("erased-deadbeefdeadbeef");
        when(candidates.customersWithNoActivitySince(CUTOFF)).thenReturn(List.of("ama.customer"));

        RetentionSweep.Swept swept = new RetentionSweep(candidates, erasure, privacy).sweepAsAt(CUTOFF, false);

        assertThat(swept.erased()).isZero();
        verify(erasure).pseudonym("ama.customer");
    }

    /**
     * An unpeppered estate cannot derive an alias, and the failure path must not throw a second
     * exception out of itself.
     *
     * <p>{@code SubjectPseudonym.of} throws when no pepper is configured (D35). A failure log is the
     * last place to raise from, so {@code safeAlias} catches it — and this asserts the sweep still
     * returns rather than propagating, because the alternative is one bad customer taking the whole
     * run down through the error handler rather than through the error.
     */
    @Test
    @DisplayName("a failure on an unpeppered estate still returns a count rather than throwing")
    void anUnderivableAliasDoesNotThrowOutOfTheFailurePath() {
        ErasureWorkflow erasure = mock(ErasureWorkflow.class);
        when(erasure.eraseCustomer(anyString())).thenThrow(new IllegalStateException("no"));
        when(erasure.pseudonym(anyString())).thenThrow(new IllegalStateException("no pepper configured"));
        when(candidates.customersWithNoActivitySince(CUTOFF)).thenReturn(List.of("ama.customer", "kofi.customer"));

        RetentionSweep.Swept swept = new RetentionSweep(candidates, erasure, privacy).sweepAsAt(CUTOFF, false);

        assertThat(swept.selected()).isEqualTo(2);
        assertThat(swept.erased()).isZero();
    }

    /**
     * DISABLING THE SWEEP ON A RUNNING ESTATE STOPS IT ERASING. The kill switch.
     *
     * <p>{@code configureTasks} runs once at startup, so the registered task outlives a rebind — and
     * booking carries {@code spring-cloud-starter-consul-config} with its watch on by default, so a
     * Consul KV write rebinds these properties with no HTTP endpoint involved. Without the check at the
     * top of {@code sweep()} an operator who switched the sweep off on a running estate would be
     * silently ignored and the nightly erasure would continue, which is the wrong direction for an
     * irreversible act.
     *
     * <p>Red-first: with that check removed this fails — one customer is erased despite the sweep being
     * disabled. Note the <em>opposite</em> is deliberately not possible: enabling by rebind registers
     * no task, so a sweep that was off at startup stays off until a restart.
     */
    @Test
    @DisplayName("a sweep disabled after startup erases nothing on its next run")
    void disablingItOnARunningEstateStopsIt() {
        ErasureWorkflow erasure = mock(ErasureWorkflow.class);
        when(erasure.eraseCustomer(anyString())).thenReturn(new ErasureWorkflow.Erased(1, 0, 0, 0));
        /* THE REPOSITORY MUST ANSWER WITH SOMEBODY, or this case proves nothing. `sweep()` computes
           its own cutoff from the clock, so the stub matches any instant; an unstubbed mock returns an
           empty list and the assertions below would then hold for a sweep with no kill switch at all.
           That is not hypothetical — this test was written that way first and passed against the
           mutation it exists to catch. */
        when(candidates.customersWithNoActivitySince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of("kofi.longgone"));
        PrivacyProperties disabledNow = new PrivacyProperties();
        disabledNow.getRetention().setFinancialDays(2190);
        disabledNow.getRetention().setSweepEnabled("false");
        disabledNow.getRetention().setSweepDryRun("false");

        RetentionSweep.Swept swept = new RetentionSweep(candidates, erasure, disabledNow).sweep();

        assertThat(swept.selected()).as("it must not even ask who is eligible").isZero();
        assertThat(swept.erased()).isZero();
        verify(erasure, never()).eraseCustomer(anyString());
    }

    /**
     * The control for the case above: with the sweep ENABLED and the same stub, it does erase.
     *
     * <p>Without this, {@code disablingItOnARunningEstateStopsIt} could pass for a reason that has
     * nothing to do with the switch — an unstubbed repository, a mis-typed matcher, a sweep that never
     * erases anybody at all. It is the assertion that makes the zero above mean something.
     */
    @Test
    @DisplayName("the same fixture with the sweep enabled does erase — the control")
    void theControlForTheKillSwitch() {
        ErasureWorkflow erasure = mock(ErasureWorkflow.class);
        when(erasure.eraseCustomer(anyString())).thenReturn(new ErasureWorkflow.Erased(1, 0, 0, 0));
        when(candidates.customersWithNoActivitySince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of("kofi.longgone"));
        PrivacyProperties enabled = new PrivacyProperties();
        enabled.getRetention().setFinancialDays(2190);
        enabled.getRetention().setSweepEnabled("true");
        enabled.getRetention().setSweepDryRun("false");

        RetentionSweep.Swept swept = new RetentionSweep(candidates, erasure, enabled).sweep();

        assertThat(swept.selected()).isEqualTo(1);
        assertThat(swept.erased()).isEqualTo(1);
        verify(erasure).eraseCustomer("kofi.longgone");
    }

    /**
     * THE ANNOTATION ITSELF, pinned — because the three cases above pass with or without it.
     *
     * <p>They construct the class directly, so no proxy exists and no transaction manager is involved;
     * a {@code @Transactional} added to {@link RetentionSweep} would leave all three green and change
     * the behaviour completely in production. The javadoc says "do not add {@code @Transactional} to
     * this class" and this is what makes that sentence enforceable.
     *
     * <p>Method level as well as class level: {@code @Transactional} on {@code sweep} or
     * {@code sweepAsAt} has the same effect through the proxy and is the likelier edit, since it looks
     * narrower and therefore safer.
     */
    @Test
    @DisplayName("RetentionSweep is not transactional, at the class or on either sweep method")
    void theSweepIsNotTransactional() {
        assertThat(RetentionSweep.class.getAnnotation(Transactional.class))
            .as("one transaction per customer is the design — a class-level transaction rolls the whole night back")
            .isNull();

        assertThat(RetentionSweep.class.getDeclaredMethods())
            .filteredOn(m -> m.getAnnotation(Transactional.class) != null)
            .as("nor on sweep()/sweepAsAt(), which reaches the same outcome through the proxy")
            .isEmpty();
    }
}
