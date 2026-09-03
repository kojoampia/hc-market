package net.jojoaddison.service.payment;

import net.jojoaddison.domain.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The payment seam as a booking uses it: take the money, and give it back if the booking fails —
 * {@code decisions.md} D41.
 *
 * <h2>Why this sits between the resource and the port</h2>
 *
 * <p>{@code CustomerBookingResource} used to call {@link PaymentProvider} directly, read the
 * outcome's state, and drop everything else on it. Two things had to be added — storing the handle,
 * and releasing the money when {@code creator.create} throws — and both are about the <em>platform's
 *</em> obligations rather than about HTTP. Putting them in the resource would have made the money's
 * lifecycle a detail of one endpoint, which is where it was when the handle went missing.
 *
 * <p>What stays in the resource: the status codes. 402 for a decline, 502 for a provider that fell
 * over, and the decision to refuse the booking at all. Those are answers to a client, and a service
 * that threw {@code ResponseStatusException} would be deciding them from the wrong place.
 *
 * <h2>The two halves are one defect seen from two sides</h2>
 *
 * <p>Money committed with no handle kept, and money committed with no booking made, are the same
 * failure: the platform holding an obligation it has no way to discharge. {@link #take} closes the
 * first by writing the handle down before anything else can fail; {@link #release} closes the second
 * by using it. Neither works without the other, which is why they were built together.
 */
@Service
public class BookingPayments {

    private static final Logger LOG = LoggerFactory.getLogger(BookingPayments.class);

    private final PaymentProvider provider;
    private final PaymentRecorder recorder;

    public BookingPayments(PaymentProvider provider, PaymentRecorder recorder) {
        this.provider = provider;
        this.recorder = recorder;
    }

    /**
     * What came back from the provider, and where it was written down.
     *
     * @param intent what was asked for. Kept because releasing the money needs the amount and the
     *     currency that were authorized — bounded by what the provider agreed to, not by what the
     *     booking says the service costs
     * @param outcome the provider's answer. The caller decides what it means for the request
     * @param attemptId the {@code payment_attempt} row, or null when the provider gave no handle to
     *     store — which is every booking in the estate today, because the only configured provider
     *     reports {@link PaymentState#OFF_PLATFORM} and the platform is not in the money's path
     */
    public record Taken(PaymentIntent intent, PaymentOutcome outcome, String attemptId) {}

    /**
     * Asks the provider to commit the customer's money, and keeps whatever handle comes back.
     *
     * <p><strong>Every handle is stored, whatever the state.</strong> Not only the successes: a
     * provider that hands back a reference with a decline is telling the platform how to ask about
     * that attempt later, and a declined booking that turns out to have taken money is exactly the
     * situation in which somebody needs to. An outcome with no reference stores nothing — there is
     * no fact to keep — so today's off-platform estate writes no rows at all and behaves as it always
     * has.
     *
     * <p><strong>A booking that costs nothing reaches no provider at all</strong> — {@code
     * decisions.md} D44. Two seeded services are genuinely free, every provider D37 chose refuses an
     * authorization for zero, and the guard is here rather than at the call site for the same reason
     * "no handle, no row" is in the recorder: an invariant about money should be held by the one
     * method every caller goes through, not by the manners of whoever calls it.
     *
     * <p><strong>A provider that throws answers {@link PaymentState#FAILED}</strong>, which is what
     * the exception means. Only the call to the provider is wrapped: a {@link PaymentRecorder} that
     * throws is this platform failing to keep the one fact it cannot reconstruct, and that has to stay
     * loud.
     */
    public Taken take(Booking booking) {
        PaymentIntent intent = new PaymentIntent(
            booking.getReference(),
            booking.getCustomerLogin(),
            booking.getPriceMinor(),
            booking.getCurrency(),
            booking.getServiceName()
        );
        if (intent.amountMinor() == 0) {
            // Nothing to authorize, so nothing is asked and nothing is recorded. Zero exactly: a
            // negative amount is a defect in whatever priced it, and quietly treating it as free
            // would be this service deciding that the platform owes the customer money.
            LOG.debug("booking {} costs nothing; no provider is asked to authorize it", intent.bookingReference());
            return new Taken(intent, PaymentOutcome.nothingToPay(), null);
        }
        PaymentOutcome outcome = authorize(intent);
        // Returns null when the outcome carried no reference — the guard lives in the recorder, so the
        // "no handle, no row" invariant cannot be lost by a caller. See PaymentRecorder#record.
        String attemptId = recorder.record(provider.name(), intent, outcome);
        return new Taken(intent, outcome, attemptId);
    }

    /**
     * Asks the provider, and turns a thrown exception into the answer it is — {@code decisions.md}
     * D44.
     *
     * <p>{@link PaymentState#FAILED} and its 502 already existed; there was no route to them from an
     * exception, so an adapter whose HTTP client timed out produced a 500 and a stack trace while a
     * provider that politely answered {@code FAILED} produced a 502 and a retry. Two answers to one
     * situation, and the unhandled one is the shape every real adapter will actually take — a
     * {@code RestClientException}, a {@code JsonProcessingException}, a null dereference in somebody
     * else's response body.
     *
     * <p><strong>The reason is composed here, never copied.</strong> It is rendered into the response
     * body, and a payment provider's own words are where a phone number or a cardholder's name
     * arrives unannounced — the hazard D41 met by way of {@code attention_note} and D43 by way of the
     * next action. The class name is enough for whoever reads the log, and the log gets the whole
     * thing at ERROR because a provider that cannot be reached is worth a line whatever the customer
     * is told.
     *
     * <p>It is deliberately not narrowed to a provider-specific exception type. There is no such type
     * on the port, on purpose: an adapter is somebody else's code and the seam has no business
     * requiring it to wrap its failures correctly before this platform will behave.
     */
    private PaymentOutcome authorize(PaymentIntent intent) {
        try {
            return provider.authorize(intent);
        } catch (RuntimeException e) {
            LOG.error("{} could not be asked to authorize booking {}", provider.name(), intent.bookingReference(), e);
            return PaymentOutcome.failed(
                "the %s payment provider could not be asked (%s)".formatted(provider.name(), e.getClass().getSimpleName())
            );
        }
    }

    /**
     * Gives back money committed for a booking that could not be created.
     *
     * <p>Before this existed, {@code creator.create} throwing after a successful authorization left
     * the customer charged for a booking that does not exist, with nothing anywhere naming the
     * payment. The row makes the compensation possible; this makes it happen.
     *
     * <p><strong>Void or refund, by state.</strong> An authorization is voided; money already
     * captured has to be refunded, because a void against a settled payment is refused by every
     * provider that distinguishes them. Choosing between them by state is not a settlement-model
     * assumption — it is true of any provider that has both calls, and a provider with only one
     * implements the other as an alias.
     *
     * <p><strong>A pending payment is released too, and that is D43's addition.</strong> It holds no
     * money — {@link PaymentState#holdsMoney()} is false for it — and it is the state that most needs
     * this: the customer's phone is sitting on a prompt, or their browser on a payment page, for a
     * booking that has just failed to exist. Left alone, they approve it a minute later and the estate
     * has taken money for nothing, with the confirmation arriving at a webhook that will never find a
     * booking. That is exactly D41's defect coming back down the asynchronous path, which is why the
     * test here is {@code holdsMoney() || awaitingCustomer()} rather than the tidier first half alone.
     * A provider that cannot cancel a live prompt answers {@link PaymentState#FAILED} and the row is
     * flagged — correctly, because a person now has a payment to watch for.
     *
     * <p><strong>A release that fails is flagged, not retried.</strong> The row is marked for a
     * person: an automatic second attempt against a provider that has just failed is how one stuck
     * payment becomes several, and the operator's action — reconcile against the provider's console —
     * is not one this platform can take. The full cause goes to the log at ERROR, where a message
     * from a provider can name whatever it likes; the note kept in the database is composed here from
     * a provider name, a reference and an exception class, so that a table nothing sweeps cannot
     * acquire a customer's details by way of an error string.
     *
     * @param taken what {@link #take} returned
     * @param why the platform's reason, for the provider's record. Describes the decision, never the
     *     customer
     */
    public void release(Taken taken, String why) {
        PaymentState state = taken.outcome().state();
        if (taken.attemptId() == null || !(state.holdsMoney() || state.awaitingCustomer())) {
            // Nothing was committed and nothing is on its way — an off-platform booking, or a
            // refusal. There is nothing to give back, and asking the unconfigured provider to give it
            // back throws by design.
            return;
        }
        String reference = taken.outcome().providerReference();
        try {
            // The amount and currency come from the intent that was authorized, not from the
            // booking: what may be given back is bounded by what the provider agreed to, and the
            // booking is the thing that failed to exist.
            // A pending payment takes the void path with an authorization: both are "stop this, no
            // money has moved", and a provider with one call for cancelling a live payment uses it
            // for both. Only a capture has to be undone by moving money back.
            PaymentOutcome released = state == PaymentState.CAPTURED
                ? provider.refund(reference, taken.intent().amountMinor(), taken.intent().currency(), why)
                : provider.voidAuthorization(reference, why);
            if (released.state() == PaymentState.VOIDED || released.state() == PaymentState.REFUNDED) {
                recorder.resolved(taken.attemptId(), released.state());
                LOG.info("released {} payment {} — {}", provider.name(), reference, why);
                return;
            }
            flag(taken, reference, "answered " + released.state());
        } catch (RuntimeException e) {
            LOG.error("could not release {} payment {} after {} — money is committed and its booking does not exist", provider.name(), reference, why, e);
            flag(taken, reference, "threw " + e.getClass().getSimpleName());
        }
    }

    private void flag(Taken taken, String reference, String what) {
        recorder.needsAttention(taken.attemptId(), "release of %s payment %s %s".formatted(provider.name(), reference, what));
    }
}
