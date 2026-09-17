package net.jojoaddison.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * How long a registered-but-never-activated account is kept, and when the sweep that removes it runs
 * — {@code healthconnect.accounts}, {@code decisions.md} D94, backlog NEW-47.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>JHipster's generated {@code UserService} carried
 * {@code @Scheduled(cron = "0 0 1 * * ?")} over a query for unactivated accounts holding an activation
 * key and CREATED before {@code Instant.now().minus(3, ChronoUnit.DAYS)}. Both numbers were literals in a generated
 * file. That deletion destroys a sign-in name, a first and last name, an email address and a password
 * hash, it runs on every estate, and until {@code decisions.md} D91 it was recorded nowhere: ten
 * documents in this repository said there was no scheduler here, two of them drafts written for
 * counsel.
 *
 * <p><strong>D91's objection was that an undecided framework default was destroying personal data
 * unrecorded — not the number.</strong> So the number does not move: three days is the default here,
 * and an estate that sets nothing behaves byte-identically to every estate that has ever run. What
 * changes is that it is now a stated policy an operator can change, written down in
 * {@code docs/privacy-notice.md} §7.1 and {@code docs/processing-record.md} §3.1, instead of a
 * literal nobody chose.
 *
 * <h2>Every value is a String, and blank means "the default"</h2>
 *
 * <p>The same convention as {@code FoundingTerms} in payout (D57), and for the same measured reason:
 * a variable this repository documents and no compose file carries is a variable that silently does
 * nothing (D46, D50), so both are passed through in all three compose files — and compose's
 * {@code ${X:-}} sets an <em>empty</em> variable rather than leaving it unset, which Spring reads as a
 * value that is present. A typed {@code int} field would then fail to bind on every estate that had
 * not set one.
 *
 * <p>It matters more here than it does in payout, because one of the two values is a <em>cron
 * expression</em>: an empty cron is not a missing cron, it is an invalid one, and
 * {@code @Scheduled(cron = "${...:0 0 1 * * ?}")} would take the empty string from the environment in
 * preference to its own default and fail the context. That is why {@link UnactivatedAccountSweep}
 * registers its task through {@code SchedulingConfigurer} rather than through an annotation
 * placeholder: blank-handling belongs in one place, in Java, where it can be tested.
 *
 * <h2>Malformed refuses startup; "never delete" is not expressible and that is deliberate</h2>
 *
 * <p>A value that is set and unusable fails the context, naming the property and what it was given.
 * A retention of {@code 0} or a negative number is refused rather than accepted: zero means "delete
 * every unactivated account on the next sweep, including the one registered a second ago", and a
 * negative window puts the cutoff in the future, which is the same thing said differently. Neither is
 * a policy anyone would choose, and both are one keystroke from a plausible one.
 *
 * <p>There is deliberately <strong>no</strong> value meaning "keep them for ever". That is a
 * different shape from a number — it needs a sentinel, and it would leave names and email addresses
 * accumulating under no stated period at all, which {@code docs/processing-record.md} §6.1 already
 * flags as an open question to counsel for <em>activated</em> accounts. D94 §5 surfaces it as a
 * question rather than answering it; if the answer is yes, the sentinel goes here and §7.1 of the
 * notice changes with it.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.accounts")
public class AccountRetention {

    /**
     * Three days — JHipster's generated figure, kept so that default behaviour does not move.
     *
     * <p>Not a number this project chose, and D94 §2 records that choosing a different one is a
     * separate decision with a counsel dimension: a longer window holds a name and an email address
     * for longer, and nobody has taken a position on how long that may be.
     */
    public static final String DEFAULT_RETENTION_DAYS = "3";

    /** Daily at 01:00 in the JVM's zone — JHipster's generated {@code 0 0 1 * * ?}, unchanged. */
    public static final String DEFAULT_SWEEP_CRON = "0 0 1 * * ?";

    private String unactivatedRetentionDays = "";
    private String unactivatedSweepCron = "";

    /**
     * How old an unactivated account's activation key must be before the sweep deletes it.
     *
     * @throws IllegalStateException if the value is set and is not a positive whole number of days.
     */
    public Duration unactivatedRetention() {
        String value = orDefault(unactivatedRetentionDays, DEFAULT_RETENTION_DAYS);
        int days;
        try {
            days = Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            throw refusal("unactivated-retention-days", value, "it is not a whole number of days");
        }
        if (days < 1) {
            throw refusal(
                "unactivated-retention-days",
                value,
                "it must be at least one day — 0 deletes an account registered a second ago on the next " +
                    "sweep, and a negative window puts the cutoff in the future, which is the same thing. " +
                    "There is no value meaning 'never delete': that is a separate decision (D94 §5)"
            );
        }
        return Duration.ofDays(days);
    }

    /**
     * When the sweep runs. A cron expression in Spring's six-field form, in the JVM's default zone —
     * which is {@code Etc/UTC} in every container this estate has ever run, so 01:00 here is 01:00 in
     * Accra.
     *
     * <p>Deliberately <em>not</em> one of the three named zone constants (D47, D48, D51): those exist
     * because a stored or rendered date must not depend on the machine. The hour a housekeeping task
     * runs at is not a date and nothing is derived from it, so pinning it to a zone would assert a
     * property nothing needs.
     *
     * @throws IllegalStateException if the value is set and is not a valid cron expression.
     */
    public String unactivatedSweepCron() {
        String value = orDefault(unactivatedSweepCron, DEFAULT_SWEEP_CRON);
        if (!CronExpression.isValidExpression(value)) {
            throw refusal("unactivated-sweep-cron", value, "it is not a valid six-field cron expression");
        }
        return value;
    }

    private static String orDefault(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given.trim();
    }

    private static IllegalStateException refusal(String property, String value, String why) {
        return new IllegalStateException(
            (
                "healthconnect.accounts.%s is '%s' and %s. This is the estate's retention policy for accounts " +
                "that were never activated — it deletes a real person's name and email address — so the " +
                "gateway refuses to start on a value it cannot read rather than falling back to something " +
                "nobody chose. Leave it unset for the documented default (decisions.md D94)"
            ).formatted(property, value, why)
        );
    }

    public String getUnactivatedRetentionDays() {
        return unactivatedRetentionDays;
    }

    public void setUnactivatedRetentionDays(String unactivatedRetentionDays) {
        this.unactivatedRetentionDays = unactivatedRetentionDays;
    }

    public String getUnactivatedSweepCron() {
        return unactivatedSweepCron;
    }

    public void setUnactivatedSweepCron(String unactivatedSweepCron) {
        this.unactivatedSweepCron = unactivatedSweepCron;
    }
}
