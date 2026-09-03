package net.jojoaddison.service;

import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.repository.BookingHistoryRepository;
import net.jojoaddison.repository.BookingQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a booking, its first audit row and its {@code booking.requested} event in one transaction.
 *
 * <p>Separate from {@link BookingWorkflow} because creating is not a transition — there is no prior
 * state to check legality against. Sharing the method would mean a nullable "from" state threaded
 * through the state machine for the benefit of one caller.
 *
 * <p>There are two ways in, and the difference between them is one event —
 * {@link #createAwaitingPayment} writes the same row and publishes nothing. See
 * {@code decisions.md} D43.
 */
@Service
public class BookingCreator {

    private final BookingQueryRepository bookings;
    private final BookingHistoryRepository history;
    private final OutboxRecorder outbox;

    public BookingCreator(BookingQueryRepository bookings, BookingHistoryRepository history, OutboxRecorder outbox) {
        this.bookings = bookings;
        this.history = history;
        this.outbox = outbox;
    }

    @Transactional
    public Booking create(Booking booking, String actor) {
        return write(booking, actor, "requested", true);
    }

    /**
     * Creates a booking that is waiting on a payment, and tells nobody — {@code decisions.md} D43.
     *
     * <p>The row and its audit entry are written exactly as {@link #create} writes them.
     * <strong>What is missing is the event</strong>, and that omission is the mechanism by which a
     * pending booking stays private: {@code booking.requested} is what opens a conversation in
     * messaging and puts "Ama Mensah asked for a home visit" in the professional's bell menu, so
     * publishing it here would tell a professional about a booking whose money may never arrive —
     * and then leave that notification sitting there when it does not, since D43 publishes nothing on
     * the abandoned path either.
     *
     * <p>It is published later instead, by {@code BookingTransition.PaymentConfirmed}, so the event
     * is late rather than lost and every consumer sees exactly one of it. The status filter on the
     * professional's inbox is the second guard rather than the first: the event is what reaches the
     * other services, and no query of theirs is under this service's control.
     */
    @Transactional
    public Booking createAwaitingPayment(Booking booking, String actor) {
        return write(booking, actor, "awaiting payment", false);
    }

    private Booking write(Booking booking, String actor, String note, boolean announce) {
        Booking saved = bookings.save(booking);
        history.save(
            new BookingStatusChange()
                .fromStatus(null)
                .toStatus(saved.getStatus())
                .actor(actor)
                .occurredAt(saved.getRaisedAt())
                .note(note)
                .booking(saved)
        );
        if (announce) {
            outbox.record("booking.requested", saved, actor);
        }
        return saved;
    }
}
