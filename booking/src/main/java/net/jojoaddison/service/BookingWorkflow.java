package net.jojoaddison.service;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
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
                booking.setScheduledTime(SlotTime.parse(propose.time()));
            }
            case BookingTransition.Cancel cancel -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(cancel.by());
                booking.setCancellationReason(cancel.reason());
                booking.setLateCancellation(isLate(booking, now));
            }
            case BookingTransition.Complete ignored -> booking.setCompletedAt(now);
            case BookingTransition.NoShow ignored -> booking.setCompletedAt(now);
            // Nothing to set: raisedAt was written when the row was created and must not move, or
            // D40's "was this booking made before the erasure?" comparison changes its answer.
            case BookingTransition.PaymentConfirmed ignored -> {}
            case BookingTransition.PaymentAbandoned abandoned -> {
                booking.setCancelledAt(now);
                booking.setCancelledBy(CancelledBy.PLATFORM);
                booking.setCancellationReason(abandoned.reason());
                // Explicitly false rather than computed. A booking nobody paid for and nobody saw
                // cannot owe a late-cancellation fee, and isLate() would say otherwise for any
                // appointment inside the free window.
                booking.setLateCancellation(false);
            }
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
        String event = eventNameFor(transition);
        if (event != null) {
            outbox.record(event, saved, actor);
        }

        LOG.info("booking {} {} -> {} by {}", saved.getReference(), current, saved.getStatus(), actor);
        return saved;
    }

    /**
     * The topic each transition publishes on — spec §7.
     *
     * <p>{@code booking.accepted} is the event for accepting even though the resulting state is
     * CONFIRMED: the topic names the act, not the state. Declining, proposing and no-show have no
     * topic of their own in the spec, so they fan in through {@code notification.raised}.
     *
     * <p><strong>Null means "publish nothing", and exactly one transition uses it</strong> —
     * {@code decisions.md} D43. A booking abandoned for want of a payment never had a
     * {@code booking.requested} published for it, so an event about its cancellation would be the
     * first thing anyone downstream ever heard of it: messaging would open a conversation to raise a
     * notification about a booking the professional was deliberately never told about, and the
     * customer's own notification would arrive for a booking their screen has been calling "awaiting
     * payment" since they abandoned it. The switch stays exhaustive over the sealed hierarchy, so a
     * transition added later still has to state its answer here, and null has to be chosen rather
     * than fallen into.
     *
     * @return the short topic name, or null to publish nothing at all
     */
    private static String eventNameFor(BookingTransition transition) {
        return switch (transition) {
            case BookingTransition.Accept ignored -> "booking.accepted";
            case BookingTransition.Decline ignored -> "booking.declined";
            case BookingTransition.Cancel ignored -> "booking.cancelled";
            case BookingTransition.Complete ignored -> "booking.completed";
            case BookingTransition.ProposeReschedule ignored -> "notification.raised";
            case BookingTransition.NoShow ignored -> "notification.raised";
            // The booking is announced when its money is confirmed, not when it was created. This is
            // the event BookingCreator.createAwaitingPayment did not publish.
            case BookingTransition.PaymentConfirmed ignored -> "booking.requested";
            case BookingTransition.PaymentAbandoned ignored -> null;
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

    /**
     * When the appointment happens, on the line — read in <strong>the booking's own zone</strong>.
     *
     * <p>{@code decisions.md} D58, backlog NEW-19. This converts an APPOINTMENT's wall clock, which
     * spec §13 #8 — ratified as D55 on 2026-09-07 — puts in the professional's calendar and not the
     * brokerage's. {@code Booking.zoneId} is that calendar: not-null, captured from the offering at
     * creation ({@code CustomerBookingResource.create}) and never recomputed, so reading it here is
     * reading the term the customer was quoted rather than re-deciding it. Emphatically <strong>not
     * {@link MarketCalendar#MARKET_ZONE}</strong>, which is the marketplace's own calendar and would
     * behave identically today while being wrong for the one case the ratification exists for.
     *
     * <p>It converted with {@code ZoneOffset.UTC} until D58 — nil consequence while every
     * {@code Booking.zoneId} is {@code Africa/Accra} and Ghana is UTC+0 all year, and a wrong
     * late-cancellation boundary on a live booking the day a professional is onboarded outside GMT.
     *
     * <p><strong>Public because there is one derivation and two callers.</strong>
     * {@code CustomerBookingResource.cancellationPreview} quotes the hours remaining from the same
     * instant this decides the fee on, and it had a second copy of the line — which is how one
     * quantity came to need fixing in two places twice. Two copies of an appointment's conversion is
     * the defect, not the duplication.
     */
    public Instant scheduledAt(Booking booking) {
        LocalDate date = booking.getScheduledDate();
        return date.atTime(booking.getScheduledTime()).atZone(zoneOf(booking)).toInstant();
    }

    /**
     * The booking's zone, or the marketplace's when the stored value cannot be read.
     *
     * <p>{@code zone_id} is {@code NOT NULL} with no column default and every row in every estate
     * says {@code Africa/Accra}, so neither branch below is reachable from anything this service has
     * ever written. It is a {@code varchar(64)} holding free text all the same, and {@link ZoneId#of}
     * throws on a name that is not in the tzdb — so without this, one unreadable row would make its
     * booking impossible to cancel <em>and</em> impossible to preview, a 500 on the money path
     * because of a string.
     *
     * <p>{@link MarketCalendar#MARKET_ZONE} rather than {@code ZoneOffset.UTC} for the stand-in, and
     * this is the <strong>only</strong> place on this path where that constant belongs: it is what
     * the write side already defaults a blank offering zone to ({@code CustomerBookingResource
     * .zoneOf}), so a row we cannot read is read in the same calendar it would have been written in.
     * UTC would be the same instant and would say a calendar was never chosen. The WARN names the
     * booking and the value because the row, not the reader, is what needs correcting.
     */
    private static ZoneId zoneOf(Booking booking) {
        String zone = booking.getZoneId();
        if (zone == null || zone.isBlank()) {
            LOG.warn("booking {} has no zoneId; reading its appointment in {}", booking.getReference(), MarketCalendar.MARKET_ZONE);
            return MarketCalendar.MARKET_ZONE;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            LOG.warn(
                "booking {} carries an unreadable zoneId {}; reading its appointment in {}",
                booking.getReference(),
                zone,
                MarketCalendar.MARKET_ZONE
            );
            return MarketCalendar.MARKET_ZONE;
        }
    }

}
