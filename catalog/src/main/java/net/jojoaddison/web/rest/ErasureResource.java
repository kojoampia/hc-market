package net.jojoaddison.web.rest;

import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.ErasureWorkflow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public ErasureResource(ErasureWorkflow erasure) {
        this.erasure = erasure;
    }

    @PostMapping("/{login}/erase")
    public ErasureReceipt erase(@PathVariable String login) {
        return new ErasureReceipt(ErasureWorkflow.pseudonym(login), erasure.eraseCustomer(login));
    }

    public record ErasureReceipt(String pseudonym, int reviewsDeidentified) {}
}
