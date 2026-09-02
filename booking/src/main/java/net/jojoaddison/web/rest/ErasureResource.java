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
    private final SubjectPseudonym pseudonyms;

    public ErasureResource(ErasureWorkflow erasure, SubjectPseudonym pseudonyms) {
        this.erasure = erasure;
        this.pseudonyms = pseudonyms;
    }

    /**
     * Redacts this customer's bookings in place.
     *
     * <p>Returns what it did rather than 204, because "how many rows" is the thing an operator has
     * to record against the request — and zero is a meaningful answer worth seeing, not an error.
     *
     * <p><strong>503 when no pepper is configured</strong> — {@code decisions.md} D35. The alias is an
     * HMAC keyed by a per-estate secret, and without it the derivation refuses rather than falling
     * back to something re-identifiable. Checked here so the refusal reads as "this deployment is
     * missing a variable" rather than as a 500 from somewhere inside a transaction; the derivation
     * throws as well, which is what makes an unpeppered alias impossible rather than merely unlikely.
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
            erased.bookingsErased(),
            erased.outboxPayloadsRedacted(),
            erased.disputesRedacted(),
            erased.historyRowsReKeyed()
        );
    }

    /**
     * @param pseudonym what the rows now carry instead of the login — returned so an operator can
     *     find them again in an audit without keeping the original login written down anywhere
     * @param bookingsErased rows in {@code booking}
     * @param outboxPayloadsRedacted published events whose payload carried the login and the display
     *     name. Reported because it is the count most likely to be non-zero when every other one is
     *     zero — a customer whose bookings were all erased in an earlier pass still has one of these
     *     per event ever published about them
     * @param disputesRedacted rows in {@code dispute}
     * @param historyRowsReKeyed {@code actor} columns across both status-history tables
     */
    public record ErasureReceipt(
        String pseudonym,
        int bookingsErased,
        int outboxPayloadsRedacted,
        int disputesRedacted,
        int historyRowsReKeyed
    ) {}
}
