package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.repository.BookingHistoryRepository;
import net.jojoaddison.repository.BookingQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies transitions to bookings and records the audit.
 *
 * <p>Every state change in the system goes through {@link #apply}, which is the only method that
 * writes {@code status}. That is deliberate: a second path that sets the field directly would be a
 * path with no audit row and no legality check, and it would stay invisible until someone asked why
 * a booking is in a state nothing can reach.
 *
 * <h2>Why this is not called {@code BookingService}</h2>
 *
 * <p>Because {@code service Booking with serviceClass} in the JDL makes JHipster generate a class
 * of that exact name. Writing this logic there works right up until the next
 * {@code jhipster jdl --force}, which silently replaces it with generated CRUD — and the failure is
 * a wall of "cannot find symbol" on methods that existed five minutes ago. Hand-written logic needs
 * a name the generator will never claim.
 */
@Service
public class BookingWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(BookingWorkflow.class);

    private final BookingQueryRepository bookings;
    private final BookingHistoryRepository history;
    private final OutboxRecorder outbox;
    private final int freeCancellationHours;

    public BookingWorkflow(
        BookingQueryRepository bookings,
        BookingHistoryRepository history,
        OutboxRecorder outbox,
        @Value("${healthconnect.booking.free-cancellation-hours:24}") int freeCancellationHours
    ) {
        this.bookings = bookings;
        this.history = history;
        this.outbox = outbox;
        this.freeCancellationHours = freeCancellationHours;
    }

    @Transactional(readOnly = true)
    public Optional<Booking> byReference(String reference) {
        return bookings.findByReference(reference);
    }

    @Transactional(readOnly = true)
    public List<Booking> forCustomer(String login, BookingStatus status) {
        return status == null
            ? bookings.findByCustomerLoginOrderByScheduledDateDesc(login)
            : bookings.findByCustomerLoginAndStatusOrderByScheduledDateDesc(login, status);
    }

    /**
     * Applies a transition, or refuses it.
     *
     * @throws IllegalStateException when the transition is not legal from the booking's current
     *                               state. The message names both states and the legal sources,
     *                               because "cannot accept" alone tells a caller nothing about why.
     */
    @Transactional
    public Booking apply(Booking booking, BookingTransition transition, String actor) {
        BookingStatus current = booking.getStatus();
        if (!transition.legalFrom(current)) {
            throw new IllegalStateException(
                "cannot %s a booking that is %s — %s is legal only from %s".formatted(
                        transition.action(),
                        current,
                        transition.action(),
                        transition.from()
                    )
            );
        }

        Instant now = Instant.now();
        booking.setStatus(transition.to());

        // Exhaustive over the sealed hierarchy, so adding a transition is a compile error here
        // rather than a silently missing side effect.
        switch (transition) {
            case BookingTransition.Accept ignored -> booking.setRespondedAt(now);
            case BookingTransition.Decline decline -> {
                booking.setRespondedAt(now);
                booking.setCancellationReason(decline.reason());
            }
            case BookingTransition.ProposeReschedule propose -> {
                booking.setRespondedAt(now);
                booking.setScheduledDate(propose.date());
                booking.setScheduledTime(propose.time());
            }
            case BookingTransition.Cancel cancel -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(cancel.by());
                booking.setCancellationReason(cancel.reason());
                booking.setLateCancellation(isLate(booking, now));
            }
            case BookingTransition.Complete ignored -> booking.setCompletedAt(now);
            case BookingTransition.NoShow ignored -> booking.setCompletedAt(now);
        }

        Booking saved = bookings.save(booking);
        history.save(
            new BookingStatusChange()
                .fromStatus(current)
                .toStatus(saved.getStatus())
                .actor(actor)
                .occurredAt(now)
                .note(transition.action())
                .booking(saved)
        );
        // Same transaction as the booking write and the audit row. OutboxRecorder is MANDATORY,
        // so if this ever ends up outside a transaction it fails loudly rather than quietly
        // reintroducing the dual write.
        outbox.record(eventNameFor(transition), saved, actor);

        LOG.info("booking {} {} -> {} by {}", saved.getReference(), current, saved.getStatus(), actor);
        return saved;
    }

    /**
     * The topic each transition publishes on — spec §7.
     *
     * <p>{@code booking.accepted} is the event for accepting even though the resulting state is
     * CONFIRMED: the topic names the act, not the state. Declining, proposing and no-show have no
     * topic of their own in the spec, so they fan in through {@code notification.raised}.
     */
    private static String eventNameFor(BookingTransition transition) {
        return switch (transition) {
            case BookingTransition.Accept ignored -> "booking.accepted";
            case BookingTransition.Decline ignored -> "booking.declined";
            case BookingTransition.Cancel ignored -> "booking.cancelled";
            case BookingTransition.Complete ignored -> "booking.completed";
            case BookingTransition.ProposeReschedule ignored -> "notification.raised";
            case BookingTransition.NoShow ignored -> "notification.raised";
        };
    }

    /** Marks a booking reviewed, so one completed session yields exactly one review. */
    @Transactional
    public boolean markReviewed(String reference) {
        return bookings
            .findByReference(reference)
            .filter(b -> !Boolean.TRUE.equals(b.getReviewed()))
            .map(b -> {
                b.setReviewed(true);
                bookings.save(b);
                return true;
            })
            .orElse(false);
    }

    /**
     * Whether a cancellation falls inside the free window.
     *
     * <p>Measured from <em>now</em> to the appointment, not from when the booking was made — the
     * obligation is about how much notice the professional gets, and a booking made months ago and
     * cancelled an hour before is late however long it sat there.
     *
     * <p>A booking already in the past is <strong>not</strong> a late cancellation: it is a no-show
     * or an administrative tidy-up, and charging a 50% fee for cancelling something that already
     * failed to happen would be wrong.
     */
    public boolean isLate(Booking booking, Instant now) {
        Instant scheduled = scheduledAt(booking);
        if (scheduled.isBefore(now)) {
            return false;
        }
        return Duration.between(now, scheduled).toHours() < freeCancellationHours;
    }

    public int freeCancellationHours() {
        return freeCancellationHours;
    }

    /** Africa/Accra is GMT with no offset and no DST — spec §13 open question #8 is still open. */
    private static Instant scheduledAt(Booking booking) {
        LocalDate date = booking.getScheduledDate();
        return date.atTime(safeTime(booking.getScheduledTime())).toInstant(ZoneOffset.UTC);
    }

    static LocalTime safeTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (RuntimeException e) {
            return LocalTime.NOON;
        }
    }
}
