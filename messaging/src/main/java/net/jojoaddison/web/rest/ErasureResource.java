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
    private final SubjectPseudonym pseudonyms;

    public ErasureResource(ErasureWorkflow erasure, SubjectPseudonym pseudonyms) {
        this.erasure = erasure;
        this.pseudonyms = pseudonyms;
    }

    /**
     * <p>503 when no pepper is configured — {@code decisions.md} D35, and the same refusal in all
     * three services. It matters most here: without the pepper this service cannot recompute the alias
     * of anybody in its own erased-subject register, so it must not write a new one either.
     */
    @PostMapping("/{login}/erase")
    public ErasureReceipt erase(@PathVariable String login) {
        if (!pseudonyms.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "erasure is unavailable: healthconnect.privacy.pepper is not set on this deployment (decisions.md D35)"
            );
        }
        ErasureWorkflow.Erased erased = erasure.eraseCustomer(login);
        return new ErasureReceipt(
            erasure.pseudonym(login),
            erased.conversationsPseudonymised(),
            erased.messagesRedacted(),
            erased.notificationsReKeyed(),
            erased.notificationsRedacted()
        );
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
