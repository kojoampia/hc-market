package net.jojoaddison.service.payment.provider;

import net.jojoaddison.service.payment.PaymentProviderProperties;

/**
 * Paystack, as a name and a seam and nothing else yet — {@code decisions.md} D37/D45.
 *
 * <p>Chosen by D37 as one of the three the customer picks between. Registered only when
 * {@code healthconnect.payments.paystack.enabled} is true, which it is nowhere in this repository,
 * and it refuses everything it is asked — see {@link ProviderAwaitingIntegration} for why each
 * refusal takes the shape it does.
 *
 * <h2>What this adapter needs before a line of it can be written</h2>
 *
 * <p>Nothing below is implemented and nothing below is guessed. Each item is a question for
 * Paystack's own documentation and a live test account, and the whole list has to be answered before
 * {@code authorize} or {@code readCallback} can say anything true.
 *
 * <ol>
 *   <li><strong>The authorization call.</strong> Its URL, method and request body; which field
 *       carries an amount and in what unit; whether the currency is a field or an account property;
 *       what the platform's own reference is called on the request, since
 *       {@link net.jojoaddison.service.payment.PaymentIntent#bookingReference()} is what this estate
 *       reconciles by; and whether an email address is required — the intent carries a login and
 *       deliberately no contact details, so if one is needed it has to be fetched at this boundary
 *       rather than added to the intent for every provider.
 *   <li><strong>Its response.</strong> Which field is the durable handle this platform must keep —
 *       {@code PaymentOutcome.providerReference()}, the one value D41 exists to stop being dropped —
 *       and which is the URL the customer must visit. Both are required for a
 *       {@code PaymentOutcome.pendingAt(reference, url)}; the constructor refuses without either.
 *   <li><strong>The status vocabulary.</strong> Every value the response and the callback can carry,
 *       and which {@link net.jojoaddison.service.payment.PaymentState} each maps to. An unmapped
 *       status must map to {@code FAILED} and never to a booking-permitting state — mapping an
 *       unrecognised value onto "fine" is the quietest failure available in this seam (D44).
 *   <li><strong>The callback payload.</strong> Which event names arrive, and where in the body the
 *       handle from item 2 appears — the same value, or this platform cannot match a confirmation to
 *       the booking that is waiting for it.
 *   <li><strong>The signature.</strong> The algorithm, the key, and exactly what bytes it is computed
 *       over. This repository believes, from D43 and unverified, that it is HMAC-SHA512 of the raw
 *       body under the secret key, presented in an {@code x-paystack-signature} header — treat every
 *       word of that as a thing to confirm rather than a thing to implement, including the header's
 *       spelling and the digest's encoding.
 *   <li><strong>The credentials.</strong> {@code healthconnect.payments.paystack.secret} holds one
 *       value because a callback needs one; if the outbound call needs a different key, add the field
 *       in the same commit as the code that reads it.
 * </ol>
 *
 * <p>The last thing to check, and the easiest to forget: Paystack's minimum charge. D44 built the
 * zero-amount guard on the belief that all three providers refuse an authorization for nothing, from
 * their published behaviour rather than from an account. If there is a floor above zero, a booking
 * priced between zero and that floor is uncreatable and nothing in this estate knows it.
 */
public class PaystackPaymentProvider extends ProviderAwaitingIntegration {

    /** The name a customer chooses and a callback is addressed to: {@code /webhooks/payments/paystack}. */
    public static final String NAME = "paystack";

    public PaystackPaymentProvider(PaymentProviderProperties.Provider settings) {
        super(NAME, settings);
    }
}
