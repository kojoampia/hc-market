package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.repository.RetentionSweepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Applies the financial retention period, by erasing customers the estate may no longer keep —
 * {@code decisions.md} D96, backlog NEW-52.
 *
 * <h2>What was missing, and what this is not</h2>
 *
 * <p>{@link PrivacyProperties} has carried counsel's ratified periods since D42 and
 * {@code GET /api/desk/privacy} reported them beside {@code enforced: false}. The old javadoc there
 * named this class's job precisely — <em>"when one exists it calls
 * {@code ErasureWorkflow.eraseCustomer} on everything past the window; the erasure semantics are
 * already decided and tested, so what is missing is the trigger and nothing else"</em> — and it was
 * right, except for the sentence beside it claiming there was no scheduler to trigger from. There has
 * been one in all five services for the estate's whole life (D91).
 *
 * <p><strong>It is deliberately a different class from {@link DisputeSlaSweep}, which is the estate's
 * other new sweep and shares only a trigger with this one.</strong> That one notifies and mutates
 * nothing; this one performs an irreversible act on a real person's record, on a timer, with nobody
 * watching. Folding two tasks together because they are both cron-driven is how the safer one's
 * defaults end up governing the dangerous one.
 *
 * <h2>Three switches, because "on" must not mean "deleting"</h2>
 *
 * <p>The sweep is <strong>off by default</strong> and, when switched on, is in <strong>dry run by
 * default</strong> — so reaching a deletion takes two independent decisions, and the first thing a
 * newly-enabled sweep does is count. {@link PrivacyProperties.Retention} holds all three values and
 * argues the shape; what matters here is the consequence: an estate that sets
 * {@code sweep-enabled=true} and nothing else gets a report every night, at WARN, naming how many
 * customers <em>would</em> be erased, and the desk still reports {@code enforced: false} because
 * nothing is.
 *
 * <h2>Why {@code SchedulingConfigurer} and not {@code @Scheduled}</h2>
 *
 * <p>D94's finding, and it is the same one here: compose's {@code ${X:-}} sets an empty variable
 * rather than leaving one unset, so {@code @Scheduled(cron = "${...:0 30 2 * * ?}")} prefers the
 * empty environment value to its own default and fails the context on every estate that passes the
 * variable through without setting it. Blank-handling lives in Java, in one place, where a test drives
 * it. There is a second reason particular to this class: <strong>an annotation cannot decline to
 * register.</strong> A {@code @Scheduled} method always runs and has to check a flag on the way in,
 * which means a disabled sweep is still a task the scheduler wakes up; this registers nothing at all
 * when the sweep is off, so "off" is the absence of a task rather than a task that returns early.
 *
 * <h2>The window is read at registration, so a bad one fails at startup</h2>
 *
 * <p>Both booleans, the cron and the presence of a financial period are checked in
 * {@link #configureTasks}, not inside the task. A value this estate cannot use is therefore a refusal
 * to start rather than a stack trace at 02:30 on a morning nobody is watching — D94's rule, and
 * {@code Retention}'s refusals are worded for the operator who reads them.
 *
 * <h2>One transaction per customer, and catching is safe here</h2>
 *
 * <p>{@link #sweep()} is <strong>not</strong> {@code @Transactional} and must not become so.
 * {@code eraseCustomer} carries its own transaction across its five tables — D31's rule that a partial
 * erasure is worse than a failed one is about one <em>person</em>, and it still holds — while the
 * sweep's loop is a sequence of them. So a customer whose erasure fails rolls back alone and the
 * remaining customers are still erased, rather than the whole night's work being abandoned at the
 * first bad row.
 *
 * <p>That is also the only reason the {@code catch} in the loop is legitimate. CLAUDE.md's standing
 * trap is that catching inside a transaction poisons it and the commit fails afterwards with an
 * {@code UnexpectedRollbackException} that names nothing; here the transaction being rolled back is
 * the inner one, which has already ended by the time control returns, so there is no outer
 * transaction to poison. <strong>Do not add {@code @Transactional} to this class.</strong>
 *
 * <h2>It counts what it erased, never what it selected</h2>
 *
 * <p>D39's rule, and the reason it is not pedantry: the count is the only record that a scheduled
 * irreversible act happened, and a receipt whose count is too large reads as "data was still exposed"
 * while one that is too small reads as "we held nothing about this person". {@link Swept} therefore
 * reports the selected total and the erased total <em>separately</em> — equal on a clean run, and
 * different in exactly the case worth seeing.
 *
 * <p>Each erasure records itself on the {@code erased_subject} register, in the same transaction, for
 * free: this calls the same {@code eraseCustomer} the desk calls, so a swept erasure is registered
 * like any other (D39). <strong>What the register cannot say is WHY</strong> — it holds an alias and
 * a timestamp and nothing about whether a person asked or a clock expired. That is a real gap and it
 * is D96's surfaced decision rather than something invented here; see backlog NEW-67.
 */
@Component
public class RetentionSweep implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionSweep.class);

    private final RetentionSweepRepository candidates;

    private final ErasureWorkflow erasure;

    private final PrivacyProperties privacy;

    public RetentionSweep(RetentionSweepRepository candidates, ErasureWorkflow erasure, PrivacyProperties privacy) {
        this.candidates = candidates;
        this.erasure = erasure;
        this.privacy = privacy;
    }

    /**
     * Registers the sweep, or deliberately registers nothing.
     *
     * <p>Every value is read here so an unusable one is a startup failure. The financial period is
     * checked for presence only when the sweep is enabled: an estate that is not sweeping has no need
     * of a window, and refusing to start over a blanked number nothing reads would be a guard that
     * fires on a correct state.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        PrivacyProperties.Retention retention = privacy.getRetention();
        if (!retention.sweepEnabled()) {
            LOG.info(
                "retention: no sweep registered — healthconnect.privacy.retention.sweep-enabled is false, so the " +
                    "financial and operational periods are a stated policy and nothing applies them (decisions.md D96)"
            );
            return;
        }
        Duration window = financialWindow(retention);
        String cron = retention.sweepCron();
        boolean dryRun = retention.sweepDryRun();
        LOG.warn(
            "retention: sweeping on '{}' — customers with no booking activity for {} days will be {} (decisions.md D96)",
            cron,
            window.toDays(),
            dryRun ? "COUNTED and not erased (dry run)" : "ERASED, IRREVERSIBLY"
        );
        registrar.addCronTask(this::sweep, cron);
    }

    /**
     * One run. Selects the customers the window has run out for and — unless this is a dry run —
     * erases each of them.
     *
     * <p>Package-private on purpose, and there is deliberately <strong>no endpoint</strong> that calls
     * it. An HTTP door onto a retention sweep is a way to erase somebody's record a day early, on an
     * estate where the desk's own erasure is already one request away for the case where that is
     * wanted. The only callers are this class's registered task and the tests that put the boundary
     * where they need it.
     *
     * @return what it selected and what it erased — see {@link Swept}, and note the two are not the
     *         same number by construction.
     */
    Swept sweep() {
        PrivacyProperties.Retention retention = privacy.getRetention();
        Duration window = financialWindow(retention);
        Instant cutoff = Instant.now().minus(window);
        return sweepAsAt(cutoff, retention.sweepDryRun());
    }

    /**
     * The run itself, taking the cutoff and the mode as parameters so a test can put the boundary
     * exactly on it without moving a clock or waiting six years.
     *
     * @param cutoff a customer with any booking activity at or after this instant is left alone.
     * @param dryRun when true, nothing is erased and {@link Swept#erased()} is zero.
     */
    Swept sweepAsAt(Instant cutoff, boolean dryRun) {
        List<String> logins = candidates.customersWithNoActivitySince(cutoff);
        if (logins.isEmpty()) {
            LOG.debug("retention: no customer is past the financial window as at {}", cutoff);
            return new Swept(0, 0);
        }
        if (dryRun) {
            /* THE COUNT AND NOT THE LOGINS. A dry run exists so somebody can see the size of what is
               about to happen, and printing the logins would put the identities of the people about
               to be erased into a log that outlives them — which is the one place the erasure cannot
               reach. D39's register holds aliases for exactly this reason. */
            LOG.warn(
                "retention: DRY RUN — {} customer(s) have no booking activity since {} and WOULD be erased. " +
                    "Nothing was changed. Set healthconnect.privacy.retention.sweep-dry-run=false to apply " +
                    "(decisions.md D96)",
                logins.size(),
                cutoff
            );
            return new Swept(logins.size(), 0);
        }
        int erased = 0;
        for (String login : logins) {
            try {
                erasure.eraseCustomer(login);
                erased++;
            } catch (RuntimeException failed) {
                /* Safe to catch: see the class comment. This method is not transactional, so the
                   transaction that just rolled back was eraseCustomer's own and has already ended.
                   The login is NOT logged — it is the identity this task exists to remove — so the
                   alias is, which is what an operator needs to find the rows and is what the register
                   would hold anyway. */
                LOG.error(
                    "retention: erasure failed for one customer (alias {}); {} of {} erased so far, continuing",
                    safeAlias(login),
                    erased,
                    logins.size(),
                    failed
                );
            }
        }
        LOG.warn(
            "retention: erased {} of {} customer(s) with no booking activity since {} — irreversible, recorded on " +
                "the erased-subject register (decisions.md D39, D96)",
            erased,
            logins.size(),
            cutoff
        );
        return new Swept(logins.size(), erased);
    }

    /**
     * The alias, for a log line about a failure, or a placeholder if even that cannot be derived.
     *
     * <p>{@code pseudonym} throws when no pepper is configured (D35), and a failure log is the last
     * place to throw a second exception out of. An unpeppered estate cannot erase at all, so this
     * branch means the sweep was enabled on a deployment missing its pepper — which the message says.
     */
    private String safeAlias(String login) {
        try {
            return erasure.pseudonym(login);
        } catch (RuntimeException noPepper) {
            return "<un-derivable: healthconnect.privacy.pepper is not set>";
        }
    }

    /**
     * The financial period as a {@link Duration}, refusing an absent one.
     *
     * <p>Only the financial category is applied here, and that is a scope statement rather than an
     * omission: this is booking, which holds bookings and disputes — the financial rows. The
     * operational period (message bodies, notifications, conversations) governs <strong>messaging's
     * tables</strong>, is six times shorter, and is not enforced by anything. Sweeping it from here
     * by fanning the erasure out would apply a six-year clock to data whose stated period is one
     * year, and file a receipt saying so. Backlog NEW-68.
     */
    private Duration financialWindow(PrivacyProperties.Retention retention) {
        Integer days = retention.getFinancialDays();
        if (days == null || days < 1) {
            throw new IllegalStateException(
                (
                    "healthconnect.privacy.retention.financial-days is '%s', and the retention sweep is enabled. " +
                    "The sweep erases every customer with no booking activity for that many days, so an absent or " +
                    "non-positive window would erase the whole customer base on its first run. Set the period, or " +
                    "set healthconnect.privacy.retention.sweep-enabled=false (decisions.md D42, D96)"
                ).formatted(days)
            );
        }
        return Duration.ofDays(days);
    }

    /**
     * @param selected customers the window had run out for.
     * @param erased how many of them were actually erased — <strong>zero on a dry run</strong>, and
     *     below {@code selected} on a run where an individual erasure failed. Reported separately
     *     rather than as one number because D39's rule is that the count is the record: a single
     *     figure could not tell "nothing was eligible" from "nothing was applied", and those are
     *     opposite facts about an estate's compliance.
     */
    public record Swept(int selected, int erased) {}
}
