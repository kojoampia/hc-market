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
     * @return the row's id, which is how the caller comes back to it — not the provider's reference,
     *     because two attempts against one booking may legitimately carry the same one
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(String provider, PaymentIntent intent, PaymentOutcome outcome) {
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
                attempt.setAttentionNote(note.length() > 255 ? note.substring(0, 255) : note);
                attempts.save(attempt);
            });
    }
}
