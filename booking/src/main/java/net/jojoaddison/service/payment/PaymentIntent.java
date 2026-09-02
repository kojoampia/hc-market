package net.jojoaddison.service.payment;

/**
 * What the platform wants to happen to money, in terms no provider dictates — {@code decisions.md}
 * D15/D31/D41.
 *
 * @param bookingReference the thing being paid for, and the key a provider should de-duplicate this
 *     call on.
 *     <p><strong>Read what that does and does not cover.</strong> This used to be described as "the
 *     natural idempotency key … so a retried authorization is recognisable as the same one", and the
 *     call site defeats that: {@code CustomerBookingResource.create} mints {@code "b-" + a fresh
 *     UUID} on every request, so two submissions of one wizard are two references, two intents and —
 *     the day a provider exists — two charges. The reference is unique per <em>booking attempt</em>,
 *     not per intention to book, and the comment promised the second.
 *     <p>What it does buy, which is real and is why it is still the key: a provider's own retry, a
 *     client library's retry, and every later call about this payment all name the same booking, so
 *     the provider can collapse duplicates of <em>this</em> call and the platform can reconcile its
 *     {@code payment_attempt} rows against the provider's records without a second identifier to keep
 *     in step with the first.
 *     <p>What would close the gap is a key chosen by whoever intends the booking — an
 *     {@code Idempotency-Key} header on {@code POST /api/bookings}, carried through to here — and it
 *     is not built: it is a contract with the client rather than with the provider, and the payment
 *     seam is not the place to invent one unilaterally. Until then a double-submitted wizard is two
 *     bookings, which the estate already tolerates because they collide on the availability slot.
 * @param customerLogin who is paying. Not contact details — a provider that needs a phone number to
 *     raise a mobile-money prompt asks for it at its own boundary, because which identifier a
 *     provider needs is exactly the thing this record must not guess.
 *     <p>It reaches the provider and stops there. It is deliberately <em>not</em> stored on
 *     {@code payment_attempt}: that row would otherwise become a register of who paid for what,
 *     surviving the erasure that removed the same login from the booking beside it
 * @param amountMinor pesewas, matching every other money value in this estate. Never a double
 * @param currency explicit ISO code, for the same reason it is explicit on {@code Booking}
 * @param description what the customer sees on a statement
 */
public record PaymentIntent(String bookingReference, String customerLogin, long amountMinor, String currency, String description) {}
