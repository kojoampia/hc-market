package net.jojoaddison.service.payment;

import java.util.Optional;

/**
 * Where an adapter that needs the customer's contact details gets them — {@code decisions.md} D50,
 * D72 §3, D74.
 *
 * <p><strong>{@link GatewayCustomerContacts} implements it, since D74.</strong> This interface said
 * "nothing in this repository implements it" for as long as the source was an open disclosure
 * decision; the architect answered it on 2026-09-10 (D72 §3) and the answer was the third of the
 * three sources below — the gateway's own account store, asked over
 * {@code GET /internal/customers/{login}/email} with the short-lived estate-signed token D38's
 * mechanism already mints. The three-sources section is kept as written, because two of the three
 * are still refused and the reasons have not changed.
 *
 * <h2>Why a provider has to ask, rather than being told</h2>
 *
 * <p>{@link PaymentIntent} carries a login and no contact details, and its javadoc says why — "which
 * identifier a provider needs is exactly the thing this record must not guess". Paystack's
 * {@code /transaction/initialize} requires an email address; Hubtel and MTN MoMo raise a prompt on a
 * phone number. Adding both to the intent would put two pieces of personal data on every payment in
 * the estate to satisfy whichever provider happens to be configured, which is the shape D22 exists to
 * be suspicious of and D41 exists to keep out of {@code payment_attempt}.
 *
 * <p>So the seam's answer, written down before there was an adapter to test it against, is that a
 * provider fetches what it needs <em>at its own boundary</em>. This is that boundary.
 *
 * <h2>The three sources that were considered, and why none of them is built</h2>
 *
 * <ol>
 *   <li><strong>The booking request.</strong> Rejected. An email typed into a booking wizard is a
 *       second, unverified contact detail for a person the estate already has one for, arriving
 *       through a different door — so the receipt for somebody's money goes to whichever address the
 *       client last sent. The prototype is explicit that this is not how it works: the account screen
 *       renders the email <em>read-only</em>, sourced from the BridgeCare record, and the booking
 *       wizard never asks for one. It is also a new client-supplied field that something downstream
 *       trusts, which is D22's rule verbatim.
 *   <li><strong>The login, when it happens to be an email.</strong> Rejected. The gateway's
 *       {@code LOGIN_REGEX} permits both spellings, so this would work for whoever registered with an
 *       address and fail for whoever did not — and it would fail at the moment they try to pay, which
 *       is the worst available moment for a rule that holds for a subset of users. All eighteen
 *       seeded customers are {@code firstname.lastname}.
 *   <li><strong>The account store, which is the gateway's.</strong> The correct source, and the one
 *       this interface is shaped for. It was not built for a year because standing up an endpoint that
 *       returns a person's email address by login is a disclosure decision about the estate's personal
 *       data — who may ask, under what authority, and whether the answer is routable — of exactly the
 *       kind D38 took for the erasure fan-out and D45 declined to take for the provider list. It is
 *       not a decision the payment seam may take on its own, and inventing a source is the one thing
 *       {@code service.payment.provider}'s package documentation exists to prevent.
 *       <strong>D72 §3 took it</strong>, on the terms it stated: the route predicates are what keep
 *       such an endpoint private, and Hubtel and MoMo will want a phone number through the same door.
 *       {@link GatewayCustomerContacts} is that decision executed, and D74's §4.3 answers the part
 *       D72's terms got wrong — the predicates keep catalog's {@code /internal/**} private and cannot
 *       keep the gateway's own, because there is no route in front of it.
 * </ol>
 *
 * <h2>Three answers, not two — and the third is the one D74 added</h2>
 *
 * <p>The signature says {@code Optional}, so it has two answers in it: an address, or none. That was
 * exactly right while the only implementation was {@link #unanswered()} and could not fail. A real
 * implementation makes a network call, and folding "we hold no address for this person" together with
 * "the account store could not be asked" would produce the estate's own favourite kind of defect: an
 * unreachable gateway logged as <em>"this estate holds no email"</em>, which points a reader at an
 * unimplemented decision that is implemented. That is a wrong diagnosis in the log, on the path where
 * a customer is trying to pay.
 *
 * <p>So {@link ContactsUnavailable} is the third answer, and the split follows {@code CatalogClient}'s
 * — {@code CatalogUnavailable} for "cannot ask", {@code UnknownOffering} for "asked, and there is no
 * such thing". Both still end in 502 and no booking, because {@code BookingPayments.take} wraps the
 * provider call and turns any {@code RuntimeException} into {@code FAILED} (D44); what differs is what
 * the log says, and that is the whole point of separating them.
 *
 * <h2>What absent means, and it is the estate's standing answer</h2>
 *
 * <p>Fail closed, exactly as an absent {@code HC_PRIVACY_PEPPER} does (D35): the service starts,
 * everything that does not need a contact detail keeps working, and the one call that does refuses.
 * For Paystack that is {@code authorize}, so a priced booking naming it answers <strong>502 and no
 * booking</strong> — no round trip, no money, and an ERROR line naming this decision. Which is what
 * every priced booking naming Paystack did before D74, and what one for a customer with no email
 * address on file still does.
 *
 * <p><strong>Exactly one implementation.</strong> {@code PaymentConfiguration} takes this through an
 * {@code ObjectProvider} and calls {@code getIfAvailable()}, which answers
 * {@code NoUniqueBeanDefinitionException} rather than choosing — so a second {@code @Component} fails
 * the context at startup. Whoever adds one marks it {@code @Primary}, and had better have an argument
 * for why two sources of one person's email address is a thing this platform wants.
 */
public interface CustomerContacts {
    /**
     * The customer's email address, or empty when this estate holds none for that login.
     *
     * <p>Empty is not an error and must not be logged as one by an implementation. It is a fact about
     * the account: the login is unknown to the store, or it is known and carries no address —
     * {@code email} has no {@code @NotNull} anywhere in the gateway's user model, so that is a
     * reachable state and not a corruption. The <em>caller</em> decides what an unanswerable contact
     * detail means for the call it was about to make, because only the caller knows whether it was
     * required.
     *
     * <p>"Could not be asked" is {@link ContactsUnavailable} and never empty — see the class comment.
     *
     * @param customerLogin the JWT subject the booking was made under. Never a display name, and
     *     never anything a client sent — see {@link PaymentIntent#customerLogin()}
     * @throws ContactsUnavailable if this estate could not put the question
     */
    Optional<String> emailOf(String customerLogin);

    /**
     * An estate that does not hold customer contact details.
     *
     * <p>Was the only implementation until D74, and is still what {@code PaymentConfiguration} hands an
     * adapter when no {@code CustomerContacts} bean is registered — a named, harmless answer rather
     * than a null collaborator, so an adapter that needs one has something to call and a single branch
     * to write. It answers empty for every login, including a blank one, and never throws.
     *
     * <p>Kept, and not only for that: the payment adapter unit tests use it as the "this estate cannot
     * name the customer" fixture, which is a case that must keep working whether or not a real
     * implementation exists.
     */
    static CustomerContacts unanswered() {
        return customerLogin -> Optional.empty();
    }

    /**
     * The question could not be put — not the answer that there is nothing to tell.
     *
     * <p>An unreachable account store, one that refuses the estate's own credential, one that answers
     * something unreadable. Unchecked, because the one caller that matters is inside
     * {@code PaymentProvider.authorize} and D44 already wraps exactly that call: this arrives at the
     * customer as the same 502-and-no-booking a declining provider gives, with an ERROR naming the
     * cause rather than a stack trace.
     *
     * <p><strong>Its message must never carry an email address</strong>, and on this path it never can:
     * the failure happens instead of an answer. It names the login, which the caller already had.
     */
    class ContactsUnavailable extends RuntimeException {

        public ContactsUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
