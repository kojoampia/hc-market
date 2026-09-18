package net.jojoaddison.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * When the overdue-dispute sweep runs, and whether it runs — {@code decisions.md} D96, backlog NEW-52.
 *
 * <h2>Why this is its own holder and not two more fields on {@link PrivacyProperties}</h2>
 *
 * <p>Because the two sweeps D96 adds are not the same kind of thing, and sharing a configuration class
 * is the first step towards being built as one. {@code healthconnect.privacy} is the retention and
 * erasure policy — figures counsel ratified, a regulator's registration number, and a switch that
 * governs an irreversible deletion. The hour at which a warning is logged about a missed service
 * promise belongs to none of that. Put them together and the next person to widen one widens both.
 *
 * <p>The prefix is {@code healthconnect.disputes}, which already carries
 * {@code working-days-to-resolve} — read by {@link DisputeWorkflow} through a {@code @Value} and
 * deliberately left there. Moving it would be a change to a working class for tidiness, and this
 * holder binding a prefix whose other key it does not declare is harmless: Spring ignores properties a
 * holder has no field for.
 *
 * <h2>This one is ON by default, and that asymmetry is the decision</h2>
 *
 * <p>{@link RetentionSweep} is off by default because it destroys records. This sweep reads two
 * columns and writes a log line, so the cautious default is the wrong one: the promise of a
 * five-working-day resolution is currently kept by <em>nothing</em>, and a sweep shipped switched off
 * would leave it kept by nothing while looking as though it had been addressed. The switch exists so
 * an estate drowning in overdue disputes can silence it while it digs out, which is a different
 * situation from the one the default serves.
 *
 * <h2>Every value is a String, and blank means "the default"</h2>
 *
 * <p>{@code AccountRetention}'s reason (D94), unchanged: compose's {@code ${X:-}} sets an empty
 * variable rather than leaving it unset, so a typed field would fail to bind on every estate that
 * passed the variable through without setting it. It matters most for the cron, where an empty value
 * is not a missing expression but an invalid one.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.disputes")
public class DisputeSlaProperties {

    /** On. See the class comment — a promise nothing keeps is not made safer by a quiet default. */
    public static final String DEFAULT_OVERDUE_SWEEP_ENABLED = "true";

    /**
     * Daily at 07:00 in the JVM's zone — which is {@code Etc/UTC} in every container this estate has
     * ever run, so 07:00 here is 07:00 in Accra.
     *
     * <p>A working hour on purpose, unlike the two deletion sweeps at 01:00 and 02:30. This one exists
     * to be <em>read</em> by somebody at a desk, so it runs when the log line stands a chance of being
     * seen on the day it is about, rather than in the middle of the night with the housekeeping.
     */
    public static final String DEFAULT_OVERDUE_SWEEP_CRON = "0 0 7 * * ?";

    private String overdueSweepEnabled = "";
    private String overdueSweepCron = "";

    /**
     * Whether the overdue-dispute sweep runs. Default on.
     *
     * @throws IllegalStateException if the value is set and is not {@code true} or {@code false}.
     */
    public boolean overdueSweepEnabled() {
        String value = orDefault(overdueSweepEnabled, DEFAULT_OVERDUE_SWEEP_ENABLED);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw refusal("overdue-sweep-enabled", value, "it is neither 'true' nor 'false'");
    }

    /**
     * When it runs, as a six-field Spring cron expression.
     *
     * @throws IllegalStateException if the value is set and is not a valid cron expression.
     */
    public String overdueSweepCron() {
        String value = orDefault(overdueSweepCron, DEFAULT_OVERDUE_SWEEP_CRON);
        if (!CronExpression.isValidExpression(value)) {
            throw refusal("overdue-sweep-cron", value, "it is not a valid six-field cron expression");
        }
        return value;
    }

    private static String orDefault(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given.trim();
    }

    /*  Refused rather than defaulted, and the reasoning is milder than PrivacyProperties' twin
        because the harm is: this sweep changes nothing, so a misread value costs a warning that does
        or does not appear. It still refuses, for the reason D57 gives about the brokerage figures — a
        value somebody set and this estate silently ignored is worse than a startup that says so, and
        an operator who typed something here was trying to change behaviour. */
    private static IllegalStateException refusal(String property, String value, String why) {
        return new IllegalStateException(
            (
                "healthconnect.disputes.%s is '%s' and %s. This governs the sweep that warns when a dispute has " +
                "missed the resolution promise the prototype makes to customers, so booking refuses to start on a " +
                "value it cannot read rather than ignoring it. Leave it unset for the documented default " +
                "(decisions.md D96)"
            ).formatted(property, value, why)
        );
    }

    public String getOverdueSweepEnabled() {
        return overdueSweepEnabled;
    }

    public void setOverdueSweepEnabled(String overdueSweepEnabled) {
        this.overdueSweepEnabled = overdueSweepEnabled;
    }

    public String getOverdueSweepCron() {
        return overdueSweepCron;
    }

    public void setOverdueSweepCron(String overdueSweepCron) {
        this.overdueSweepCron = overdueSweepCron;
    }
}
