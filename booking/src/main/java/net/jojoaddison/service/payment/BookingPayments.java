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

    private final PaymentProviders providers;
    private final PaymentRecorder recorder;

    public BookingPayments(PaymentProviders providers, PaymentRecorder recorder) {
        this.providers = providers;
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
     *     store — which is every booking in the estate today, because with nothing configured the
     *     fallback reports {@link PaymentState#OFF_PLATFORM} and the platform is not in the money's
     *     path
     * @param provider the adapter that answered, carried so that {@link #release} gives the money
     *     back through the same one that took it — {@code decisions.md} D45. Null only when nobody
     *     was asked, which is the free booking. Before there was a registry this was implicit and
     *     therefore correct by accident; with three providers, re-resolving the default at release
     *     time would void an authorization at whichever provider happened to be first
     */
    public record Taken(PaymentIntent intent, PaymentOutcome outcome, String attemptId, PaymentProvider provider) {}

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
     *
     * <p><strong>And a provider does not get to say the booking was free</strong> — see
     * {@link #onlyThisMethodDecidesNothingIsOwed}. The guard above is the only thing that may produce
     * {@link PaymentState#NOTHING_TO_PAY}, and this is where that is enforced rather than merely
     * documented.
     *
     * <p><strong>Which provider is asked is resolved here too</strong> — {@code decisions.md} D45 —
     * and it happens <em>after</em> the zero-amount guard, deliberately. A free booking asks nobody,
     * so it must not be refused for failing to name somebody: an estate running three providers would
     * otherwise reject every free booking made by a client that had nothing to choose. Resolution can
     * refuse ({@link PaymentChoiceRefused}), and it refuses before any money moves, which is why it is
     * the first thing that happens on the priced path rather than the last.
     *
     * @param chosenProvider the provider the customer asked for, or null for "no preference". Checked
     *     against what this service is configured for — never trusted, never defaulted away. See
     *     {@link PaymentProviders#chosen(String)}
     */
    public Taken take(Booking booking, String chosenProvider) {
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
            return new Taken(intent, PaymentOutcome.nothingToPay(), null, null);
        }
        PaymentProvider provider = providers.chosen(chosenProvider);
        PaymentOutcome outcome = onlyThisMethodDecidesNothingIsOwed(provider, intent, authorize(provider, intent));
        // Returns null when the outcome carried no reference — the guard lives in the recorder, so the
        // "no handle, no row" invariant cannot be lost by a caller. See PaymentRecorder#record.
        String attemptId = recorder.record(providers.nameOf(provider), intent, outcome);
        return new Taken(intent, outcome, attemptId, provider);
    }

    /**
     * Refuses a provider's claim that a priced booking costs nothing — {@code decisions.md} D44.
     *
     * <p>{@link PaymentState#NOTHING_TO_PAY} is documented as the one value in the enum no provider
     * reports, and until this existed the documentation was the whole of the guarantee:
     * {@link PaymentOutcome#nothingToPay()} is a public factory on the record every adapter
     * constructs. {@link PaymentState#PENDING} got two compact-constructor invariants for the same
     * class of defect, on the reasoning that a state which lies is worse than no state; this had a
     * javadoc.
     *
     * <p>What it admits is the quietest failure available in this seam. A ₵150.00 booking, an adapter
     * mapping an unrecognised provider status onto "free": {@link PaymentState#permitsBooking()} is
     * true and the state is not {@code PENDING}, so the booking is created in {@code REQUESTED},
     * {@code booking.requested} is published, the professional is told, and no {@code payment_attempt}
     * row is written because no handle came back. No money moved, and nothing anywhere in the estate
     * disagrees with anything.
     *
     * <p>The check cannot live on the record — the outcome does not know the amount — so it lives in
     * the one method that does, which is the same reason the zero-amount guard is here rather than at
     * the call site.
     *
     * <p><strong>{@link PaymentState#FAILED}, not a thrown exception.</strong> A provider answering
     * something this platform cannot use is exactly what that state means, and it lands on the 502
     * that the {@code catch} above exists to give instead of the 500 a throw would produce — the same
     * defect this package set out to remove, reintroduced one branch along. Not {@code DECLINED}
     * either: the customer's instrument said nothing, and sending them to another card would be a
     * lie about whose fault this is.
     *
     * <p>The handle is carried across if one came with it. D41's rule has no exceptions: a provider
     * confused about the amount may still be holding a fact about this booking's money, and
     * {@link PaymentOutcome#failed(String)} alone would drop the reference.
     */
    private PaymentOutcome onlyThisMethodDecidesNothingIsOwed(PaymentProvider provider, PaymentIntent intent, PaymentOutcome outcome) {
        if (outcome.state() != PaymentState.NOTHING_TO_PAY) {
            return outcome;
        }
        String name = providers.nameOf(provider);
        LOG.error(
            "{} answered NOTHING_TO_PAY for booking {}, which costs {} {} — refused as a provider failure",
            name,
            intent.bookingReference(),
            intent.amountMinor(),
            intent.currency()
        );
        return new PaymentOutcome(
            PaymentState.FAILED,
            outcome.providerReference(),
            "the %s payment provider answered NOTHING_TO_PAY for a booking that costs something".formatted(name)
        );
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
     * <p><strong>The reason is composed here, never copied.</strong> A payment provider's own words are
     * where a phone number or a cardholder's name arrives unannounced — the hazard D41 met by way of
     * {@code attention_note} and D43 by way of the next action — so this one names the provider and
     * the exception's class and nothing else. The whole exception goes to the log at ERROR, because a
     * provider that cannot be reached is worth a line whatever the customer is told.
     *
     * <p><strong>That is not on its own what keeps a provider out of the response body.</strong> This
     * used to say the reason "is rendered into the response body", and it was — but so was
     * {@code PaymentOutcome.declined(reason)}'s, which an adapter writes, and
     * {@code CustomerBookingResource} relayed both verbatim. The boundary composes its own message
     * from the state now (see {@code authorizePayment}), so what a customer is told is derived from a
     * {@link PaymentState} whichever path produced it, and the care taken here is a second line rather
     * than the only one.
     *
     * <p>It is deliberately not narrowed to a provider-specific exception type. There is no such type
     * on the port, on purpose: an adapter is somebody else's code and the seam has no business
     * requiring it to wrap its failures correctly before this platform will behave.
     */
    private PaymentOutcome authorize(PaymentProvider provider, PaymentIntent intent) {
        try {
            return provider.authorize(intent);
        } catch (RuntimeException e) {
            String name = providers.nameOf(provider);
            LOG.error("{} could not be asked to authorize booking {}", name, intent.bookingReference(), e);
            return PaymentOutcome.failed("the %s payment provider could not be asked (%s)".formatted(name, e.getClass().getSimpleName()));
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
        if (taken.provider() == null || taken.attemptId() == null || !(state.holdsMoney() || state.awaitingCustomer())) {
            // Nothing was committed and nothing is on its way — an off-platform booking, or a
            // refusal. There is nothing to give back, and asking the unconfigured provider to give it
            // back throws by design.
            return;
        }
        String reference = taken.outcome().providerReference();
        // The provider that took the money, carried on the Taken rather than resolved again — D45.
        // Re-resolving would ask whichever provider a fresh choice landed on to void an authorization
        // it never issued, on the one path where money is committed and the booking does not exist.
        PaymentProvider provider = taken.provider();
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
                LOG.info("released {} payment {} — {}", providers.nameOf(provider), reference, why);
                return;
            }
            flag(taken, reference, "answered " + released.state());
        } catch (RuntimeException e) {
            LOG.error(
                "could not release {} payment {} after {} — money is committed and its booking does not exist",
                providers.nameOf(provider),
                reference,
                why,
                e
            );
            flag(taken, reference, "threw " + e.getClass().getSimpleName());
        }
    }

    private void flag(Taken taken, String reference, String what) {
        recorder.needsAttention(
            taken.attemptId(),
            "release of %s payment %s %s".formatted(providers.nameOf(taken.provider()), reference, what)
        );
    }
}
