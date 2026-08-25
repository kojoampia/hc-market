package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.DisputeTransition;
import net.jojoaddison.service.DisputeWorkflow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Disputes — decisions.md D23. Two resources, one file, because they share the view records and
 * splitting them would mean a third file holding only those.
 *
 * <p>Named {@code DisputeResources}, plural. The JDL generates {@code DisputeResource} and
 * {@code DisputeStatusChangeResource}; both were deleted (their unscoped CRUD would let any
 * authenticated user resolve anyone's dispute), and a hand-written class must never take a name the
 * generator will reclaim.
 */
public final class DisputeResources {

    private DisputeResources() {}

    // ------------------------------------------------------------------------ views --

    public record DisputeView(
        String reference,
        String bookingReference,
        String professionalRef,
        String raisedBy,
        String reason,
        String status,
        Instant raisedAt,
        Instant dueBy,
        String resolution,
        Instant resolvedAt,
        Long refundMinor,
        String currency
    ) {}

    public record RaiseDispute(@NotBlank @Size(max = 1000) String reason) {}

    public record Decide(@Size(max = 1000) String resolution, Long refundMinor) {}

    static DisputeView view(Dispute d) {
        return new DisputeView(
            d.getReference(),
            d.getBookingReference(),
            d.getProfessionalRef(),
            d.getRaisedBy() == null ? null : d.getRaisedBy().name(),
            d.getReason(),
            d.getStatus() == null ? null : d.getStatus().name(),
            d.getRaisedAt(),
            d.getDueBy(),
            d.getResolution(),
            d.getResolvedAt(),
            d.getRefundMinor(),
            d.getCurrency()
        );
    }

    // -------------------------------------------------------------------- customer --

    /**
     * What a customer can do about their own booking: raise one dispute, and read it back.
     *
     * <p>404 rather than 403 throughout, matching {@code CustomerBookingResource} — a booking that
     * is not yours is indistinguishable from one that does not exist, and saying which is a
     * disclosure.
     */
    @RestController
    @RequestMapping("/api/disputes")
    public static class Customer {

        private final DisputeWorkflow workflow;
        private final BookingQueryRepository bookings;

        public Customer(DisputeWorkflow workflow, BookingQueryRepository bookings) {
            this.workflow = workflow;
            this.bookings = bookings;
        }

        @GetMapping
        public List<DisputeView> mine() {
            return workflow.raisedBy(login()).stream().map(DisputeResources::view).toList();
        }

        @PostMapping("/bookings/{bookingRef}")
        public ResponseEntity<DisputeView> raise(@PathVariable String bookingRef, @Valid @RequestBody RaiseDispute body) {
            String me = login();
            Booking booking = bookings
                .findByReference(bookingRef)
                .filter(b -> me.equals(b.getCustomerLogin()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such booking"));
            try {
                Dispute raised = workflow.raise(booking, me, CancelledBy.CUSTOMER, body.reason());
                return ResponseEntity.status(HttpStatus.CREATED).body(view(raised));
            } catch (IllegalStateException refused) {
                // "already disputed" and "not a completed session" are both the caller asking for
                // something the state does not allow, not a server fault.
                throw new ResponseStatusException(HttpStatus.CONFLICT, refused.getMessage());
            }
        }

        @GetMapping("/{reference}")
        public DisputeView one(@PathVariable String reference) {
            String me = login();
            return workflow
                .byReference(reference)
                .filter(d -> me.equals(d.getRaisedByLogin()))
                .map(DisputeResources::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such dispute"));
        }

        private String login() {
            return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated customer"));
        }
    }

    // ------------------------------------------------------------------------ desk --

    /**
     * The brokerage desk.
     *
     * <p>Guarded by {@link MarketplaceAuthorities#BROKERAGE} and deliberately not by
     * {@code ROLE_ADMIN}: upholding a dispute writes a compensating entry against a professional's
     * earnings, which is a narrower and more consequential power than general administration.
     *
     * <p>There is no UI for this here. decisions.md D26 stops at the API — the console belongs in
     * {@code hc-admin}, a separate repository whose Bootstrap conventions translate from nothing in
     * this product.
     */
    @RestController
    @RequestMapping("/api/desk/disputes")
    @PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
    public static class Desk {

        private final DisputeWorkflow workflow;

        public Desk(DisputeWorkflow workflow) {
            this.workflow = workflow;
        }

        /** Oldest deadline first. Sorting by {@code dueBy} is the only use that field currently has. */
        @GetMapping
        public List<DisputeView> queue() {
            return workflow.queue().stream().map(DisputeResources::view).toList();
        }

        @PostMapping("/{reference}/review")
        public DisputeView review(@PathVariable String reference) {
            return decide(reference, new DisputeTransition.Review());
        }

        @PostMapping("/{reference}/uphold")
        public DisputeView uphold(@PathVariable String reference, @Valid @RequestBody Decide body) {
            if (body.refundMinor() != null && body.refundMinor() < 0) {
                // The sign is applied by payout when it writes the compensating entry. A negative
                // here would reverse the reversal and credit the professional twice.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refundMinor is an amount, not a signed adjustment");
            }
            return decide(reference, new DisputeTransition.Uphold(body.resolution(), body.refundMinor()));
        }

        @PostMapping("/{reference}/reject")
        public DisputeView reject(@PathVariable String reference, @Valid @RequestBody Decide body) {
            return decide(reference, new DisputeTransition.Reject(body.resolution()));
        }

        private DisputeView decide(String reference, DisputeTransition transition) {
            Dispute dispute = workflow
                .byReference(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such dispute"));
            String actor = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated desk user"));
            try {
                return view(workflow.apply(dispute, transition, actor));
            } catch (IllegalStateException illegal) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, illegal.getMessage());
            }
        }
    }
}
