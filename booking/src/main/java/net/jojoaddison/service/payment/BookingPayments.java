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
     */
    public Taken take(Booking booking) {
        PaymentIntent intent = new PaymentIntent(
            booking.getReference(),
            booking.getCustomerLogin(),
            booking.getPriceMinor(),
            booking.getCurrency(),
            booking.getServiceName()
        );
        PaymentOutcome outcome = provider.authorize(intent);
        String attemptId = recorder.record(provider.name(), intent, outcome); // RED-FIRST: record every outcome
        return new Taken(intent, outcome, attemptId);
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
        if (taken.attemptId() == null || !taken.outcome().state().holdsMoney()) {
            // Nothing was committed — an off-platform booking, or a refusal. There is nothing to give
            // back, and asking the unconfigured provider to give it back throws by design.
            return;
        }
        String reference = taken.outcome().providerReference();
        try {
            // The amount and currency come from the intent that was authorized, not from the
            // booking: what may be given back is bounded by what the provider agreed to, and the
            // booking is the thing that failed to exist.
            PaymentOutcome released = taken.outcome().state() == PaymentState.CAPTURED
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
