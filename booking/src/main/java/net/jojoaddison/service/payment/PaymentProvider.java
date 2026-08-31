package net.jojoaddison.service.payment;

/**
 * The payment seam — {@code decisions.md} D15/D31.
 *
 * <h2>What this deliberately does not model</h2>
 *
 * <p>Every method here concerns <strong>the customer's side of the money</strong>: committing it,
 * moving it, giving it back. <strong>Nothing here pays the professional.</strong> That omission is
 * the single most important property of this interface, and it is what makes it survivable when a
 * provider is finally chosen.
 *
 * <p>The two plausible models in Ghana settle that leg completely differently. A split-settlement
 * provider moves the professional's share itself at capture, and the platform's job is only to record
 * that it happened. A reconcile-afterwards arrangement has the platform receive everything and
 * disburse later, on its own schedule, against the ledger. If this interface had a
 * {@code payProfessional} on it, it would have picked one — and picking wrong is the expensive
 * mistake D15 was written to avoid, because a wrong abstraction costs more to remove than no
 * abstraction costs to add.
 *
 * <p>Payout's {@code Ledger} already records what each professional is owed, derived from completed
 * bookings. That record is correct under either model. How the money reaches them is the provider's
 * question and stays outside this seam until there is a provider to ask.
 *
 * <h2>And no timing model either</h2>
 *
 * <p>{@code authorize} and {@code capture} are separate calls because some providers separate them,
 * not because this estate has decided when each happens. A provider that only does immediate charges
 * implements {@code authorize} as a capture and returns {@link PaymentState#CAPTURED} — nothing here
 * requires two steps or forbids one.
 *
 * <h2>There is exactly one implementation today</h2>
 *
 * <p>{@link net.jojoaddison.config.PaymentConfiguration.UnconfiguredPaymentProvider}, which reports
 * {@link PaymentState#OFF_PLATFORM} and refuses everything else. This interface being unused in
 * anger is the accurate state of affairs, not an oversight: D15 has no provider and no Act 987
 * opinion, and writing a Paystack or Hubtel client before either exists would be the unfinished
 * integration rather than the seam.
 *
 * <h2>Nothing is persisted, and that is the same discipline</h2>
 *
 * <p>There is no {@code payment_attempt} table. The columns such a table needs are the provider's —
 * its reference format, its status vocabulary, its webhook identifiers — and inventing them now would
 * be guessing at the shape in the one place a wrong guess is expensive to undo, since a schema that
 * has run in production is a migration rather than an edit. When a provider is chosen, the table it
 * needs is obvious and this interface does not change.
 */
public interface PaymentProvider {
    /** For logs and for {@code GET /api/desk/privacy}-style introspection. {@code "none"} when absent. */
    String name();

    /**
     * Commits the customer's money to a booking, or explains why it cannot be committed.
     *
     * <p>Must not throw for an ordinary refusal — a declined card is an outcome, not an exception.
     * {@link PaymentState#FAILED} is for the provider being unreachable or answering with an error.
     */
    PaymentOutcome authorize(PaymentIntent intent);

    /** Moves previously authorized money. A provider without a two-step flow never sees this called. */
    PaymentOutcome capture(String providerReference);

    /** Returns money to the customer, in whole or in part. {@code amountMinor} is pesewas. */
    PaymentOutcome refund(String providerReference, long amountMinor, String reason);

    /** Asks the provider what it thinks the state is — the reconciliation call. */
    PaymentOutcome status(String providerReference);
}
