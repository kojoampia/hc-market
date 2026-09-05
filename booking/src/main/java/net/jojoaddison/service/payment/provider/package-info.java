/**
 * The three payment providers D37 chose, as far as they can honestly be built here —
 * {@code decisions.md} D45, and D49 for the one of them that now is.
 *
 * <h2>Read this before implementing any of them</h2>
 *
 * <p><strong>Paystack is implemented; Hubtel and MTN MoMo are not.</strong> D49 built the first one
 * when working evidence for its wire format turned up in a sibling product, and the rule it followed
 * is the rule below rather than an exception to it: {@code authorize} and {@code readCallback} are
 * written because the evidence covers them, and {@code capture}, {@code refund},
 * {@code voidAuthorization} and {@code status} still refuse because it does not. An adapter is not a
 * thing that is finished or unfinished — it is six calls, each of which is either sourced or guessed.
 *
 * <p>What remains here for the other two is a <strong>seam with the provider-specific parts
 * missing</strong>: a name, its settings, and a refusal in place of every call that would need to
 * know how the provider actually speaks. That is not an unfinished afternoon's work. It is the
 * deliberate limit of what WP-13 could produce, and the reason is worth keeping when somebody arrives
 * with credentials and is tempted to delete this comment.
 *
 * <p>WP-13 was built with <strong>no network access, no provider account and no credentials</strong>.
 * Paystack's, Hubtel's and MTN MoMo's documentation could not be read and none of the three could be
 * called. A signature scheme, a JSON field path, a status mapping or a callback envelope written
 * under those conditions is fiction — and it is fiction that <em>compiles, and passes the tests
 * written to match it</em>, because the only thing it is ever checked against is the same assumption
 * that produced it. This repository has already paid for the general version of that lesson twice:
 * D9's skipped suite, where everything compiled for a week, and D14's green publish that put no image
 * in the registry. A green test over a seam nothing real has touched is not evidence.
 *
 * <p>And the place it would have gone wrong is the worst one available. An adapter is reached on the
 * path where a customer's money is already committed: a wrong signature check accepts a forgery or
 * rejects every genuine callback, and a wrong status mapping creates bookings for money that never
 * arrived or cancels bookings whose money did.
 *
 * <h2>What is built, and is verifiable here</h2>
 *
 * <p>Everything around the adapters, which was most of WP-13: the registry that keys them by name
 * ({@link net.jojoaddison.service.payment.PaymentProviders}), the customer's choice and its
 * validation against what this service is actually configured for, the gateway route and the gateway
 * permit that expose the webhook, the per-provider secret and the refusal when it is absent, and the
 * dispatch by which a callback addressed to one provider reaches that provider's adapter and no
 * other. All of that can be exercised against a substituted provider, and is.
 *
 * <h2>What each adapter still needs, in one place</h2>
 *
 * <p>The per-provider lists are on the classes, because that is where somebody implementing one will
 * be looking. They share a shape, and these are the questions to answer for any of the three:
 *
 * <ol>
 *   <li>the authorization call — URL, method, body, the amount's unit, and where the platform's own
 *       booking reference goes;
 *   <li>its response — <strong>which field is the durable handle</strong>, and for Paystack which is
 *       the URL the customer visits;
 *   <li>the status vocabulary — every value, and the
 *       {@link net.jojoaddison.service.payment.PaymentState} each maps to, with anything
 *       unrecognised mapping to {@code FAILED};
 *   <li>the callback payload — its shape, and where in it the handle from (2) appears;
 *   <li>the signature — the algorithm, the key, <strong>exactly what bytes it is computed over</strong>,
 *       and the header it arrives in;
 *   <li>the credentials — which values the outbound call needs, as against the single {@code secret}
 *       {@link net.jojoaddison.service.payment.PaymentProviderProperties} models for callbacks.
 * </ol>
 *
 * <p>Two facts about this estate that an implementer needs and will not find in a provider's
 * documentation. Money here is <strong>minor units as a {@code long}</strong> (pesewas) with an
 * explicit ISO currency, never a decimal — a provider that speaks in decimals is converted at this
 * boundary, and that conversion is a place a rounding error becomes money. And the intent carries a
 * <strong>login and no contact details</strong>, deliberately: a provider that needs a phone number
 * or an email fetches it at its own boundary, because which identifier a provider needs is the thing
 * {@link net.jojoaddison.service.payment.PaymentIntent} must not guess.
 *
 * <p><strong>That second one is now a known cost rather than a principle</strong>, and whoever
 * implements Hubtel or MoMo meets it before anything else. "At its own boundary" turned out to mean
 * "from a source this estate does not have": Paystack needs an email, both of the others need a phone
 * number, and the account store that holds them belongs to the gateway with no endpoint to ask.
 * {@link net.jojoaddison.service.payment.CustomerContacts} is that boundary, it has no
 * implementation, and giving it one is a disclosure decision rather than a class — D49 sets out the
 * three candidate sources and why two of them were rejected. Do not solve it by adding a field to the
 * booking request; that is the first of the three and it is rejected on D22's grounds.
 *
 * <h2>Act 987 is not answered here, and this package must not pre-empt it</h2>
 *
 * <p>Whether a split-settlement model clears Ghana's Payment Systems and Services Act is a question
 * for a person with the standing to answer it, and WP-13 did not answer it.
 * {@link net.jojoaddison.service.payment.PaymentProvider} deliberately has
 * <strong>no method that pays the professional</strong>, and that omission is what lets this seam
 * survive the answer either way — split-at-capture and reconcile-afterwards are the same interface
 * from here. Adding a settlement or transfer call to an adapter in this package would decide it by
 * accident, in code, which is the expensive mistake D15 was written to avoid.
 */
package net.jojoaddison.service.payment.provider;
