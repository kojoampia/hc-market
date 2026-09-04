package net.jojoaddison.service.payment.provider;

import net.jojoaddison.service.payment.PaymentProviderProperties;

/**
 * Hubtel, as a name and a seam and nothing else yet — {@code decisions.md} D37/D45.
 *
 * <p>Chosen by D37 as one of the three the customer picks between, and the first of the two whose
 * shape is a prompt on the customer's phone rather than a page to visit — so a successful
 * {@code authorize} here is expected to produce
 * {@link net.jojoaddison.service.payment.PaymentOutcome#pendingOnDevice(String)} and no URL.
 * Registered only when {@code healthconnect.payments.hubtel.enabled} is true, which it is nowhere in
 * this repository, and it refuses everything it is asked.
 *
 * <h2>What this adapter needs before a line of it can be written</h2>
 *
 * <p>Nothing below is implemented and nothing below is guessed.
 *
 * <ol>
 *   <li><strong>The authorization call.</strong> Its URL, method and body; the amount's unit and
 *       whether it is a decimal rather than minor units, since everything in this estate is pesewas
 *       as a {@code long} and a conversion at this boundary is a place a rounding error becomes
 *       money; and — the one that has to be decided rather than looked up — <strong>where the
 *       customer's mobile number comes from</strong>. A device prompt needs a number,
 *       {@link net.jojoaddison.service.payment.PaymentIntent} carries a login and deliberately no
 *       contact details, and D15's note on that field says which identifier a provider needs is
 *       exactly what the intent must not guess. So it is fetched at this boundary, from the gateway's
 *       user store, and the question of what happens when the customer has no number on file is a
 *       product answer somebody has to give before this adapter can fail sensibly.
 *   <li><strong>Its response.</strong> Which field is the durable handle to keep, and whether one is
 *       returned at all at prompt time. A pending outcome with no handle is refused by
 *       {@code PaymentOutcome}'s constructor, deliberately: nothing could ever match the callback to
 *       the booking. If Hubtel issues the handle only on the callback, this adapter cannot use
 *       {@code PENDING} as it stands and that is a decision to bring back to the seam rather than to
 *       work around here.
 *   <li><strong>The status vocabulary.</strong> Every value, and its
 *       {@link net.jojoaddison.service.payment.PaymentState}. Unrecognised maps to {@code FAILED},
 *       never to anything that permits a booking.
 *   <li><strong>The callback payload.</strong> The shape, the field holding the handle from item 2,
 *       and whether a cancelled or expired prompt produces a callback at all — if it does not, a
 *       booking left in {@code PENDING_PAYMENT} has no exit, which is the expiry D43 deliberately did
 *       not build and which becomes real the day this adapter works.
 *   <li><strong>The signature.</strong> The algorithm, the key, what bytes it covers and the header
 *       it arrives in. This repository knows only that Hubtel has one; it has no belief about its
 *       shape and should not acquire one from anywhere but the documentation.
 *   <li><strong>The credentials.</strong> {@code healthconnect.payments.hubtel.secret} holds a single
 *       value for verifying callbacks. Outbound calls are understood to authenticate with a client id
 *       and secret pair — two values, not one — so the settings record grows a field in the same
 *       commit as the code that reads it, and not before.
 * </ol>
 */
public class HubtelPaymentProvider extends ProviderAwaitingIntegration {

    /** The name a customer chooses and a callback is addressed to: {@code /webhooks/payments/hubtel}. */
    public static final String NAME = "hubtel";

    public HubtelPaymentProvider(PaymentProviderProperties.Provider settings) {
        super(NAME, settings);
    }
}
