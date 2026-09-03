package net.jojoaddison.service.payment;

/**
 * What the platform knows about the money for one booking — {@code decisions.md} D15/D31/D43.
 *
 * <p>Every payment provider has a notion of these states; none of them is specific to one. That is
 * the test each value had to pass to be here.
 *
 * <h2>The two questions are answered per value, not by a rule over the list</h2>
 *
 * <p>{@link #permitsBooking()} and {@link #holdsMoney()} used to be written as {@code this == A ||
 * this == B} chains at the bottom of the file. Both were correct, and both had the same defect: a
 * value added later gets {@code false} for each of them <strong>by omission</strong>, which is a
 * decision taken by whoever forgot rather than by whoever added the state. D43 added {@link #PENDING}
 * and the answer to the first question is the whole substance of that decision, so the answers now
 * sit on the constants where they cannot be reached by default. Adding a state without answering both
 * is a compile error.
 */
public enum PaymentState {
    /**
     * The platform is not collecting this money at all — the customer pays the professional directly.
     *
     * <p><strong>This is the estate's actual state today</strong>, and naming it is most of the point
     * of this enum. Before D31 the assumption was unwritten: bookings were created, completed, and a
     * {@code Ledger} row credited a professional for money the platform had never touched. That is a
     * defensible business model, but it was nowhere stated, and an unstated assumption about money is
     * the kind that gets discovered by an accountant.
     */
    OFF_PLATFORM(true, false),

    /**
     * This booking costs nothing, so no provider was asked anything — {@code decisions.md} D44.
     *
     * <p><strong>The one value in this enum that is not a provider's answer</strong>, and the reason it is
     * here rather than folded into {@link #OFF_PLATFORM}. Every other constant is a provider's answer;
     * this one is the platform declining to ask the question, decided from the amount before any
     * adapter is reached. It therefore passes the admission test above — "no provider dictates it" —
     * in its strongest form rather than its usual one: it is not that every provider has a notion of
     * it, it is that the decision is identical whichever provider is configured.
     *
     * <p>Two of the eighteen seeded professionals offer a service at {@code priceMinor: 0} and "from
     * ₵0" is the catalogue working. All three providers D37 chose refuse an authorization for 0 — from
     * their published behaviour, there being no live account here — so without this every free booking
     * in the estate would have become uncreatable the day a provider was configured, silently until
     * then, because {@link #OFF_PLATFORM} is the answer to any amount at all. And a provider that
     * politely accepted zero would still be one round trip to a third party about money nobody owes.
     *
     * <p><strong>Not {@code OFF_PLATFORM}</strong>, which says the customer pays the professional
     * directly. Nobody pays anybody for a free session, and the distinction earns its keep the day a
     * provider is configured: {@code OFF_PLATFORM} should stop being produced at that moment, and a
     * free booking wearing it would keep answering yes to "is any money in this estate settled off
     * the platform?" for ever.
     *
     * <p>It permits a booking — in {@code REQUESTED}, like any other, because there is nothing for a
     * webhook to confirm — and holds no money, so nothing is released if the create then fails.
     */
    NOTHING_TO_PAY(true, false),

    /**
     * The provider has taken the request and nobody can yet say whether the money will arrive —
     * {@code decisions.md} D43.
     *
     * <p>This is the ordinary answer from all three providers D37 chose. Paystack returns an
     * authorization URL for the customer to visit; Hubtel and MTN MoMo raise a prompt on the
     * customer's phone. In each case the synchronous call ends with the payment un-decided and the
     * customer holding the next move, so a seam whose {@code authorize} could only answer
     * {@code AUTHORIZED} or {@code DECLINED} would have had to <em>guess</em>, and the shape of that
     * guess is the same either way: an optimistic guess creates bookings for money that never
     * arrives, and a pessimistic one refuses every booking in the estate.
     *
     * <p><strong>It permits a booking, and that is D43's decision rather than a property of the
     * word.</strong> What it does not permit is a booking in {@code REQUESTED}: a pending payment
     * yields a booking in {@code PENDING_PAYMENT}, which no professional-facing query returns and
     * which publishes no {@code booking.requested} until the money is confirmed. See D43 for the
     * reasoning and for what the alternative would have cost.
     *
     * <p>It holds no money — nothing is committed until the customer acts — but it is not inert
     * either, which is why {@link #awaitingCustomer()} exists: an abandoned pending payment has to be
     * cancelled at the provider, because the customer may still approve the prompt afterwards.
     *
     * <p>A pending outcome <strong>must</strong> carry a provider reference; {@link PaymentOutcome}
     * refuses to construct one without. A pending payment nothing can name is one no webhook can ever
     * find, which is the same class of defect as D41's dropped handle.
     */
    PENDING(true, false),

    /** The provider has the customer's commitment; the money has not moved yet. */
    AUTHORIZED(true, true),

    /** The money has moved to wherever the provider moves it to. */
    CAPTURED(true, true),

    /** Returned to the customer, in whole or in part. */
    REFUNDED(false, false),

    /**
     * The authorization was released without the money ever moving — {@code decisions.md} D41.
     *
     * <p>Distinct from {@link #REFUNDED} because it is a distinct call at every real provider and
     * the two are not interchangeable: a void releases a hold before settlement and usually leaves no
     * trace on the customer's statement, while a refund moves money back after it has been taken and
     * shows up as two entries. Collapsing them would have the platform issue refunds against money it
     * had never captured, which providers reject.
     *
     * <p>It is not a settlement-model choice, which is why it belongs here rather than with the
     * questions D15 defers: whichever way settlement is eventually arranged, an authorization the
     * platform decides not to use has to be given back.
     */
    VOIDED(false, false),

    /** The customer's instrument said no. A business answer, and final for this attempt. */
    DECLINED(false, false),

    /** The provider could not be asked, or answered with an error. A technical answer, so retryable. */
    FAILED(false, false);

    private final boolean permitsBooking;
    private final boolean holdsMoney;

    PaymentState(boolean permitsBooking, boolean holdsMoney) {
        this.permitsBooking = permitsBooking;
        this.holdsMoney = holdsMoney;
    }

    /**
     * Whether a booking row may be written against this state.
     *
     * <p>{@link #OFF_PLATFORM} passes — it has to, or the estate as it stands today could take no
     * bookings at all. {@link #NOTHING_TO_PAY} passes because there was nothing to ask anybody for
     * (D44). {@link #AUTHORIZED} and {@link #CAPTURED} pass because the money is committed.
     * {@link #PENDING} passes because D43 says so, and it is the only one of the five whose booking is
     * not a {@code REQUESTED} one. The rest do not pass, and the distinction between {@link #DECLINED}
     * and {@link #FAILED} is what the client should do next, not whether to proceed.
     *
     * <p><strong>This answers "may a row exist", not "in what state".</strong> The mapping from an
     * outcome to a {@code BookingStatus} is in {@code CustomerBookingResource}, deliberately: a
     * payment seam that named booking states would be a seam that knows the booking state machine,
     * and this one is meant to survive a provider being chosen without knowing anything about
     * bookings beyond a reference and an amount.
     */
    public boolean permitsBooking() {
        return permitsBooking;
    }

    /**
     * Whether the platform is holding money it would have to give back if the booking fell through.
     *
     * <p>{@link #OFF_PLATFORM} is the whole reason this is a method rather than a null check on the
     * reference: nothing was ever committed, so there is nothing to release, and calling a provider
     * to release it would fail loudly against an estate where that is simply the normal case.
     *
     * <p>{@link #PENDING} answers <strong>false</strong> and still needs releasing — see
     * {@link #awaitingCustomer()}. Money that has not arrived is not money held, but the request for
     * it is still live at the provider.
     *
     * <p>{@link #NOTHING_TO_PAY} answers false and needs nothing at all: no provider was asked, so
     * there is no attempt row and {@link BookingPayments#release} returns on the first clause.
     */
    public boolean holdsMoney() {
        return holdsMoney;
    }

    /**
     * Whether the provider is still waiting on the customer, so money may yet arrive.
     *
     * <p>The state that is neither "we hold it" nor "nothing will happen", and the reason
     * {@link BookingPayments#release} cannot be written as a test of {@link #holdsMoney()} alone. A
     * booking abandoned while its payment is pending must still have that payment cancelled at the
     * provider: the customer's phone is sitting on a prompt, and approving it an hour later against a
     * booking that was never created is D41's defect arriving down the asynchronous path.
     */
    public boolean awaitingCustomer() {
        return this == PENDING;
    }
}
