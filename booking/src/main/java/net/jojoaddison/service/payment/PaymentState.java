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
     * client should do next, not whether to proceed.
     */
    public boolean permitsBooking() {
        return this == OFF_PLATFORM || this == AUTHORIZED || this == CAPTURED;
    }
}
