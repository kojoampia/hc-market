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
        Booking saved = bookings.save(booking);
        history.save(
            new BookingStatusChange()
                .fromStatus(null)
                .toStatus(saved.getStatus())
                .actor(actor)
                .occurredAt(saved.getRaisedAt())
                .note("requested")
                .booking(saved)
        );
        outbox.record("booking.requested", saved, actor);
        return saved;
    }
}
