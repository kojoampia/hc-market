package net.jojoaddison.service.payment;

/**
 * What actually happened — {@code decisions.md} D15/D31.
 *
 * @param state see {@link PaymentState}
 * @param providerReference the provider's own handle for this payment, or null when there is none.
 *     Null is not an error case: an {@link PaymentState#OFF_PLATFORM} outcome has no provider and so
 *     has nothing to reference, and code that treats null as a failure would break the only path
 *     this estate currently takes
 * @param reason why, in words, when the state is not a success. Null otherwise
 */
public record PaymentOutcome(PaymentState state, String providerReference, String reason) {
    public static PaymentOutcome offPlatform() {
        return new PaymentOutcome(PaymentState.OFF_PLATFORM, null, null);
    }

    public static PaymentOutcome authorized(String providerReference) {
        return new PaymentOutcome(PaymentState.AUTHORIZED, providerReference, null);
    }

    public static PaymentOutcome declined(String reason) {
        return new PaymentOutcome(PaymentState.DECLINED, null, reason);
    }

    public static PaymentOutcome failed(String reason) {
        return new PaymentOutcome(PaymentState.FAILED, null, reason);
    }
}
