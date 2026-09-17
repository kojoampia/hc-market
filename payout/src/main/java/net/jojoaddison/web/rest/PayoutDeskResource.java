package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.util.function.Supplier;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.service.PayoutRun;
import net.jojoaddison.service.dto.PayoutDeskDtos.BatchView;
import net.jojoaddison.service.dto.PayoutDeskDtos.OpenBatch;
import net.jojoaddison.service.dto.PayoutDeskDtos.Settle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The brokerage desk's payout surface — {@code decisions.md} D95, backlog NEW-51.
 *
 * <h2>{@code ROLE_BROKERAGE}, and why this is not the generated resource</h2>
 *
 * <p>The generated {@code PayoutResource} was deleted (D54): it disclosed every professional's
 * settlement history to any token the estate accepts and let the same token record a payment that
 * never happened or mark an unpaid batch {@code PAID}. That deletion was right and this is its
 * replacement — one door in, guarded by the desk's own authority, with the two acts that actually
 * exist and no third.
 *
 * <p>Deliberately not {@code ROLE_ADMIN}: see {@link MarketplaceAuthorities#BROKERAGE}. And
 * deliberately <strong>not</strong> under {@code /api/pro/**}, which takes no professional parameter
 * and resolves the owner from the JWT subject — that is what makes spec §9's "refuse any reference
 * that is not the caller's" true by construction there, and a desk endpoint naming somebody else's
 * {@code professionalRef} is a different resource rather than an exception to it. The professional's
 * own read stays where it is, on {@code ProEarningsResource.payouts}.
 *
 * <p>Gateway-routed, unlike catalog's {@code /internal/**}: the predicate is
 * {@code /services/healthconnectpayout/api/**} and this path begins {@code /api/}. So the authority
 * is the only thing in front of it, which is the whole reason it is a narrow one.
 *
 * <h2>Why every refusal is a status and not a partial success</h2>
 *
 * <p>Each of {@link PayoutRun}'s refusals leaves nothing written, and the desk has to be able to tell
 * them apart: "you asked for a period that is not payable yet" and "there is nothing unsettled in
 * that period" are both 409 but read completely differently to whoever is trying to pay somebody. The
 * message is the answer, so the statuses are mapped from the exception type rather than collapsed.
 * {@link PayoutRun.NoTermsInForce} is the one that is 503 — it is a statement about this service, and
 * the desk should retry rather than correct the request.
 */
@RestController
@RequestMapping("/api/desk/payouts")
@PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
public class PayoutDeskResource {

    private final PayoutRun run;

    public PayoutDeskResource(PayoutRun run) {
        this.run = run;
    }

    /**
     * Compute and record one batch for one professional over one inclusive period.
     *
     * <p>201, with the batch. There is no idempotency key and none is wanted: a second call for the
     * same period finds nothing unsettled — the first call attached every row — and answers 409. The
     * guard is the attachment rather than a seen-set, exactly as the payment webhook's idempotency is
     * the booking's status under a row lock rather than a record of which callbacks arrived.
     */
    @PostMapping
    public ResponseEntity<BatchView> open(@Valid @RequestBody OpenBatch body) {
        return refusals(() ->
            ResponseEntity.status(HttpStatus.CREATED).body(run.open(body.professionalRef(), body.periodStart(), body.periodEnd()))
        );
    }

    /**
     * Record that a human made the transfer.
     *
     * <p>The batch is addressed by its reference, which is what a person reads off a bank statement,
     * and never by {@code id}.
     */
    @PostMapping("/{reference}/settled")
    public ResponseEntity<BatchView> settle(@PathVariable String reference, @Valid @RequestBody Settle body) {
        return refusals(() -> ResponseEntity.ok(run.settle(reference, body.settledOn(), body.bankReference())));
    }

    /**
     * One batch, read back.
     *
     * <p>Present because the two writes above are the only other way to see a batch's state, and
     * "did that settlement land" is a question somebody will otherwise answer with {@code psql} —
     * which is NEW-53's finding about every desk surface in this estate. It discloses one named
     * professional's settlement, which is what the authority in front of it is for.
     */
    @GetMapping("/{reference}")
    public BatchView one(@PathVariable String reference) {
        return run.byReference(reference).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such payout batch"));
    }

    /**
     * Maps {@link PayoutRun}'s refusals onto statuses.
     *
     * <p>{@link PayoutRun.NoTermsInForce} and {@link PayoutRun.NoSuchBatch} come first, before the
     * supertypes they extend — Java would otherwise never reach either, and the second of them is the
     * difference between 404 and 400 on a mistyped reference. The messages are this service's own
     * prose throughout, and the only caller-supplied values that appear in one are the period and the
     * reference the desk itself just sent.
     */
    private ResponseEntity<BatchView> refusals(Supplier<ResponseEntity<BatchView>> act) {
        try {
            return act.get();
        } catch (PayoutRun.NoTermsInForce unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage());
        } catch (PayoutRun.NoSuchBatch missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing.getMessage());
        } catch (IllegalStateException refused) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, refused.getMessage());
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, malformed.getMessage());
        }
    }
}
