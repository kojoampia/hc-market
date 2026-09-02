package net.jojoaddison.web.rest;

import java.util.List;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.ErasureWorkflow;
import net.jojoaddison.service.SubjectPseudonym;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A data subject's erasure request, as it lands on messaging — {@code decisions.md} D24/D31/D38.
 *
 * <p>Same shape and same authority as booking's: {@code ROLE_BROKERAGE}, not self-service, because
 * an erasure request must be identity-checked by a person before anything irreversible happens.
 *
 * <p><strong>This is a leg of a fan-out as well as a desk endpoint.</strong> It was one of three
 * calls an operator had to remember to make, each returning a receipt that looked complete on its
 * own; D38 gives booking an orchestrating endpoint that calls this one and catalog's behind a single
 * operator action. So this accepts two kinds of caller and they are not equivalent —
 * {@code ROLE_BROKERAGE} is a person at the desk, and
 * {@link MarketplaceAuthorities#CUSTOMER_ERASURE} is booking, holding a token minted for one named
 * customer that expires in thirty seconds. {@link ErasureFanoutToken#mayErase} is where the second is
 * held to that, and it is called rather than expressed in the annotation because the annotation
 * cannot see a claim without assuming the principal's type.
 *
 * <p>The class-level authority stays at {@code ROLE_BROKERAGE} deliberately. Method-level
 * {@code @PreAuthorize} wins over it, so widening one method does not widen the class — and a method
 * added here later without an annotation of its own inherits the desk authority rather than the
 * fan-out one, which is the right way round for a mistake to fall.
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
     * three services. It matters most here: without the pepper this service cannot recompute the alias
     * of anybody in its own erased-subject register, so it must not write a new one either.
     *
     * <p>The body is optional and a direct desk call sends none. When the erasure fan-out sends one it
     * carries every booking reference the customer has, which is a fact only booking holds and the
     * only way this service can reach a notification about a booking it has no thread for — D36's
     * residual, closed by D38. A reference this service knows nothing about is simply a deep link that
     * matches no row.
     */
    @PostMapping("/{login}/erase")
    @PreAuthorize("hasAnyAuthority('" + MarketplaceAuthorities.BROKERAGE + "', '" + MarketplaceAuthorities.CUSTOMER_ERASURE + "')")
    public ErasureReceipt erase(@PathVariable String login, @RequestBody(required = false) FanOut fanOut) {
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
        ErasureWorkflow.Erased erased = erasure.eraseCustomer(login, fanOut == null ? List.of() : fanOut.references());
        return new ErasureReceipt(
            erasure.pseudonym(login),
            erased.conversationsPseudonymised(),
            erased.messagesRedacted(),
            erased.notificationsReKeyed(),
            erased.notificationsRedacted()
        );
    }

    /**
     * What the fan-out knows that this service does not.
     *
     * @param bookingReferences every booking reference the customer has, from booking's own table.
     *     Null-tolerant because an absent body and an empty list must behave the same way: both mean
     *     "nobody told me", and both leave the union exactly as D36 built it.
     */
    public record FanOut(List<String> bookingReferences) {
        List<String> references() {
            return bookingReferences == null ? List.of() : bookingReferences;
        }
    }

    /**
     * @param conversationsPseudonymised threads re-keyed to the pseudonym
     * @param messagesErased message bodies replaced
     * @param notificationsReKeyed notifications addressed to the customer, now addressed to the alias
     * @param notificationsRedacted notifications in somebody ELSE's list whose body named the
     *     customer — the professional's "Ama Mensah asked for a home visit". Reported separately
     *     because an operator reading a single total would have no way to tell that data held about
     *     this person by another user was dealt with too
     *     <p>Both, because this reported the second alone until a real erasure returned zero for a
     *     customer whose conversation had just been re-keyed — a booking raises a thread before
     *     anyone writes in it. The receipt is what an operator files against the request, so it must
     *     not under-report.
     */
    public record ErasureReceipt(
        String pseudonym,
        int conversationsPseudonymised,
        int messagesErased,
        int notificationsReKeyed,
        int notificationsRedacted
    ) {}
}
