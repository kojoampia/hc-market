package net.jojoaddison.web.rest;

import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.ErasureWorkflow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A data subject's erasure request, as it lands on messaging — {@code decisions.md} D24/D31.
 *
 * <p>Same shape and same authority as booking's: {@code ROLE_BROKERAGE}, not self-service, because
 * an erasure request must be identity-checked by a person before anything irreversible happens.
 *
 * <p>One of three. A complete erasure calls booking, messaging and catalog; nothing here sequences
 * them, because this estate has no service-to-service authentication and an endpoint that fanned out
 * would need a mechanism that does not exist. Calling one and not the others leaves a partially
 * erased customer, which is stated here rather than left to be discovered.
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

    public record ErasureReceipt(String pseudonym, int messagesErased) {}
}
