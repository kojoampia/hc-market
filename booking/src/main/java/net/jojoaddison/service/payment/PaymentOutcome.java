package net.jojoaddison.service.payment;

/**
 * What actually happened — {@code decisions.md} D15/D31/D41.
 *
 * @param state see {@link PaymentState}
 * @param providerReference the provider's own handle for this payment, or null when there is none.
 *     Null is not an error case: an {@link PaymentState#OFF_PLATFORM} outcome has no provider and so
 *     has nothing to reference, and code that treats null as a failure would break the only path
 *     this estate currently takes.
 *     <p><strong>It is also the one field on this record that cannot be reconstructed from anything
 *     the platform knows.</strong> The state can be asked for again, the reason can be looked up, the
 *     amount is on the booking — but the handle is issued by somebody else and exists nowhere but
 *     here. {@code BookingPayments} therefore writes it to {@code payment_attempt} before it does
 *     anything else that could fail. Before D41 it was read for its state and dropped, so the day a
 *     provider first answered {@code AUTHORIZED} the money would have been committed with nothing
 *     held to capture, refund or void it.
 *     <p>A provider that hands back a handle <em>with</em> a refusal — some do, so that the attempt
 *     can be queried afterwards — should use the canonical constructor rather than
 *     {@link #declined(String)}. The handle is kept whatever the state; a refusal that can be looked
 *     up later is worth more than a tidy null.
 * @param reason why, in words, when the state is not a success. Null otherwise
 */
public record PaymentOutcome(PaymentState state, String providerReference, String reason) {
    public static PaymentOutcome offPlatform() {
        return new PaymentOutcome(PaymentState.OFF_PLATFORM, null, null);
    }

    public static PaymentOutcome authorized(String providerReference) {
        return new PaymentOutcome(PaymentState.AUTHORIZED, providerReference, null);
    }

    public static PaymentOutcome captured(String providerReference) {
        return new PaymentOutcome(PaymentState.CAPTURED, providerReference, null);
    }

    public static PaymentOutcome refunded(String providerReference) {
        return new PaymentOutcome(PaymentState.REFUNDED, providerReference, null);
    }

    /** The authorization was released before any money moved — {@link PaymentState#VOIDED}. */
    public static PaymentOutcome voided(String providerReference) {
        return new PaymentOutcome(PaymentState.VOIDED, providerReference, null);
    }

    public static PaymentOutcome declined(String reason) {
        return new PaymentOutcome(PaymentState.DECLINED, null, reason);
    }

    public static PaymentOutcome failed(String reason) {
        return new PaymentOutcome(PaymentState.FAILED, null, reason);
    }
}
