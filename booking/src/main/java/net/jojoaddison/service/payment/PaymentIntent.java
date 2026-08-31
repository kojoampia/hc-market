package net.jojoaddison.service.payment;

/**
 * What the platform wants to happen to money, in terms no provider dictates — {@code decisions.md}
 * D15/D31.
 *
 * @param bookingReference the thing being paid for, and the natural idempotency key: it is unique by
 *     schema and stable for the life of the booking, so a retried authorization is recognisable as
 *     the same one without inventing a second identifier to keep in step with the first
 * @param customerLogin who is paying. Not contact details — a provider that needs a phone number to
 *     raise a mobile-money prompt asks for it at its own boundary, because which identifier a
 *     provider needs is exactly the thing this record must not guess
 * @param amountMinor pesewas, matching every other money value in this estate. Never a double
 * @param currency explicit ISO code, for the same reason it is explicit on {@code Booking}
 * @param description what the customer sees on a statement
 */
public record PaymentIntent(String bookingReference, String customerLogin, long amountMinor, String currency, String description) {}
