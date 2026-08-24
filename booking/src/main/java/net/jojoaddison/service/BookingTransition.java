package net.jojoaddison.service;

import java.util.Set;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;

/**
 * The booking state machine, as spec §3 asks for it: a sealed interface with explicit transitions.
 *
 * <p>Sealed matters here for a specific reason. Every legal move a booking can make is a permitted
 * subtype of this interface, so adding a state or an action forces the compiler to point at every
 * switch that has not been updated. A status field plus scattered {@code if (status == ...)} checks
 * gives you the same behaviour and none of that — the day someone adds a state, the checks that
 * forgot about it simply fall through.
 *
 * <pre>
 *   REQUESTED ──accept───▶ CONFIRMED ──complete──▶ COMPLETED
 *       │                      │
 *       ├──decline──▶ DECLINED  ├──no-show──▶ NO_SHOW
 *       ├──propose──▶ RESCHEDULE_PROPOSED ──accept──▶ CONFIRMED
 *       └──cancel───▶ CANCELLED ◀──cancel── CONFIRMED
 * </pre>
 *
 * <p>{@code ACCEPTED} is deliberately absent from {@link BookingStatus} — accepting moves straight
 * to {@code CONFIRMED}, which is what the prototype's schedule always assumed. See decisions.md D7.
 * The Kafka topic is still {@code booking.accepted}: it names the act, not the resulting state.
 */
public sealed interface BookingTransition {
    /** The states this transition may be applied from. */
    Set<BookingStatus> from();

    /** The state the booking ends in. */
    BookingStatus to();

    /** What the audit row records. */
    String action();

    /**
     * The professional accepts a request, or accepts the customer's answer to a reschedule.
     * Both arrive here because both mean the same thing: the appointment is now on.
     */
    record Accept() implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.REQUESTED, BookingStatus.RESCHEDULE_PROPOSED);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.CONFIRMED;
        }

        @Override
        public String action() {
            return "accept";
        }
    }

    /** The professional declines, with a reason the customer sees. */
    record Decline(String reason) implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.REQUESTED);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.DECLINED;
        }

        @Override
        public String action() {
            return "decline";
        }
    }

    /** The professional proposes another time. The booking waits for the customer. */
    record ProposeReschedule(java.time.LocalDate date, String time) implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.RESCHEDULE_PROPOSED;
        }

        @Override
        public String action() {
            return "propose";
        }
    }

    /**
     * Either side cancels. A confirmed booking cancelled inside the free window carries a fee —
     * the fee is computed by the payout service from the booking's {@code lateCancellation} flag,
     * not here, because the rate belongs to the brokerage config and changes over time.
     */
    record Cancel(CancelledBy by, String reason) implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.REQUESTED, BookingStatus.RESCHEDULE_PROPOSED, BookingStatus.CONFIRMED);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.CANCELLED;
        }

        @Override
        public String action() {
            return "cancel";
        }
    }

    /** The session happened. This is what puts a row in the ledger. */
    record Complete() implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.CONFIRMED);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.COMPLETED;
        }

        @Override
        public String action() {
            return "complete";
        }
    }

    /** The customer did not turn up. Distinct from a cancellation because nobody chose it. */
    record NoShow() implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.CONFIRMED);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.NO_SHOW;
        }

        @Override
        public String action() {
            return "no-show";
        }
    }

    /** Whether this transition is legal from {@code current}. */
    default boolean legalFrom(BookingStatus current) {
        return from().contains(current);
    }
}
