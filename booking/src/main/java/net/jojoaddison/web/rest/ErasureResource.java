package net.jojoaddison.web.rest;

import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.ErasureWorkflow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A data subject's erasure request, as it lands on booking — {@code decisions.md} D24/D31.
 *
 * <p>{@code ROLE_BROKERAGE}, matching the dispute and verification desks: erasure is irreversible and
 * it is a decision about a real person's record, which is a narrower power than general
 * administration and should be grantable to the people who handle these requests without also
 * handing them everything else.
 *
 * <p><strong>Not a self-service endpoint.</strong> A customer cannot call this for themselves, and
 * the reason is not paternalism: an erasure request has to be identity-checked before it is acted
 * on, and an API that erased on the strength of the caller's own token would let anyone who
 * borrowed a session destroy that person's booking history. The check happens off-system, by a
 * person, and this is what they call afterwards.
 *
 * <p><strong>This is one of three.</strong> Booking holds the address and the notes; messaging holds
 * the message bodies; catalog holds the review authorship and the saved list. A complete erasure
 * calls all three, and there is no orchestrator here to do it in one shot — this estate has no
 * service-to-service authentication, so an endpoint that fanned out would need a mechanism that does
 * not exist. The desk in {@code hc-admin} is where that sequencing belongs. Until it does, the gap
 * is real and is recorded rather than hidden: calling one and not the others leaves a partially
 * erased customer.
 */
@RestController
@RequestMapping("/api/desk/customers")
@PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
public class ErasureResource {

    private final ErasureWorkflow erasure;

    public ErasureResource(ErasureWorkflow erasure) {
        this.erasure = erasure;
    }

    /**
     * Redacts this customer's bookings in place.
     *
     * <p>Returns what it did rather than 204, because "how many rows" is the thing an operator has
     * to record against the request — and zero is a meaningful answer worth seeing, not an error.
     */
    @PostMapping("/{login}/erase")
    public ErasureReceipt erase(@PathVariable String login) {
        int count = erasure.eraseCustomer(login);
        return new ErasureReceipt(ErasureWorkflow.pseudonym(login), count);
    }

    /**
     * @param pseudonym what the rows now carry instead of the login — returned so an operator can
     *     find them again in an audit without keeping the original login written down anywhere
     */
    public record ErasureReceipt(String pseudonym, int bookingsErased) {}
}
