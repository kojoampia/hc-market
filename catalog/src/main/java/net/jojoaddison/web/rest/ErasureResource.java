package net.jojoaddison.web.rest;

import net.jojoaddison.security.ErasureFanoutToken;
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
 * A data subject's erasure request, as it lands on catalog — {@code decisions.md} D24/D31/D38.
 *
 * <p>Same authority and same reasoning as booking's and messaging's. It <em>was</em> one of three
 * calls an operator had to remember to make, each with its own plausible receipt; since D38 booking
 * has an orchestrating endpoint that calls this one behind a single action, so this accepts the
 * short-lived fan-out token as well as a person at the desk. {@link ErasureFanoutToken#mayErase} holds
 * the second kind of caller to the one customer its token names.
 *
 * <p>No payload here, unlike messaging's. The fan-out carries the customer's booking references
 * because messaging needs them to reach a notification about a booking it has no thread for; nothing
 * in this service is keyed to a booking, so sending them would be handing over a list of a person's
 * bookings to a service with no use for it.
 *
 * <p>The class-level authority stays at {@code ROLE_BROKERAGE}, which also guards
 * {@code /api/desk/professionals/**} elsewhere in this service. Method-level {@code @PreAuthorize}
 * wins over it, so widening this one method does not widen anything else — and a method added here
 * later without its own annotation inherits the desk authority rather than the fan-out one.
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
    @PreAuthorize("hasAnyAuthority('" + MarketplaceAuthorities.BROKERAGE + "', '" + MarketplaceAuthorities.CUSTOMER_ERASURE + "')")
    public ErasureReceipt erase(@PathVariable String login) {
        if (!ErasureFanoutToken.mayErase(login)) {
            // Reached by a fan-out token minted for somebody else, or one that has outlived its
            // stated lifetime. 403 rather than 404: the caller is authenticated and the path exists.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "this token does not authorise erasing " + login);
        }
        if (!pseudonyms.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "erasure is unavailable: healthconnect.privacy.pepper is not set on this deployment (decisions.md D35)"
            );
        }
        ErasureWorkflow.Erased erased = erasure.eraseCustomer(login);
        return new ErasureReceipt(erasure.pseudonym(login), erased.reviewsDeidentified(), erased.favouritesDeleted());
    }

    /**
     * @param pseudonym what the rows now carry instead of the login
     * @param reviewsDeidentified reviews whose author is now the alias. The body stays — D24
     * @param favouritesDeleted rows removed from the saved list — {@code decisions.md} D39. Reported
     *     because this is the only place in the estate where an erasure <strong>deletes</strong>
     *     anything, and it was the only thing this receipt did not mention: a customer with no reviews
     *     and a saved list of twelve produced a receipt of zeroes, filed against a legal request as
     *     "catalog held nothing for this person". The same defect D31 found in messaging's empty
     *     thread, in the service where the consequence is irreversible
     */
    public record ErasureReceipt(String pseudonym, int reviewsDeidentified, int favouritesDeleted) {}
}
