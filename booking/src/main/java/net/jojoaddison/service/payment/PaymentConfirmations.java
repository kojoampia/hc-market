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
     *     nothing here may be called with an unverified outcome. It must also <strong>name a
     *     payment</strong> — {@code PaymentWebhookResource} refuses one that does not with a 401
     *     before reaching here, which is where that invariant is enforced (D49, as reviewed). The
     *     branch below is the second line of the same defence and answers rather than throws, because
     *     this method is inside the transaction that a webhook's transition shares
     */
    @Transactional
    public Result confirm(PaymentOutcome outcome) {
        if (outcome.providerReference() == null || outcome.providerReference().isBlank()) {
            // NOT the WARN below, and the difference is the whole point of having two. This is an
            // adapter of ours returning something inapplicable — no callback said anything wrong —
            // and the message underneath used to be given for it, sending whoever read it to the
            // provider's console to look for a reference the provider had in fact sent.
            LOG.error(
                "a payment callback outcome carried no provider reference, so nothing here can find the payment it is about; " +
                    "this is an adapter defect rather than an unrecognised callback (decisions.md D49)"
            );
            return Result.UNKNOWN_PAYMENT;
        }
        List<PaymentAttempt> found = attempts.findByProviderReferenceOrderByRecordedAtDesc(outcome.providerReference());
        if (found.isEmpty()) {
            // Not necessarily an attack: a provider replaying a callback from a database that has
            // since been rebuilt looks exactly like this. Logged without the handle's contents on the
            // grounds that it is a stranger's string until an attempt matches it. It really is the
            // provider's reference now — the branch above takes the case where there was none.
            LOG.warn("a payment callback named a reference this service has never issued; nothing to apply");
            return Result.UNKNOWN_PAYMENT;
        }
        PaymentAttempt attempt = matching(found);
        // Read before anything writes to the row. The question moneyWithNoBooking has to answer is
        // what this platform believed BEFORE the callback arrived — "did we already let this money
        // go?" — and one line further down that is no longer recoverable.
        Believed believed = Believed.of(attempt);
        Optional<Booking> booking = bookings.findByReferenceForUpdate(attempt.getBookingReference());

        if (booking.isEmpty()) {
            return moneyWithNoBooking(attempt, believed, outcome);
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
     * Which attempt row a callback is about, when a handle names more than one.
     *
     * <p>{@link PaymentRecorder#record} says in its own javadoc that "two attempts against one booking
     * may legitimately carry the same" provider reference, which is why the lookup returns a list and
     * not an {@code Optional} — declaring it as one would 500 on a callback that is perfectly correct.
     * The first version then took the newest row, and that is a guess rather than a match: it is right
     * whenever a provider reuses a handle for a retry of the <em>same</em> payment and wrong whenever
     * the newer row belongs to a booking that no longer exists, in which case the confirmation is
     * filed against the wrong booking and the customer who is actually waiting waits for ever — every
     * retry landing on the same wrong row.
     *
     * <p><strong>The booking that is waiting is a match rather than a preference.</strong> At most one
     * booking per handle can be in {@code PENDING_PAYMENT}: the two transitions out of it are one-way
     * and nothing re-enters it, so a booking still in that state is the one and only thing this
     * callback could legally move. Recency stays as the fallback for everything else — a second
     * callback about an already-confirmed payment, a handle whose bookings have all moved on — where
     * there is nothing to choose between and the newest row is the most recent account of the payment.
     *
     * <p>The lookup is deliberately the unlocked {@link BookingQueryRepository#findByReference}: this
     * is choosing which row to work on, and the lock is taken once, on the one that is chosen.
     */
    private PaymentAttempt matching(List<PaymentAttempt> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return candidates
            .stream()
            .filter(candidate ->
                bookings
                    .findByReference(candidate.getBookingReference())
                    .filter(waiting -> waiting.getStatus() == BookingStatus.PENDING_PAYMENT)
                    .isPresent()
            )
            .findFirst()
            .orElseGet(() -> candidates.get(0));
    }

    /**
     * A payment whose booking is not there — either a race, or the case somebody has to look at.
     *
     * <p>They are told apart by what the platform did to the attempt earlier, with no extra column —
     * see {@link Believed}. A row still sitting in the state the provider first reported, unflagged,
     * has simply arrived before the booking's transaction committed.
     *
     * <p>Three outcomes, and the middle one is the reason this is not two lines:
     *
     * <ul>
     *   <li><strong>Released, and the callback holds money.</strong> The customer approved the prompt
     *       in the gap, or the provider settled something we had cancelled. Money is committed for a
     *       booking that does not exist and nothing here can put that right, so the row is flagged and
     *       the log carries the whole of it at ERROR.
     *   <li><strong>Released, and the callback holds no money.</strong> The prompt expired, or the
     *       provider is confirming the cancellation itself. Nothing is owed, so nothing is flagged —
     *       and, importantly, <strong>nothing is written</strong>: {@code state} here is this
     *       platform's own record of what it did about the payment, and overwriting {@code VOIDED}
     *       with the {@code FAILED} that follows it would destroy the one fact whoever reconciles this
     *       needs. That is D41's rule for a failed release, applied to the callback that comes after.
     *   <li><strong>Not released.</strong> The ordinary race. The verdict is recorded, because it is a
     *       fact about a payment the platform will act on as soon as the booking is there, and the
     *       provider is asked to retry.
     * </ul>
     */
    private Result moneyWithNoBooking(PaymentAttempt attempt, Believed believed, PaymentOutcome outcome) {
        if (believed.released()) {
            if (outcome.state().holdsMoney()) {
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
            } else {
                LOG.info(
                    "{} reported {} for payment {}, which this platform had already released for booking {}; nothing owed, nothing changed",
                    attempt.getProvider(),
                    outcome.state(),
                    attempt.getProviderReference(),
                    attempt.getBookingReference()
                );
            }
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

    /**
     * What the platform believed about a payment before this callback arrived.
     *
     * <p>Captured as a value rather than read from the entity where it is needed, because the entity
     * is managed: {@link PaymentRecorder#confirmed} joins this transaction and writes through the same
     * persistence context, so by the time {@link #moneyWithNoBooking} runs on a path that has already
     * recorded something, the row would answer with the callback's own verdict rather than with what
     * preceded it.
     *
     * @param state the state as the row last held it, which is either the provider's last word or this
     *     platform's record of having released the payment
     * @param flagged whether an operator has already been asked to look at this row. It is part of
     *     "have we let this money go?" and was missing from the first version: {@code BookingPayments}
     *     flags a <em>failed</em> release and deliberately leaves {@code state} alone (D41), so a row
     *     the platform tried and failed to void is indistinguishable from an untouched one by state.
     *     Reading state alone therefore filed the worst case in the estate — money committed, booking
     *     gone, cancellation refused — as a benign race, at INFO
     */
    private record Believed(String state, boolean flagged) {
        static Believed of(PaymentAttempt attempt) {
            return new Believed(attempt.getState(), attempt.isNeedsAttention());
        }

        /** Whether this platform has already stopped expecting to keep this money. */
        boolean released() {
            return PaymentState.VOIDED.name().equals(state) || PaymentState.REFUNDED.name().equals(state) || flagged;
        }
    }
}
