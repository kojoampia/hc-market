package net.jojoaddison.service.payment;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.PaymentAttempt;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.repository.PaymentAttemptRepository;
import net.jojoaddison.service.BookingTransition;
import net.jojoaddison.service.BookingWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a provider's callback does to a booking — {@code decisions.md} D43.
 *
 * <h2>The contract, in one place</h2>
 *
 * <p>A verified callback names a payment by the provider's own handle and says what became of it. This
 * turns that into at most one booking transition:
 *
 * <ul>
 *   <li>the handle finds a {@code payment_attempt} row, or there is nothing to apply —
 *       {@link Result#UNKNOWN_PAYMENT};
 *   <li>the attempt's state is written down whatever happens next, because what the provider says is a
 *       fact even when the platform can do nothing with it;
 *   <li>if the booking is still {@code PENDING_PAYMENT}: a state that permits a booking confirms it
 *       into {@code REQUESTED} and publishes {@code booking.requested}; anything else abandons it into
 *       {@code CANCELLED};
 *   <li>if the booking has moved on, or was never written, nothing is transitioned.
 * </ul>
 *
 * <h2>The same callback twice</h2>
 *
 * <p>All three providers D37 chose retry until they get a 2xx, and send duplicates besides, so this is
 * the ordinary case rather than the exotic one. <strong>Idempotency is decided from the booking's own
 * state, not from a record of what has been seen.</strong> A second callback finds the booking already
 * {@code REQUESTED}, has no legal transition to make, and answers {@link Result#ALREADY_APPLIED} —
 * which the endpoint returns as 200, because a provider told it needs to retry will keep retrying
 * until it gives up and files the payment as undelivered.
 *
 * <p>A {@code processed_event} table, which is how payout de-duplicates its Kafka consumer, was
 * considered and is the wrong tool here: it would record that this exact callback had been seen, and
 * the thing that must not happen twice is not the callback but the transition. Two genuinely different
 * callbacks about one payment — a pending followed by a capture — must both be applied, and one
 * callback re-sent after a partial failure must be applied once in total rather than never. The
 * booking's status answers both; a seen-set answers neither.
 *
 * <p>The lock is what makes that true under concurrency:
 * {@link BookingQueryRepository#findByReferenceForUpdate} holds the row for the transaction, so two
 * simultaneous retries cannot both read {@code PENDING_PAYMENT} and both publish.
 *
 * <h2>Two orders of arrival, and only one of them is a problem</h2>
 *
 * <p>A provider can confirm before this service has finished writing the booking — the authorization
 * happens first, deliberately (D31/D41), so there is a window in which the attempt row exists and the
 * booking does not. The answer is {@link Result#BOOKING_NOT_YET}, which the endpoint returns as 404 so
 * the provider retries, and by then the booking is there. It is the one case where a non-2xx is the
 * correct answer to a genuine callback.
 *
 * <p>The other order — a confirmation for a booking that was abandoned and whose payment this platform
 * tried to cancel — is not a race and cannot be fixed by retrying. Money has arrived for a booking
 * that does not exist, which is precisely what {@code needs_attention} is for.
 */
@Service
public class PaymentConfirmations {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentConfirmations.class);

    /**
     * The actor on the audit row. Not a login, and it must never look like one: an erasure re-keys
     * {@code actor} where it matches the customer, and a real person's name here would be a claim that
     * they made a transition a machine made.
     */
    private static final String ACTOR = "system:payment";

    private final PaymentAttemptRepository attempts;
    private final BookingQueryRepository bookings;
    private final BookingWorkflow workflow;
    private final PaymentRecorder recorder;

    public PaymentConfirmations(
        PaymentAttemptRepository attempts,
        BookingQueryRepository bookings,
        BookingWorkflow workflow,
        PaymentRecorder recorder
    ) {
        this.attempts = attempts;
        this.bookings = bookings;
        this.workflow = workflow;
        this.recorder = recorder;
    }

    /** What happened, in terms the endpoint turns into a status code and nothing more. */
    public enum Result {
        /** The booking moved. */
        APPLIED,
        /** There was nothing left to do, and that is a success — see the class comment. */
        ALREADY_APPLIED,
        /** No {@code payment_attempt} carries that handle. 404, and worth a log line. */
        UNKNOWN_PAYMENT,
        /** The payment is known and its booking is not written yet. 404, so the provider retries. */
        BOOKING_NOT_YET,
    }

    /**
     * Applies a verified callback.
     *
     * @param outcome what {@link PaymentProvider#readCallback} made of the request. It has already
     *     been established that this is the provider speaking; nothing here re-checks that, and
     *     nothing here may be called with an unverified outcome
     */
    @Transactional
    public Result confirm(PaymentOutcome outcome) {
        List<PaymentAttempt> found = attempts.findByProviderReferenceOrderByRecordedAtDesc(outcome.providerReference());
        if (found.isEmpty()) {
            // Not necessarily an attack: a provider replaying a callback from a database that has
            // since been rebuilt looks exactly like this. Logged without the handle's contents on the
            // grounds that it is a stranger's string until an attempt matches it.
            LOG.warn("a payment callback named a reference this service has never issued; nothing to apply");
            return Result.UNKNOWN_PAYMENT;
        }
        PaymentAttempt attempt = found.get(0);
        // Read before anything writes to the row. The question moneyWithNoBooking has to answer is
        // what this platform believed BEFORE the callback arrived — "did we already give this money
        // back?" — and one line further down that is no longer recoverable.
        String believedBefore = attempt.getState();
        Optional<Booking> booking = bookings.findByReferenceForUpdate(attempt.getBookingReference());

        if (booking.isEmpty()) {
            return moneyWithNoBooking(attempt, believedBefore, outcome);
        }
        // The provider's verdict and the booking transition it justifies are one change, so they are
        // written in one transaction — see PaymentRecorder.confirmed. A callback that fails part-way
        // is retried by the provider, which is why nothing here has to survive its own failure.
        recorder.confirmed(attempt.getId(), outcome.state(), null);
        Booking pending = booking.orElseThrow();
        if (pending.getStatus() != BookingStatus.PENDING_PAYMENT) {
            // The duplicate, and also the booking that has since been accepted, cancelled or
            // completed. Either way the booking's life is no longer this callback's business: a
            // transition back into the payment states would be a payment provider undoing a
            // professional's decision.
            LOG.debug("payment callback for booking {} found it {}; nothing to apply", pending.getReference(), pending.getStatus());
            return Result.ALREADY_APPLIED;
        }
        if (outcome.state().awaitingCustomer()) {
            // "Still pending" is a real thing for a provider to say and there is nothing to do with
            // it. Not an error, and not a transition.
            return Result.ALREADY_APPLIED;
        }
        if (outcome.state().permitsBooking()) {
            workflow.apply(pending, new BookingTransition.PaymentConfirmed(), ACTOR);
            LOG.info("booking {} confirmed by {} payment callback", pending.getReference(), attempt.getProvider());
        } else {
            // Composed here, from this platform's vocabulary. The provider's own message is not
            // copied on to the booking: cancellationReason is a column an erasure has to sweep, and
            // a provider's text is the shortest route from a third party into it.
            workflow.apply(pending, new BookingTransition.PaymentAbandoned("payment was not completed"), ACTOR);
            LOG.info("booking {} cancelled: {} reported {}", pending.getReference(), attempt.getProvider(), outcome.state());
        }
        return Result.APPLIED;
    }

    /**
     * A payment whose booking is not there — either a race, or the case somebody has to look at.
     *
     * <p>They are told apart by what the platform did to the attempt earlier, with no extra column:
     * a row this service has already released carries {@code VOIDED} or {@code REFUNDED}, or is
     * already flagged. A row still sitting in the state the provider first reported has simply
     * arrived before the booking's transaction committed.
     */
    private Result moneyWithNoBooking(PaymentAttempt attempt, String believedBefore, PaymentOutcome outcome) {
        boolean released = PaymentState.VOIDED.name().equals(believedBefore) || PaymentState.REFUNDED.name().equals(believedBefore);
        if (released && outcome.state().holdsMoney()) {
            recorder.confirmed(
                attempt.getId(),
                outcome.state(),
                "%s confirmed payment %s after it was released, and its booking does not exist".formatted(
                        attempt.getProvider(),
                        attempt.getProviderReference()
                    )
            );
            LOG.error(
                "{} confirmed payment {} after this platform released it, for booking {} which does not exist — money is committed and nothing is owed for it",
                attempt.getProvider(),
                attempt.getProviderReference(),
                attempt.getBookingReference()
            );
            // Still a 404 to the provider: there is nothing here to apply it to, and a 200 would tell
            // them the platform has this in hand when a person has to sort it out by hand.
            return Result.BOOKING_NOT_YET;
        }
        recorder.confirmed(attempt.getId(), outcome.state(), null);
        LOG.info(
            "payment callback for booking {} arrived before the booking was written; asking {} to retry",
            attempt.getBookingReference(),
            attempt.getProvider()
        );
        return Result.BOOKING_NOT_YET;
    }
}
