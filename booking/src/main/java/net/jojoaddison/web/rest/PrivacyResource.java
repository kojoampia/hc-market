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
            false
        );
    }

    /**
     * @param retention the two periods this deployment is running, from the environment. There were
     *     three until D88 removed {@code careSummary}, which governed nothing: no field in this estate
     *     has ever held a condition, an allergy or a medication
     * @param controllerRegistration null when {@code HC_DPC_REGISTRATION} is unset or blank. Reported
     *     as null rather than as an empty string so "not configured" cannot be mistaken for "registered
     *     with a number nobody can read"
     * @param enforced always false today: nothing schedules a sweep. Reported beside the periods rather
     *     than assumed, so a stated policy is never mistaken for an applied one — which matters more
     *     since D42 than it did before, because populated categories look far more like a working
     *     regime than one unset integer did
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
