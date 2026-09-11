package net.jojoaddison.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 * <h2>Nothing enforces any of this yet</h2>
 *
 * <p>Enforcing a retention period means a scheduled sweep and there is no scheduler anywhere in this
 * estate — the same gap {@code Dispute.dueBy} records. When one exists it calls
 * {@code ErasureWorkflow.eraseCustomer} on everything past the window; the erasure semantics are
 * already decided and tested, so what is missing is the trigger and nothing else.
 *
 * <p><strong>The risk that configuration is mistaken for behaviour went up with this change, not
 * down.</strong> A populated three-category policy reads far more like a working retention regime than
 * a single unset integer ever did. That is precisely why {@code GET /api/desk/privacy} keeps reporting
 * {@code enforced: false} beside the numbers, and why this logs the same caveat at every startup.
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
        LOG.info(
            "privacy: retention is financial={}d operational={}d (decisions.md D42, D88), " +
            "but NOTHING SCHEDULES A SWEEP — these are a stated policy, not an applied one",
            retention.getFinancialDays(),
            retention.getOperationalDays()
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

        /** Bookings, ledger entries, disputes. Counsel's figure: six years. */
        private Integer financialDays;

        /** Message bodies, notifications, conversations. */
        private Integer operationalDays;


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


    }
}
