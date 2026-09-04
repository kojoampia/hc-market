package net.jojoaddison.service.payment.provider;

import net.jojoaddison.service.payment.PaymentProviderProperties;

/**
 * MTN Mobile Money, taken directly rather than through an aggregator — {@code decisions.md} D37/D45.
 *
 * <p>The third of D37's choices and the second whose shape is a prompt on the customer's phone, so a
 * successful {@code authorize} is expected to produce
 * {@link net.jojoaddison.service.payment.PaymentOutcome#pendingOnDevice(String)}. Registered only
 * when {@code healthconnect.payments.momo.enabled} is true, which it is nowhere in this repository,
 * and it refuses everything it is asked.
 *
 * <p><strong>Its name on the wire is {@code momo}, not {@code mtn-momo}.</strong> One word, because
 * the name is a URL segment on {@code /webhooks/payments/{provider}}, a property key under
 * {@code healthconnect.payments}, and a value in {@code payment_attempt.provider} — three places a
 * hyphen has to survive intact, one of which is an environment variable whose relaxed binding would
 * make {@code MTN_MOMO} and {@code MTNMOMO} an argument nobody should have to have.
 *
 * <h2>What this adapter needs before a line of it can be written</h2>
 *
 * <p>Nothing below is implemented and nothing below is guessed. This is the one of the three with a
 * documented reputation for a multi-step setup, so the first item is larger than its neighbours.
 *
 * <ol>
 *   <li><strong>How this platform authenticates to MTN at all.</strong> The understanding here is
 *       that a subscription key, an API user and an API key are three separate values obtained in
 *       three separate steps, and that calls carry a short-lived token minted from them rather than
 *       the credentials themselves — every clause of which is a thing to confirm. If a token has to
 *       be minted and refreshed, that is state this adapter holds and nothing in this seam currently
 *       has anywhere to put; decide where before writing the client.
 *   <li><strong>The request-to-pay call.</strong> Its URL, method and body; the amount's unit and
 *       currency field; which header carries the caller's idempotency or reference value, and
 *       whether it is the same value that comes back on the callback — D41's whole argument is that
 *       the handle is the one thing this platform cannot reconstruct.
 *   <li><strong>Its response.</strong> Whether the handle is in the body or in a header, and whether
 *       it is the reference this platform supplied rather than one MTN issues. If the handle is
 *       simply the platform's own reference echoed back, say so in the adapter: it changes nothing
 *       mechanically and it stops the next reader assuming a third party is holding an identifier
 *       when nobody is.
 *   <li><strong>The status vocabulary.</strong> Every value of the payment's state, including
 *       whatever it says for "the customer has not touched the prompt yet" — that one must map to
 *       {@link net.jojoaddison.service.payment.PaymentState#PENDING} and not to a failure, or a
 *       booking is cancelled while its customer is still typing their PIN.
 *   <li><strong>The callback payload and its signature.</strong> The shape, the field holding the
 *       handle, the algorithm, the key, the bytes covered and the header. If MTN's arrangement is a
 *       callback URL registered per request rather than a signed body — which is a real possibility
 *       and would be a different security model from the other two — then say so loudly here:
 *       {@code readCallback} is the only authentication on that endpoint, and an adapter that
 *       returned an outcome without verifying anything would make a public endpoint that creates
 *       bookings on a stranger's word.
 *   <li><strong>What a customer with no MTN number is told.</strong> Same question as Hubtel's, with
 *       the extra edge that a number on another network is a prompt that can never be answered.
 * </ol>
 */
public class MtnMomoPaymentProvider extends ProviderAwaitingIntegration {

    /** The name a customer chooses and a callback is addressed to: {@code /webhooks/payments/momo}. */
    public static final String NAME = "momo";

    public MtnMomoPaymentProvider(PaymentProviderProperties.Provider settings) {
        super(NAME, settings);
    }
}
