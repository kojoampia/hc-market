package net.jojoaddison.service.payment;

import java.util.Optional;

/**
 * Where an adapter that needs the customer's contact details gets them — {@code decisions.md} D49.
 *
 * <p><strong>Nothing in this repository implements it.</strong> That is the whole of what this
 * interface says, and it is deliberate: the missing piece is a decision rather than a class.
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
 *       this interface is shaped for. It is not built because standing up an endpoint that returns a
 *       person's email address by login is a disclosure decision about the estate's personal data —
 *       who may ask, under what authority, and whether the answer is routable — of exactly the kind
 *       D38 took for the erasure fan-out and D45 declined to take for the provider list. It is not a
 *       decision the payment seam may take on its own, and inventing a source is the one thing
 *       {@code service.payment.provider}'s package documentation exists to prevent.
 * </ol>
 *
 * <h2>What absent means, and it is the estate's standing answer</h2>
 *
 * <p>Fail closed, exactly as an absent {@code HC_PRIVACY_PEPPER} does (D35): the service starts,
 * everything that does not need a contact detail keeps working, and the one call that does refuses.
 * For Paystack that is {@code authorize}, so a priced booking naming it answers <strong>502 and no
 * booking</strong> — no round trip, no money, and an ERROR line naming this decision. Which is what
 * every priced booking naming Paystack did before this package, for a different reason.
 *
 * <p>Whoever answers the decision writes one class implementing this and annotates it
 * {@code @Component}. Nothing else changes: {@code PaymentConfiguration} already prefers a registered
 * implementation over {@link #unanswered()}.
 */
public interface CustomerContacts {
    /**
     * The customer's email address, or empty when this estate cannot answer.
     *
     * <p>Empty is not an error and must not be logged as one by an implementation: on today's estate
     * it is the only possible answer. The <em>caller</em> decides what an unanswerable contact detail
     * means for the call it was about to make, because only the caller knows whether it was required.
     *
     * @param customerLogin the JWT subject the booking was made under. Never a display name, and
     *     never anything a client sent — see {@link PaymentIntent#customerLogin()}
     */
    Optional<String> emailOf(String customerLogin);

    /**
     * The implementation this estate runs: it does not hold customer contact details.
     *
     * <p>A named, harmless answer rather than a null collaborator, so an adapter that needs one has
     * something to call and a single branch to write. It answers empty for every login, including a
     * blank one.
     */
    static CustomerContacts unanswered() {
        return customerLogin -> Optional.empty();
    }
}
