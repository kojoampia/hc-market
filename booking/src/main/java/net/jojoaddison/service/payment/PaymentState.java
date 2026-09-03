package net.jojoaddison.service.payment;

/**
 * What the platform knows about the money for one booking — {@code decisions.md} D15/D31.
 *
 * <p>Every payment provider has a notion of these states; none of them is specific to one. That is
 * the test each value had to pass to be here.
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
    OFF_PLATFORM,

    /** The provider has the customer's commitment; the money has not moved yet. */
    AUTHORIZED,

    /** The money has moved to wherever the provider moves it to. */
    CAPTURED,

    /** Returned to the customer, in whole or in part. */
    REFUNDED,

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
    VOIDED,

    /** The customer's instrument said no. A business answer, and final for this attempt. */
    DECLINED,

    /** The provider could not be asked, or answered with an error. A technical answer, so retryable. */
    FAILED;

    /**
     * Whether a booking may be created against this state.
     *
     * <p>{@link #OFF_PLATFORM} passes — it has to, or the estate as it stands today could take no
     * bookings at all. {@link #AUTHORIZED} and {@link #CAPTURED} pass because the money is committed.
     * The rest do not, and the distinction between {@link #DECLINED} and {@link #FAILED} is what the
     * client should do next, not whether to proceed. {@link #VOIDED} does not pass either: it is what
     * an authorization becomes after the platform has given it back, so a booking made against one
     * would be a booking whose money has already been released.
     */
    public boolean permitsBooking() {
        return this == OFF_PLATFORM || this == AUTHORIZED || this == CAPTURED;
    }

    /**
     * Whether the platform is holding money it would have to give back if the booking fell through.
     *
     * <p>{@link #OFF_PLATFORM} is the whole reason this is a method rather than a null check on the
     * reference: nothing was ever committed, so there is nothing to release, and calling a provider
     * to release it would fail loudly against an estate where that is simply the normal case.
     */
    public boolean holdsMoney() {
        return this == AUTHORIZED || this == CAPTURED;
    }
}
