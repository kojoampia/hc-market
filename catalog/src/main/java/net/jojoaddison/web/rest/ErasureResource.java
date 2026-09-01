package net.jojoaddison.web.rest;

import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.ErasureWorkflow;
import net.jojoaddison.service.SubjectPseudonym;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A data subject's erasure request, as it lands on catalog — {@code decisions.md} D24/D31.
 *
 * <p>Same authority and same reasoning as booking's and messaging's. One of three: nothing here
 * sequences the others, and calling this alone leaves a partially erased customer.
 */
@RestController
@RequestMapping("/api/desk/customers")
@PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
public class ErasureResource {

    private final ErasureWorkflow erasure;
    private final SubjectPseudonym pseudonyms;

    public ErasureResource(ErasureWorkflow erasure, SubjectPseudonym pseudonyms) {
        this.erasure = erasure;
        this.pseudonyms = pseudonyms;
    }

    /**
     * <p>503 when no pepper is configured — {@code decisions.md} D35, and the same refusal in all
     * three services. The alias is an HMAC keyed by a per-estate secret; without it the derivation
     * refuses rather than writing something a database dump can be matched back to a login.
     */
    @PostMapping("/{login}/erase")
    public ErasureReceipt erase(@PathVariable String login) {
        if (!pseudonyms.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "erasure is unavailable: healthconnect.privacy.pepper is not set on this deployment (decisions.md D35)"
            );
        }
        return new ErasureReceipt(erasure.pseudonym(login), erasure.eraseCustomer(login));
    }

    public record ErasureReceipt(String pseudonym, int reviewsDeidentified) {}
}
