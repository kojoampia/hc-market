package net.jojoaddison.web.rest;

import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.PrivacyProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the running estate believes its retention policy is — {@code decisions.md} D24/D31/D42.
 *
 * <p>Exists so the answer to "what is your retention period" comes from the deployment rather than
 * from somebody's memory of a conversation. A configuration value nothing ever reads back is a value
 * nobody notices is wrong, and since D42 these values arrive from the environment — which is exactly
 * the class of setting that drifts between two deployments without anybody noticing.
 *
 * <p>Behind {@code ROLE_BROKERAGE} rather than public. The periods themselves are not secret and a
 * published privacy notice will state them, but the registration number and the operational detail of
 * what this particular deployment believes are the desk's business.
 */
@RestController
@RequestMapping("/api/desk/privacy")
@PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
public class PrivacyResource {

    private final PrivacyProperties privacy;

    public PrivacyResource(PrivacyProperties privacy) {
        this.privacy = privacy;
    }

    @GetMapping
    public Policy policy() {
        PrivacyProperties.Retention r = privacy.getRetention();
        return new Policy(
            new RetentionView(r.getFinancialDays(), r.getOperationalDays()),
            privacy.registrationIsAbsent() ? null : privacy.getControllerRegistration(),
            // DERIVED, never a literal — decisions.md D96. This was `false` until the estate acquired
            // a sweep, and a hardcoded honest answer is one deployment away from being a hardcoded
            // dishonest one. `isEnforcing()` is true only when a row is actually deleted: the sweep
            // enabled AND not in dry run.
            r.isEnforcing()
        );
    }

    /**
     * @param retention the two periods this deployment is running, from the environment. There were
     *     three until D88 removed {@code careSummary}, which governed nothing: no field in this estate
     *     has ever held a condition, an allergy or a medication
     * @param controllerRegistration null when {@code HC_DPC_REGISTRATION} is unset or blank. Reported
     *     as null rather than as an empty string so "not configured" cannot be mistaken for "registered
     *     with a number nobody can read"
     * @param enforced whether a customer past the financial window is actually erased —
     *     {@code decisions.md} D96. <strong>Derived from the sweep's own two switches</strong>
     *     ({@code PrivacyProperties.Retention.isEnforcing()}), not a literal: it read a hardcoded
     *     {@code false} until the estate had a sweep at all, which was honest then and would have
     *     become a lie the moment one was switched on. Still {@code false} on every estate that
     *     configures nothing, because the sweep is off by default and, when enabled, is in dry run by
     *     default — and a dry run is <em>not</em> enforcement, which is the distinction this field
     *     exists to keep. Reported beside the periods rather than assumed, so a stated policy is never
     *     mistaken for an applied one
     */
    public record Policy(RetentionView retention, String controllerRegistration, boolean enforced) {}

    /**
     * Days, per category. Null only if somebody has explicitly blanked a value the estate ships.
     *
     * <p><strong>{@code careSummaryDays} is gone from this body — D88.</strong> A client that read it
     * now reads nothing, which is the honest answer: it never described any data. The field is removed
     * rather than reported as null, because a null period reads as "configured to keep it for ever".
     */
    public record RetentionView(Integer financialDays, Integer operationalDays) {}
}
