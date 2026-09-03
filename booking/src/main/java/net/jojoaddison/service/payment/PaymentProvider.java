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
 * <h2>One thing is persisted: the handle</h2>
 *
 * <p>This used to say that nothing was, and gave a good reason — the columns a payment table needs
 * are the provider's own, and inventing them before there is a provider is guessing at a shape that a
 * migration rather than an edit would later have to change. That argument holds for the provider's
 * status vocabulary and its webhook identifiers. It does not hold for {@link
 * PaymentOutcome#providerReference()}, and D41 separates the two: every other field of an outcome can
 * be asked for again, and the handle cannot, because it is issued by somebody else and derivable from
 * nothing. Dropping it makes {@link #capture}, {@link #refund}, {@link #voidAuthorization} and {@link
 * #status} uncallable — every method below takes it as its first argument — so the seam could report a
 * lifecycle it had made itself unable to complete.
 *
 * <p>So {@code payment_attempt} holds the handle, the amount, the state and nothing of the provider's
 * that would need re-modelling later. See {@code net.jojoaddison.domain.PaymentAttempt}.
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

    /**
     * Moves previously authorized money. A provider without a two-step flow never sees this called.
     *
     * <p><strong>The amount is explicit, and it may be less than was authorized.</strong> Partial
     * capture is ordinary — a session that ran short, a booking the professional part-fulfilled — and
     * a {@code capture(reference)} that could only ever take the whole authorization would have made
     * the estate's only route to a smaller charge a full capture followed by a refund, which is two
     * entries on the customer's statement for one transaction and, at most providers, two fees.
     * Passing the authorized amount is a full capture; a provider that cannot do partials must refuse
     * a smaller one with {@link PaymentState#FAILED} rather than quietly take the lot.
     *
     * <p>The authorized amount is on the {@code payment_attempt} row, which is where the caller gets
     * it. It deliberately does not come from the booking: what may be captured is bounded by what the
     * provider agreed to, not by what the catalogue says the service costs.
     *
     * @param currency the ISO code the money was authorized in. Explicit for the estate's standing
     *     reason — a bare {@code long} is not an amount of money — and because a capture denominated
     *     in something other than the authorization is a mistake a provider should be able to refuse
     */
    PaymentOutcome capture(String providerReference, long amountMinor, String currency);

    /**
     * Returns money to the customer, in whole or in part. {@code amountMinor} is pesewas.
     *
     * @param currency the ISO code. It used to be absent, which broke the house rule that money is
     *     minor units <em>plus</em> an explicit code — and did it in the method where the omission is
     *     least recoverable, since a refund in the wrong currency is a second wrong transaction
     *     rather than a rejected one
     */
    PaymentOutcome refund(String providerReference, long amountMinor, String currency, String reason);

    /**
     * Releases an authorization the platform has decided not to use, before any money moves.
     *
     * <p>The compensating action, and the reason it is on this interface rather than deferred with
     * D15's settlement questions: void is a distinct call at every real provider, it is not a choice
     * about how the money is eventually split, and without it the estate has no answer at all to
     * "the customer's money is committed and the booking could not be created". {@code
     * BookingPayments.release} is the one caller today.
     *
     * <p>Expected to answer {@link PaymentState#VOIDED}. A provider that has already settled should
     * answer {@link PaymentState#FAILED} rather than pretending — the caller's next move is a refund,
     * which is a different call and a different entry on the customer's statement.
     *
     * @param reason for the provider's own record, and for whoever reads it there afterwards. It
     *     describes the platform's decision, never the customer
     */
    PaymentOutcome voidAuthorization(String providerReference, String reason);

    /** Asks the provider what it thinks the state is — the reconciliation call. */
    PaymentOutcome status(String providerReference);

    /**
     * Turns a request that claims to be this provider's webhook into an outcome, or refuses it —
     * {@code decisions.md} D43.
     *
     * <p><strong>This method is the authentication.</strong> Nothing before it has established who is
     * calling: {@code /webhooks/payments/{provider}} takes no token, because a provider cannot be
     * given one, and the security chain in front of it permits the request precisely so that this can
     * decide. An implementation must verify the callback by the provider's own scheme — Paystack signs
     * the raw body with HMAC-SHA512 under the secret key, Hubtel and MoMo have their own — and must
     * throw {@link PaymentCallbackRefused} for anything it cannot prove came from the provider.
     * Returning an outcome is a statement that this really is the provider speaking, and everything
     * downstream acts on it: a booking is created, or cancelled, on the strength of it.
     *
     * <p><strong>Verify before parsing, and parse the same bytes that were verified.</strong> The
     * signature covers what was sent, so a body round-tripped through a parser is a different body.
     * See {@link PaymentCallback}.
     *
     * <p>The outcome's {@code providerReference} is the whole point of the return value: it is how the
     * platform finds the {@code payment_attempt} row, and through it the booking. An outcome without
     * one cannot be applied to anything, so an implementation that cannot extract a reference should
     * refuse rather than return.
     *
     * <p>An implementation must be prepared for the <em>same</em> callback more than once — every one
     * of these providers retries until it gets a 2xx, and some send a duplicate for good measure. It
     * does not have to de-duplicate: {@code PaymentConfirmations} decides idempotency from the state
     * of the booking rather than from a record of what has been seen, so the honest thing for an
     * adapter to do is to report each callback faithfully.
     */
    PaymentOutcome readCallback(PaymentCallback callback);
}
