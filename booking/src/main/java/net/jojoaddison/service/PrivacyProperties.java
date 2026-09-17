package net.jojoaddison.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Retention policy and controller registration — {@code decisions.md} D24/D31, answered by D42.
 *
 * <h2>What changed, and why there are now defaults where there deliberately were none</h2>
 *
 * <p>This class used to hold one nullable {@code retentionDays} with no default, and the comment here
 * argued at length that a default would be "a legal position taken by whoever typed it". That argument
 * was right and it has been discharged rather than abandoned: counsel answered WP-09 on 2026-09-03 and
 * ratified the three periods below for use today. A number that came from counsel is not a developer
 * inventing a claim about Ghanaian law, which is the only thing the old comment was guarding against.
 *
 * <p>What survives from that reasoning is the shape. The figures are <strong>read from the
 * environment</strong> at startup — {@code HC_RETENTION_FINANCIAL_DAYS} and
 * {@code HC_RETENTION_OPERATIONAL_DAYS} — with counsel's values as the committed fallback, so a
 * deployment can be corrected without a release and the ratified numbers are still what runs if nobody
 * sets anything.
 *
 * <h2>Two categories, because one clock cannot be right for both of them</h2>
 *
 * <p>A single period short enough for a message body is far too short for a ledger row the platform is
 * required to keep, and one long enough for the ledger holds health data for six years. Splitting them
 * makes the financial rows survive an operational sweep <strong>by construction</strong> rather than by
 * a condition somebody has to remember to write:
 *
 * <ul>
 *   <li><strong>financial</strong> — bookings, ledger entries, disputes. The statutory clock.
 *   <li><strong>operational</strong> — message bodies, notifications, conversations.
 * </ul>
 *
 * <h2>There was a third, and it governed nothing</h2>
 *
 * <p><strong>{@code care-summary-days} is removed — {@code decisions.md} D88.</strong> D42 ratified a
 * 90-day period and a lawful-basis position for conditions, allergies and medications, and
 * <strong>this estate has never stored any</strong>: {@code Booking.careSummaryShared} is a
 * {@code Boolean} recording that a summary was shared out of band, and no field, column or entity
 * anywhere holds its content. The three words appeared only in comments — including the ones that used
 * to be here.
 *
 * <p>The cost was stated when it was chosen and is recorded rather than smoothed over: counsel's
 * ratified figure is gone, so the day a care summary is genuinely stored the period has to be asked
 * again. Keeping it as a forward-looking default was the alternative.
 *
 *
 * <h2>The registration number has no default, and that is not an oversight</h2>
 *
 * <p>{@code controllerRegistration} is Jojo Addison Consultancy's registration with Ghana's Data
 * Protection Commission. It is a real identifier belonging to a real organisation: a wrong one is a
 * false claim about a regulatory relationship, and a plausible-looking placeholder is worse than an
 * absent value because it stops anyone asking. So there is no fallback — it comes from
 * {@code HC_DPC_REGISTRATION} or it is absent, blank counts as absent, and this repository is public,
 * which is the second reason the value lives in the gitignored environment file rather than here.
 *
 * <h2>Something enforces it now, and it is off by default</h2>
 *
 * <p>This section read <em>"Nothing enforces any of this yet — enforcing a retention period means a
 * scheduled sweep and there is no scheduler anywhere in this estate"</em> until
 * {@code decisions.md} D96. Both halves were wrong by then: {@code @EnableScheduling} has been active
 * in all five services for the estate's whole life (D91), and {@link RetentionSweep} is the sweep this
 * paragraph was waiting for. It calls {@code ErasureWorkflow.eraseCustomer} on every customer the
 * financial window has run out for, exactly as the old text predicted — the prediction was right and
 * the premise about the scheduler was not.
 *
 * <p><strong>Three values govern it and the two booleans do not mean the same thing.</strong>
 * {@code sweep-enabled} decides whether the sweep runs at all; {@code sweep-dry-run} decides whether a
 * run that happens <em>deletes</em>. So an estate turns the sweep on and gets a <em>report</em>, and
 * has to make a second, separate decision to let it act. That is not ceremony: this is the one
 * scheduled task in the estate that performs an irreversible act on real people's records, on a
 * timer, with nobody watching, and "enabled" is the kind of flag somebody flips while reading a
 * different document.
 *
 * <h2>{@code enforced} on the wire is DERIVED, and that is the point</h2>
 *
 * <p>{@link #getRetention()}'s {@link Retention#isEnforcing()} is {@code sweep-enabled AND NOT
 * sweep-dry-run} — the only combination under which a row is actually deleted — and
 * {@code GET /api/desk/privacy} reports that rather than the literal {@code false} it returned until
 * D96. An estate running the sweep in dry-run mode is <strong>not</strong> enforcing its policy, and
 * a desk that said it was would be the defect this whole class exists to avoid, one layer up. This is
 * "derived, never stored" applied to a claim rather than to a figure: there is no longer a hardcoded
 * answer that can be true today and false after a deployment nobody re-read.
 *
 * <p><strong>The risk that configuration is mistaken for behaviour went up with D42, not down</strong>
 * — a populated policy reads far more like a working regime than a single unset integer ever did — and
 * it is <em>still</em> the reason the desk reports the flag beside the numbers. What has changed is
 * that the honest answer is now computed from what the estate will do instead of asserted by a
 * literal. On every estate that configures nothing it is still {@code false}, which is still the
 * truth.
 *
 * <p>{@code @Component}-annotated rather than listed on the generated application class, so a
 * regeneration leaves it alone. Same reason as {@code SeedProperties}.
 *
 * <p>In {@code service} rather than {@code config} because {@code TechnicalStructureTest} lets no
 * layer reach {@code config} at all, and {@code PrivacyResource} has to read it. The generated
 * ArchUnit rule is right about the general case — configuration should not be a dependency of
 * request handling — and a properties holder is the exception it does not distinguish.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.privacy")
public class PrivacyProperties {

    private static final Logger LOG = LoggerFactory.getLogger(PrivacyProperties.class);

    private final Retention retention = new Retention();

    private String controllerRegistration;

    @PostConstruct
    void announce() {
        /* The caveat is composed from what the sweep will actually do rather than asserted, for the
           same reason GET /api/desk/privacy derives `enforced` — a startup line claiming an applied
           policy on an estate that applies none is exactly the confusion D42 raised and D96 had to
           keep answering. `RetentionSweep` logs the cron and the window it registered; this says
           which of the three regimes the estate is in and nothing about scheduling mechanics. */
        LOG.info(
            "privacy: retention is financial={}d operational={}d (decisions.md D42, D88, D96) — {}",
            retention.getFinancialDays(),
            retention.getOperationalDays(),
            retention.regime()
        );
        if (registrationIsAbsent()) {
            LOG.warn(
                "privacy: no data-controller registration number configured; set HC_DPC_REGISTRATION. " +
                "The privacy notice cannot be published without it (decisions.md D42)"
            );
        } else {
            LOG.info("privacy: data controller registered as {} (decisions.md D42)", controllerRegistration);
        }
    }

    /** Blank counts as absent — an empty environment variable is an unset one, not a registration. */
    public boolean registrationIsAbsent() {
        return controllerRegistration == null || controllerRegistration.isBlank();
    }

    public Retention getRetention() {
        return retention;
    }

    public String getControllerRegistration() {
        return controllerRegistration;
    }

    public void setControllerRegistration(String controllerRegistration) {
        this.controllerRegistration = controllerRegistration;
    }

    /**
     * The three periods, in days.
     *
     * <p>A nested holder rather than three flat fields so the desk endpoint can report the policy as
     * one object and a future sweep can be handed the whole thing rather than three arguments in an
     * order somebody will eventually transpose.
     */
    public static class Retention {

        /** Off. The default an estate that configures nothing runs — {@code decisions.md} D96. */
        public static final String DEFAULT_SWEEP_ENABLED = "false";

        /**
         * On. So the first thing a newly-enabled sweep does is <em>count</em>.
         *
         * <p>The two defaults are deliberately not the same word. Reaching a deletion takes two
         * independent decisions, and an operator who turns "enabled" on while reading something else
         * gets a report rather than an erasure.
         */
        public static final String DEFAULT_SWEEP_DRY_RUN = "true";

        /**
         * Daily at 02:30 in the JVM's zone.
         *
         * <p>Half an hour after the gateway's unactivated-account sweep (D94) rather than at the same
         * minute, so two scheduled deletions in one estate do not interleave in the logs — they are
         * in different services and different databases, and the only thing they share is whoever is
         * reading afterwards. Deliberately <strong>not</strong> one of the three named zone constants,
         * for {@code AccountRetention}'s reason: the hour a housekeeping task runs at is not a date
         * and nothing is derived from it, so pinning it to a zone would assert a property nothing
         * needs.
         */
        public static final String DEFAULT_SWEEP_CRON = "0 30 2 * * ?";

        /** Bookings, ledger entries, disputes. Counsel's figure: six years. */
        private Integer financialDays;

        /** Message bodies, notifications, conversations. */
        private Integer operationalDays;

        /*  THE THREE SWEEP VALUES ARE Strings AND THEIR DEFAULTS ARE IN JAVA — D94's finding, and it
            is a binding constraint rather than a style. Compose's `${X:-}` sets an EMPTY variable
            rather than leaving it unset, which Spring reads as a value that is present, so a typed
            `boolean` or `Integer` field would fail to bind on every estate that passed the variable
            through without setting one — and passing it through is what stops it being a variable
            that silently does nothing (D46, D50). The yml therefore carries `${HC_RETENTION_*:}`
            placeholders with no default in them, and blank means "the default" here. */
        private String sweepEnabled = "";
        private String sweepDryRun = "";
        private String sweepCron = "";

        public Integer getFinancialDays() {
            return financialDays;
        }

        public void setFinancialDays(Integer financialDays) {
            this.financialDays = financialDays;
        }

        public Integer getOperationalDays() {
            return operationalDays;
        }

        public void setOperationalDays(Integer operationalDays) {
            this.operationalDays = operationalDays;
        }

        /**
         * Whether the retention sweep runs at all. Default off.
         *
         * @throws IllegalStateException if the value is set and is not {@code true} or {@code false}.
         */
        public boolean sweepEnabled() {
            return parseStrictBoolean("retention.sweep-enabled", sweepEnabled, DEFAULT_SWEEP_ENABLED);
        }

        /**
         * Whether a run that happens reports instead of erasing. Default on.
         *
         * @throws IllegalStateException if the value is set and is not {@code true} or {@code false}.
         */
        public boolean sweepDryRun() {
            return parseStrictBoolean("retention.sweep-dry-run", sweepDryRun, DEFAULT_SWEEP_DRY_RUN);
        }

        /**
         * When the sweep runs, as a six-field Spring cron expression.
         *
         * @throws IllegalStateException if the value is set and is not a valid cron expression.
         */
        public String sweepCron() {
            String value = orDefault(sweepCron, DEFAULT_SWEEP_CRON);
            if (!CronExpression.isValidExpression(value)) {
                throw refusal("retention.sweep-cron", value, "it is not a valid six-field cron expression");
            }
            return value;
        }

        /**
         * True only when a row will actually be deleted — {@code sweep-enabled} AND NOT
         * {@code sweep-dry-run}.
         *
         * <p>This is what {@code GET /api/desk/privacy} reports as {@code enforced}. A dry run is not
         * enforcement: it counts and deletes nothing, so an estate in that state has a stated policy
         * exactly as it did before D96, and saying otherwise on the desk would be the misreading D42
         * raised.
         */
        public boolean isEnforcing() {
            return sweepEnabled() && !sweepDryRun();
        }

        /** One sentence naming which of the three regimes this estate is in, for the startup line. */
        String regime() {
            if (!sweepEnabled()) {
                return "NOTHING SWEEPS — these are a stated policy, not an applied one " +
                "(healthconnect.privacy.retention.sweep-enabled is false)";
            }
            if (sweepDryRun()) {
                return "the sweep is enabled in DRY RUN on '%s' — it will report what is past the window and erase nothing".formatted(
                        sweepCron()
                    );
            }
            return "the sweep is ENFORCING on '%s' — it erases customers past the financial window, irreversibly".formatted(
                    sweepCron()
                );
        }

        /**
         * {@code true}/{@code false} only, case-insensitively, and anything else refuses startup.
         *
         * <p><strong>{@code Boolean.parseBoolean} is the wrong tool here and the direction of its
         * wrongness is what matters.</strong> It answers {@code false} for every string that is not
         * "true" — so {@code sweep-dry-run=fales} would read as {@code false}, which is
         * <em>dry-run off</em>, which is the estate deleting people's records because somebody
         * mistyped a word. A value this class cannot read is refused rather than interpreted, which is
         * D35's and D57's rule and is worth more here than in either of them.
         */
        private static boolean parseStrictBoolean(String property, String given, String fallback) {
            String value = orDefault(given, fallback);
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw refusal(property, value, "it is neither 'true' nor 'false'");
        }

        private static String orDefault(String given, String fallback) {
            return given == null || given.isBlank() ? fallback : given.trim();
        }

        private static IllegalStateException refusal(String property, String value, String why) {
            return new IllegalStateException(
                (
                    "healthconnect.privacy.%s is '%s' and %s. This governs a SCHEDULED, IRREVERSIBLE erasure of " +
                    "real customers' records, so booking refuses to start on a value it cannot read rather than " +
                    "falling back to something nobody chose — a misread here deletes people. Leave it unset for " +
                    "the documented default (decisions.md D96)"
                ).formatted(property, value, why)
            );
        }

        public String getSweepEnabled() {
            return sweepEnabled;
        }

        public void setSweepEnabled(String sweepEnabled) {
            this.sweepEnabled = sweepEnabled;
        }

        public String getSweepDryRun() {
            return sweepDryRun;
        }

        public void setSweepDryRun(String sweepDryRun) {
            this.sweepDryRun = sweepDryRun;
        }

        public String getSweepCron() {
            return sweepCron;
        }

        public void setSweepCron(String sweepCron) {
            this.sweepCron = sweepCron;
        }
    }
}
