package net.jojoaddison.service.payment;

import java.time.Instant;
import java.util.UUID;
import net.jojoaddison.domain.PaymentAttempt;
import net.jojoaddison.repository.PaymentAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes down every handle a provider gives us, and every change to what we believe about it —
 * {@code decisions.md} D41.
 *
 * <h2>{@link Propagation#REQUIRES_NEW}, and it is the load-bearing annotation here</h2>
 *
 * <p>This is the exact mirror of {@link net.jojoaddison.service.OutboxRecorder}, which is
 * {@link Propagation#MANDATORY} so that an event can only be written in the same transaction as the
 * change it describes. A payment handle is the opposite kind of fact: <strong>it exists to survive
 * the failure of the work that comes after it.</strong> The booking whose money this is has not been
 * written yet — it cannot be, because authorizing inside that transaction would let a provider
 * timeout roll back a booking the customer's screen believed in (D31) — so a row sharing the
 * booking's transaction would be rolled back precisely when the money was committed and the booking
 * was not. That is the case the table exists for; sharing a transaction would erase the evidence of
 * it.
 *
 * <p>The cost is stated rather than hidden: a payment attempt is committed for a booking that may
 * never exist, so {@code payment_attempt} can hold rows with no booking behind them. That is the
 * correct account of what happened — the money really was committed — and it is why there is no
 * foreign key to {@code booking}.
 */
@Service
public class PaymentRecorder {

    private final PaymentAttemptRepository attempts;

    public PaymentRecorder(PaymentAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /**
     * Stores the handle. Called before anything else that could fail.
     *
     * <p><strong>No handle, no row.</strong> The table exists to hold a reference issued by somebody
     * else; a row without one records nothing that could not be derived from the booking, and
     * {@code provider_reference} is {@code NOT NULL} in the changelog precisely so that a row which
     * has forgotten its only purpose cannot exist. Today's off-platform provider returns no reference,
     * so a correctly-behaving estate writes no rows at all.
     *
     * <p>The guard is here rather than at the call site on purpose. It was at the call site once, as a
     * sentence in a javadoc that the code below it did not honour, and the result was every booking in
     * the estate failing with a 500 from a not-null violation. An invariant the schema enforces should
     * be enforced by the one method that writes the row, not by the manners of whoever calls it.
     *
     * @return the row's id, which is how the caller comes back to it — not the provider's reference,
     *     because two attempts against one booking may legitimately carry the same one; or
     *     <strong>null</strong> when the outcome carried no reference and nothing was written.
     *     <p>No adapter here produces that case yet, and one of them cannot: Paystack's reference is
     *     the booking's own, so a second attempt against one booking would be a second attempt under
     *     one reference, which a provider refuses. The column tolerating it is what lets the retry
     *     path be added later, and {@code PaystackPaymentProvider.authorize} says what has to change
     *     in the same commit (D50, as reviewed; backlog {@code NEW-11})
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(String provider, PaymentIntent intent, PaymentOutcome outcome) {
        if (outcome.providerReference() == null || outcome.providerReference().isBlank()) {
            return null;
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setBookingReference(intent.bookingReference());
        attempt.setProvider(provider);
        attempt.setProviderReference(outcome.providerReference());
        attempt.setState(outcome.state().name());
        attempt.setAmountMinor(intent.amountMinor());
        attempt.setCurrency(intent.currency());
        attempt.setRecordedAt(Instant.now());
        attempt.setNeedsAttention(false);
        // No customerLogin, no customerName, no reason text. See PaymentAttempt's javadoc: the only
        // link from this row to a person is the booking reference, which is what keeps this table
        // out of the erasure sweep.
        return attempts.save(attempt).getId();
    }

    /** The platform's belief changed, and the change succeeded. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolved(String attemptId, PaymentState state) {
        attempts
            .findById(attemptId)
            .ifPresent(attempt -> {
                attempt.setState(state.name());
                attempt.setResolvedAt(Instant.now());
                attempt.setNeedsAttention(false);
                attempt.setAttentionNote(null);
                attempts.save(attempt);
            });
    }

    /**
     * The provider told us, unprompted, what became of a payment — {@code decisions.md} D43.
     *
     * <p>Two things distinguish it from its neighbours, and both are deliberate.
     *
     * <p><strong>It joins the caller's transaction</strong> rather than opening its own. Every other
     * method here is {@link Propagation#REQUIRES_NEW}, because a handle has to survive the failure of
     * the work that follows it — the booking is written afterwards and may not be. A webhook is the
     * opposite arrangement: the provider's verdict and the booking transition it justifies are one
     * change, and a callback that fails half-way is <em>retried by the provider</em>, so there is
     * nothing to preserve independently and a great deal to be said for the two moving together. It
     * also means the webhook needs one database connection rather than two, which is not a design
     * argument but is the difference between working and hanging under a pool of one.
     *
     * <p><strong>It does not clear {@code needs_attention}</strong>, unlike {@link #resolved}. A
     * webhook arriving on a row an operator has been asked to look at is not evidence that the problem
     * went away — it is quite often the evidence that it is real, as when a payment the platform failed
     * to cancel is confirmed by the customer twenty minutes later. Clearing the flag would take the one
     * row a person was going to act on off their list, and nothing would put it back.
     *
     * @param state what the provider now says, recorded as the provider said it. The platform does not
     *     second-guess it: a confirmation that contradicts what we did is a fact to keep, not one to
     *     reconcile away
     * @param attentionNote null in the ordinary case; a note when this confirmation is itself the
     *     problem. Composed by the caller from a provider name and a reference, never from a
     *     provider's message — see {@link #needsAttention}
     */
    @Transactional
    public void confirmed(String attemptId, PaymentState state, String attentionNote) {
        attempts
            .findById(attemptId)
            .ifPresent(attempt -> {
                attempt.setState(state.name());
                attempt.setResolvedAt(Instant.now());
                if (attentionNote != null) {
                    attempt.setNeedsAttention(true);
                    attempt.setAttentionNote(trimmed(attentionNote));
                }
                attempts.save(attempt);
            });
    }

    /**
     * Money is committed, its booking does not exist, and giving it back did not work.
     *
     * <p>The state is left as the provider last reported it, because that is still what the provider
     * believes and overwriting it would lose the one fact a person needs to act on. Nothing retries:
     * a second automatic attempt against a provider that has just failed is how one stuck payment
     * becomes several.
     *
     * @param note composed by the caller from a provider name, a reference and an exception class.
     *     Never a provider's message text — that is the route by which a customer's name arrives in a
     *     table nothing sweeps, which is how D39's stored receipt came to need scrubbing
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void needsAttention(String attemptId, String note) {
        attempts
            .findById(attemptId)
            .ifPresent(attempt -> {
                attempt.setNeedsAttention(true);
                attempt.setResolvedAt(Instant.now());
                attempt.setAttentionNote(trimmed(note));
                attempts.save(attempt);
            });
    }

    /** The column is 255, and a note that overflowed it would fail the write rather than the note. */
    private static String trimmed(String note) {
        return note.length() > 255 ? note.substring(0, 255) : note;
    }
}
