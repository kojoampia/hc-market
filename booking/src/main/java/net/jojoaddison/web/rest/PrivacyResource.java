package net.jojoaddison.web.rest;

import net.jojoaddison.service.PrivacyProperties;
import net.jojoaddison.security.MarketplaceAuthorities;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the running estate believes its retention policy is — {@code decisions.md} D24/D31.
 *
 * <p>Exists so the answer to "what is your retention period" comes from the deployment rather than
 * from somebody's memory of a conversation, and so {@code null} is visible as an explicit "none set"
 * instead of being invisible. A configuration value nothing ever reads back is a value nobody
 * notices is wrong.
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
        return new Policy(privacy.getRetentionDays(), false);
    }

    /**
     * @param retentionDays null when none has been configured — see {@link PrivacyProperties}
     * @param enforced always false today: nothing schedules a sweep. Reported rather than assumed, so
     *     a configured period is never mistaken for an applied one.
     */
    public record Policy(Integer retentionDays, boolean enforced) {}
}
