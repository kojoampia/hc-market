package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.DisputeStatusChange;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import net.jojoaddison.repository.DisputeQueryRepository;
import net.jojoaddison.repository.DisputeStatusChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dispute lifecycle — decisions.md D23.
 *
 * <h2>The name</h2>
 *
 * <p><strong>{@code DisputeWorkflow}, never {@code DisputeService}.</strong> {@code service Dispute
 * with serviceClass} in the JDL makes JHipster generate {@code DisputeService}, so hand-written
 * logic under that name is silently replaced by the next regeneration and the failure is a wall of
 * "cannot find symbol" on methods that existed minutes ago. {@link BookingWorkflow} carries the same
 * scar.
 *
 * <h2>What upholding a dispute does, and does not do</h2>
 *
 * <p>It publishes {@code dispute.resolved} and nothing else. It does <em>not</em> touch the ledger,
 * because the ledger belongs to payout and this service has no business writing another service's
 * schema. It does not change {@code BookingStatus} either: a booking that was completed stays
 * completed, because it was. The dispute is a separate fact about the same booking.
 *
 * <h2>The five working days</h2>
 *
 * <p>{@code dueBy} is recorded and <strong>not enforced</strong>. There is no scheduler anywhere in
 * this estate, so nothing can escalate when it expires — the desk can sort by it, and that is all.
 * Recorded rather than dropped so the gap is visible; the prototype's promise of a five-working-day
 * resolution is not kept by anything here, and pretending otherwise in code would be worse than
 * saying so.
 */
@Service
public class DisputeWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DisputeWorkflow.class);

    private final DisputeQueryRepository disputes;
    private final DisputeStatusChangeRepository history;
    private final OutboxRecorder outbox;
    private final int workingDaysToResolve;

    public DisputeWorkflow(
        DisputeQueryRepository disputes,
        DisputeStatusChangeRepository history,
        OutboxRecorder outbox,
        @Value("${healthconnect.disputes.working-days-to-resolve:5}") int workingDaysToResolve
    ) {
        this.disputes = disputes;
        this.history = history;
        this.outbox = outbox;
        this.workingDaysToResolve = workingDaysToResolve;
    }

    /**
     * A customer raises a dispute against one of their own completed bookings.
     *
     * <p>Only from {@code COMPLETED} or {@code NO_SHOW}. There is nothing to dispute about a booking
     * that has not happened — a customer who wants out of a future appointment is cancelling, which
     * is a different act with different consequences, and routing it through here would let someone
     * avoid a late-cancellation fee by calling it a grievance.
     */
    @Transactional
    public Dispute raise(Booking booking, String raisedByLogin, CancelledBy raisedBy, String reason) {
        if (booking.getStatus() != BookingStatus.COMPLETED && booking.getStatus() != BookingStatus.NO_SHOW) {
            throw new IllegalStateException(
                "cannot dispute a booking that is %s — only a completed or no-show session can be disputed".formatted(booking.getStatus())
            );
        }
        disputes
            .findByBookingReference(booking.getReference())
            .ifPresent(existing -> {
                throw new IllegalStateException(
                    "booking %s already has dispute %s — add to it rather than raising a second".formatted(
                            booking.getReference(),
                            existing.getReference()
                        )
                );
            });

        Instant now = Instant.now();
        Dispute dispute = new Dispute()
            .reference("d-" + UUID.randomUUID().toString().substring(0, 8))
            .bookingReference(booking.getReference())
            .raisedBy(raisedBy)
            .raisedByLogin(raisedByLogin)
            .professionalRef(booking.getProfessionalRef())
            .reason(reason)
            .status(DisputeStatus.OPEN)
            .raisedAt(now)
            .dueBy(now.plus(Duration.ofDays(workingDaysToResolve)))
            .currency(booking.getCurrency());

        Dispute saved = disputes.save(dispute);
        history.save(
            new DisputeStatusChange().toStatus(DisputeStatus.OPEN).actor(raisedByLogin).occurredAt(now).note("raised").dispute(saved)
        );
        LOG.info("dispute {} raised against booking {} by {}", saved.getReference(), booking.getReference(), raisedByLogin);
        return saved;
    }

    /** Applies a desk decision, writing the audit row and — for an upheld dispute — the event. */
    @Transactional
    public Dispute apply(Dispute dispute, DisputeTransition transition, String actor) {
        DisputeStatus current = dispute.getStatus();
        if (!transition.legalFrom(current)) {
            throw new IllegalStateException(
                "cannot %s a dispute that is %s — %s is legal only from %s".formatted(
                        transition.action(),
                        current,
                        transition.action(),
                        transition.from()
                    )
            );
        }

        Instant now = Instant.now();
        dispute.setStatus(transition.to());

        // Exhaustive over the sealed hierarchy, so a new transition is a compile error here.
        switch (transition) {
            case DisputeTransition.Review ignored -> {
                // Nothing moves. The state change IS the record that someone picked it up.
            }
            case DisputeTransition.Uphold uphold -> {
                dispute.setResolution(uphold.resolution());
                dispute.setResolvedBy(actor);
                dispute.setResolvedAt(now);
                dispute.setRefundMinor(uphold.refundMinor());
            }
            case DisputeTransition.Reject reject -> {
                dispute.setResolution(reject.resolution());
                dispute.setResolvedBy(actor);
                dispute.setResolvedAt(now);
            }
        }

        Dispute saved = disputes.save(dispute);
        history.save(
            new DisputeStatusChange()
                .fromStatus(current)
                .toStatus(saved.getStatus())
                .actor(actor)
                .occurredAt(now)
                .note(transition.action())
                .dispute(saved)
        );

        // Only upholding moves money, so only upholding publishes. A rejected dispute is a fact
        // about this service and nothing downstream needs to act on it.
        if (transition instanceof DisputeTransition.Uphold) {
            outbox.record("dispute.resolved", saved, actor);
        }

        LOG.info("dispute {} {} -> {} by {}", saved.getReference(), current, saved.getStatus(), actor);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Dispute> byReference(String reference) {
        return disputes.findByReference(reference);
    }

    @Transactional(readOnly = true)
    public Optional<Dispute> forBooking(String bookingReference) {
        return disputes.findByBookingReference(bookingReference);
    }

    @Transactional(readOnly = true)
    public List<Dispute> raisedBy(String login) {
        return disputes.findByRaisedByLoginOrderByRaisedAtDesc(login);
    }

    /** The desk queue, oldest deadline first — the only use {@code dueBy} currently has. */
    @Transactional(readOnly = true)
    public List<Dispute> queue() {
        return disputes.findByStatusInOrderByDueByAsc(List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
    }
}
