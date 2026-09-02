package net.jojoaddison.web.rest;

import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.ErasureFanout;
import net.jojoaddison.service.ErasureWorkflow;
import net.jojoaddison.service.SubjectPseudonym;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * <p><strong>Two endpoints, and the second is the one to use.</strong> Booking holds the address and
 * the notes; messaging holds the message bodies and the bell menus; catalog holds the review
 * authorship and the saved list. {@code /erase} does this service only, which is what a complete
 * erasure used to be three of — and the reason that was a defect rather than an inconvenience is that
 * each of the three receipts looks like a success on its own, so a forgotten call leaves a partially
 * erased customer and no artefact anywhere says so.
 *
 * <p>{@code /erase-everywhere} does all three behind one action — {@code decisions.md} D38. It became
 * possible when D37 answered the question this class's comment used to end on: the estate's five
 * services share a signing key, so booking can mint a token catalog and messaging accept. The
 * single-service form stays, because a leg that failed is worth being able to re-run on its own and
 * because the fan-out is built out of it.
 */
@RestController
@RequestMapping("/api/desk/customers")
@PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
public class ErasureResource {

    private final ErasureWorkflow erasure;
    private final ErasureFanout fanout;
    private final SubjectPseudonym pseudonyms;

    public ErasureResource(ErasureWorkflow erasure, ErasureFanout fanout, SubjectPseudonym pseudonyms) {
        this.erasure = erasure;
        this.fanout = fanout;
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
     * The whole erasure: this service, then messaging, then catalog — {@code decisions.md} D38.
     *
     * <p><strong>200 only when every leg erased; 502 with the same receipt otherwise.</strong> The
     * body is the authority either way and it names each service and what it did, but the status code
     * has to disagree with a caller that reads nothing else, because "a partial erasure looks like a
     * success" is the defect being fixed and a 2xx would reintroduce it through the front door. 207
     * Multi-Status describes the situation more precisely and was rejected for exactly that reason:
     * mis-reading a 502 costs a retry of an idempotent operation, and mis-reading a 207 costs a
     * receipt filed against a data subject request that was never completed.
     *
     * <p>Safe to call again, and honest about it: the second run reports zeroes from every service
     * because there is nothing left under the original login. So the operator's instruction on a 502
     * is simply to call it again, and to escalate if the same leg fails twice.
     *
     * <p>{@code ROLE_BROKERAGE} from the class, and nothing else — in particular <em>not</em> the
     * fan-out authority this endpoint mints. That authority permits being a leg, and booking is never
     * one; granting it here would let a fan-out token trigger a fan-out.
     */
    @PostMapping("/{login}/erase-everywhere")
    public ResponseEntity<ErasureFanout.Receipt> eraseEverywhere(@PathVariable String login) {
        if (!pseudonyms.isConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "erasure is unavailable: healthconnect.privacy.pepper is not set on this deployment (decisions.md D35)"
            );
        }
        ErasureFanout.Receipt receipt = fanout.eraseEverywhere(login);
        return ResponseEntity.status(receipt.complete() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(receipt);
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
