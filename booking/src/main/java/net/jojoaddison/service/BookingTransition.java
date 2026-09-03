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
 *
 * <p>D43 puts one state in front of that machine and two transitions out of it:
 *
 * <pre>
 *   PENDING_PAYMENT ──payment confirmed──▶ REQUESTED
 *          └─────────payment abandoned───▶ CANCELLED
 * </pre>
 *
 * <p>Both are applied by a provider's webhook rather than by a person, and both are here rather than
 * in a payment-specific service for the reason the class comment gives: {@code BookingWorkflow.apply}
 * is the only method that writes {@code status}, so a second path would be a status change with no
 * audit row and no legality check. Nothing else may enter or leave {@code PENDING_PAYMENT} — a
 * professional cannot accept a booking whose money has not arrived, and the refusal is the ordinary
 * 409 from {@link #legalFrom} rather than a check anybody had to remember to write.
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

    /**
     * The money arrived — {@code decisions.md} D43. The booking becomes an ordinary request.
     *
     * <p>This is where {@code booking.requested} is published for a booking that waited on a payment,
     * and it is the only place: {@code BookingCreator.createAwaitingPayment} writes the row and
     * publishes nothing, because a professional should not be told about a booking whose money may
     * never arrive. So the event is late rather than absent, and everything downstream — the
     * conversation messaging opens, the notification in the bell menu — happens exactly once, when
     * there is something to tell.
     */
    record PaymentConfirmed() implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.PENDING_PAYMENT);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.REQUESTED;
        }

        @Override
        public String action() {
            return "payment confirmed";
        }
    }

    /**
     * The money did not arrive — {@code decisions.md} D43. The customer declined the prompt, the
     * payment failed, or the provider gave up on it.
     *
     * <p>Cancelled by {@link CancelledBy#PLATFORM}, because neither party chose this. It is
     * deliberately <strong>not</strong> a {@link Cancel}: that transition computes
     * {@code lateCancellation}, and a booking cancelled for want of a payment inside the free window
     * would acquire a 50% fee against a customer who has not paid anything and whose booking the
     * professional never saw.
     *
     * @param reason plain words, composed by the platform. Never the provider's own message — that is
     *     the route by which a customer's details arrive in a column an erasure has to remember to
     *     sweep, and this one is on the booking rather than in {@code payment_attempt}
     */
    record PaymentAbandoned(String reason) implements BookingTransition {
        @Override
        public Set<BookingStatus> from() {
            return Set.of(BookingStatus.PENDING_PAYMENT);
        }

        @Override
        public BookingStatus to() {
            return BookingStatus.CANCELLED;
        }

        @Override
        public String action() {
            return "payment abandoned";
        }
    }

    /** Whether this transition is legal from {@code current}. */
    default boolean legalFrom(BookingStatus current) {
        return from().contains(current);
    }
}
