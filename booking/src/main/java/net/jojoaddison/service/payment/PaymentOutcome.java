package net.jojoaddison.service.payment;

/**
 * What actually happened — {@code decisions.md} D15/D31/D41/D43.
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
 *     <p><strong>{@link PaymentState#PENDING} is the one state that requires it</strong>, and the
 *     constructor refuses without it (D43). A pending payment is confirmed later by a webhook that
 *     carries the provider's handle and nothing else, so a pending outcome with no handle describes a
 *     payment the estate can never find again — the same defect as D41's, arriving from the other end.
 * @param reason why, in words, when the state is not a success. Null otherwise
 * @param nextAction what the customer has to do, when the answer is "it depends on them" — D43. Never
 *     null; {@link PaymentNextAction#none()} is the answer for every state that is already final.
 *     <p>It is on the outcome rather than on a separate pending-specific type because a provider may
 *     answer any of these states from {@code authorize}, and a caller that had to switch on the state
 *     before knowing which shape it held would be a caller that can forget to.
 */
public record PaymentOutcome(PaymentState state, String providerReference, String reason, PaymentNextAction nextAction) {
    public PaymentOutcome {
        if (nextAction == null) {
            nextAction = PaymentNextAction.none();
        }
        if (state == PaymentState.PENDING && (providerReference == null || providerReference.isBlank())) {
            throw new IllegalArgumentException("a PENDING payment needs a provider reference — nothing else can find it when the webhook arrives");
        }
    }

    /**
     * The three-argument form every caller before D43 used, so adding {@code nextAction} did not
     * become an edit to every provider that will ever be written. An outcome with no stated action
     * has none.
     */
    public PaymentOutcome(PaymentState state, String providerReference, String reason) {
        this(state, providerReference, reason, PaymentNextAction.none());
    }

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

    /**
     * Paystack's answer: the payment is live and the customer must visit a page to complete it — D43.
     *
     * <p>The handle is not optional here. See the {@code providerReference} note above.
     */
    public static PaymentOutcome pendingAt(String providerReference, String url) {
        return new PaymentOutcome(PaymentState.PENDING, providerReference, null, PaymentNextAction.visit(url));
    }

    /** Hubtel's and MoMo's answer: a prompt is on the customer's phone and we wait for the webhook. */
    public static PaymentOutcome pendingOnDevice(String providerReference) {
        return new PaymentOutcome(PaymentState.PENDING, providerReference, null, PaymentNextAction.awaitDevicePrompt());
    }

    public static PaymentOutcome declined(String reason) {
        return new PaymentOutcome(PaymentState.DECLINED, null, reason);
    }

    public static PaymentOutcome failed(String reason) {
        return new PaymentOutcome(PaymentState.FAILED, null, reason);
    }
}
