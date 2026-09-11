package net.jojoaddison.service.payment.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.jojoaddison.service.payment.CustomerContacts;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProviderProperties;
import net.jojoaddison.service.payment.PaymentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Paystack, integrated — {@code decisions.md} D37/D45/D50.
 *
 * <p>The first adapter in this estate that speaks to the provider it names. D45 left all three as
 * seams because WP-13 had no network access, no account and no credentials, and said what each one
 * would need before a line of it could be written. <strong>That evidence arrived for this one, from
 * inside the estate rather than from a specification</strong>: {@code hc-crowdfund-app} has run a
 * live Paystack integration for months, and its adapter, its tests and its configuration answer
 * every question the list asked. Nothing here is guessed and nothing here was copied — a different
 * seam, a different domain and a different Jackson — but the wire format below is fact rather than
 * plausibility, which is the whole of what D45 was waiting for.
 *
 * <h2>What is implemented, and what is deliberately still a seam</h2>
 *
 * <p>{@code authorize} and {@code readCallback}: the booking path, exactly the two this package's
 * documentation says to do first. The working integration does {@code initialize} plus the webhook
 * and <strong>nothing else</strong>, so {@code capture}, {@code refund}, {@code voidAuthorization}
 * and {@code status} are left refusing. That is not laziness and it is not free — D50 states what it
 * costs, and the sharpest edge is that a {@code PENDING_PAYMENT} booking whose creation then fails
 * cannot have its live payment cancelled, so {@code BookingPayments.release} flags the attempt row
 * for a person. Writing them from what a Paystack API plausibly looks like would be the invention
 * D45 refused, on the calls that move money that has already been taken.
 *
 * <h2>The authorization, in full</h2>
 *
 * <p>{@code POST /transaction/initialize}, {@code Authorization: Bearer sk_…}, a JSON body of
 * {@code email}, {@code amount} and {@code reference}. Three of those are worth stating because they
 * are where a mistake would be expensive and silent:
 *
 * <ul>
 *   <li><strong>{@code amount} is minor units</strong>, which is what this estate already stores.
 *       Pesewas go on the wire unchanged, and there is no conversion here to get wrong by a factor
 *       of a hundred.
 *   <li><strong>{@code reference} is ours and is sent</strong> — Paystack does not issue it. So
 *       {@link PaymentIntent#bookingReference()} goes out, comes back on the callback, and is what
 *       {@code PaymentConfirmations} finds the {@code payment_attempt} row by. The handle kept is
 *       nonetheless the one in Paystack's <em>response</em>, not the one that was sent: they are the
 *       same value today and the provider's account of its own identifier is the one that will
 *       appear on the callback if they ever stop being.
 *       <p><strong>What that reference has to be is unique per attempt, and today it is unique per
 *       attempt by accident.</strong> See {@link #authorize} — the source this integration was read
 *       from was careful about exactly this axis and this adapter does not have to be yet.
 *   <li><strong>The answer is {@link PaymentState#PENDING}</strong> and can be nothing else. What
 *       comes back is a page for the customer to visit, so at the moment this returns nobody has
 *       paid anything — which is precisely the case D43 built {@code PENDING} and
 *       {@code PENDING_PAYMENT} for.
 * </ul>
 *
 * <h2>The callback is the authentication, and it is HMAC-SHA512 over the raw body</h2>
 *
 * <p>Hex-encoded, computed under the secret key, presented in {@code x-paystack-signature}. D43
 * guessed all four of those from memory and hedged every word of the guess; they are now confirmed
 * against a working integration. The comparison is constant-time, and <strong>case is folded before
 * comparing rather than during</strong> — hex is case-insensitive, and a comparison that branches on
 * case is not constant-time either, so the tolerance and the timing property have to be arranged in
 * that order.
 *
 * <p>{@code charge.success} is the only event that permits a booking. Everything else is
 * {@link PaymentState#FAILED} <em>carrying the reference</em>, which is not the same as
 * {@link PaymentOutcome#failed(String)} and matters: that factory drops the handle, and a failure
 * outcome naming no payment leaves the customer's booking in {@code PENDING_PAYMENT} for ever while
 * Paystack retries a callback that can never be matched to anything.
 *
 * <h2>The key's shape is checked, because the mistake is invisible until somebody pays</h2>
 *
 * <p>Paystack issues {@code pk_}/{@code sk_} across {@code test_}/{@code live_} and its dashboard
 * lists them side by side, so pasting the public key into the secret's slot is an easy slip and
 * nothing downstream catches it: the service starts, offers Paystack, and then 401s at
 * {@code /transaction/initialize} the first time a customer picks it — and cannot verify a signature
 * either, since the callback HMAC is computed with the secret key. It is refused here at both doors
 * and announced at startup, which is where the mistake belongs.
 *
 * <h2>The email address, which this estate could not supply for a year</h2>
 *
 * <p>Paystack's initialize requires one. {@link PaymentIntent} carries a login and no contact details,
 * deliberately, and nothing implemented {@link CustomerContacts} — because the only defensible source
 * is the account store and standing up an endpoint that returns a person's email by login is a
 * disclosure decision the payment seam may not take alone. <strong>D72 §3 took it and D74 built
 * it</strong>: {@code GatewayCustomerContacts} asks the gateway with a short-lived estate-signed token,
 * so {@code authorize} can name who is paying.
 *
 * <p><strong>The refusal below is still live, and for two reasons rather than one.</strong> An account
 * that carries no email address answers empty — a reachable state, since {@code email} has no
 * {@code @NotNull} in the gateway's user model — and so does a login the account store has never heard
 * of, which on the quality box is every one of the eighteen seeded customers, because those are rows in
 * four services and not gateway accounts. Either way {@code authorize} refuses <strong>before the round
 * trip</strong>: no request, no reference, no money, and a 502. Read {@link CustomerContacts} for the
 * three candidate sources, why two of them stay rejected, and what {@code ContactsUnavailable} means
 * beside empty.
 */
public class PaystackPaymentProvider extends ProviderAwaitingIntegration {

    private static final Logger LOG = LoggerFactory.getLogger(PaystackPaymentProvider.class);

    /** The name a customer chooses and a callback is addressed to: {@code /webhooks/payments/paystack}. */
    public static final String NAME = "paystack";

    static final String DEFAULT_BASE_URL = "https://api.paystack.co";

    /**
     * How long to wait on Paystack before giving up, when nothing is configured.
     *
     * <p>This call sits inside {@code POST /api/bookings}, so the customer is watching it. Ten
     * seconds is long enough for a payment API having a slow moment and short enough that a provider
     * which has stopped answering does not hold booking's request threads.
     */
    static final int DEFAULT_TIMEOUT_MS = 10_000;

    private static final String INITIALIZE = "/transaction/initialize";

    /**
     * Read one transaction's state back. {@code GET /transaction/verify/{reference}} takes OUR reference
     * — the same one {@code authorize} sent — which is why this call exists at all: the estate can ask
     * about a payment using the only identifier it has.
     */
    private static final String VERIFY = "/transaction/verify/";

    /**
     * Give money back. {@code POST /refund} with {@code {transaction, amount}}, where {@code transaction}
     * accepts the transaction reference.
     */
    private static final String REFUND = "/refund";

    /** Confirmed against a working integration; D43 guessed this spelling and hedged it. */
    private static final String SIGNATURE_HEADER = "x-paystack-signature";

    private static final String SUCCESS_EVENT = "charge.success";

    private static final String SECRET_KEY_PREFIX = "sk_";

    /**
     * The one currency this adapter will let money be taken in.
     *
     * <p><strong>Not a limitation of Paystack — a limitation of the evidence.</strong> The working
     * integration sends no currency field at all, so the amount is denominated by whatever the
     * merchant account settles in, and this adapter has nothing telling it that a per-transaction
     * currency is even accepted. Every price in this estate is GHS. A booking denominated in
     * something else would therefore be charged as that many minor units of the account's currency —
     * a silent mis-charge rather than a rejected call — so it is refused as {@link
     * PaymentState#FAILED} instead, which is a 502 and no booking. Whoever adds a second currency
     * adds it here with the documentation that says how to declare it.
     */
    static final String SETTLES_IN = "GHS";

    private final RestClient http;
    private final CustomerContacts contacts;
    private final ObjectMapper json;

    public PaystackPaymentProvider(
        PaymentProviderProperties.Provider settings,
        RestClient.Builder builder,
        ObjectMapper json,
        CustomerContacts contacts
    ) {
        super(NAME, settings);
        int timeout = settings.timeoutMsOr(DEFAULT_TIMEOUT_MS);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        this.http = builder.baseUrl(settings.baseUrlOr(DEFAULT_BASE_URL)).requestFactory(factory).build();
        this.json = json;
        this.contacts = contacts;
    }

    /**
     * Four of the six, and the other two do not exist in Paystack's model — {@code decisions.md} D86.
     *
     * <p>This list is the estate's account of itself at startup: WARN for a seam, INFO for an
     * integration (see {@code ProviderAwaitingIntegration}). It names what is <em>written</em>, and
     * D86 is explicit that written is not the same as verified against a live account — nothing here
     * has spoken to Paystack, and {@code status} and {@code refund} are written from the published API
     * rather than from a round trip anybody has watched.
     *
     * <p>{@code capture} and {@code voidAuthorization} are absent because there is nothing for them to
     * do, not because they are unwritten. {@code /transaction/initialize} is a charge, not a two-step
     * hold: the money moves when the customer completes the checkout, which arrives as
     * {@code charge.success}. So there is no authorization held separately to capture, and none to
     * void — before the customer pays there is nothing, and afterwards the operation is a refund. The
     * interface's own javadoc anticipates exactly this: <em>"a provider that only does immediate
     * charges implements authorize as a capture and returns CAPTURED — nothing here requires two steps
     * or forbids one."</em> Both therefore keep {@code ProviderAwaitingIntegration}'s refusal, and each
     * overrides it only to say <strong>why</strong>, because "not integrated" and "not a thing this
     * provider has" are different facts for whoever reads the log.
     */
    @Override
    protected List<String> integratedCalls() {
        return List.of("authorize", "readCallback", "status", "refund");
    }

    /**
     * Starts a Paystack transaction and hands the customer somewhere to go.
     *
     * <p>Four refusals come before the round trip, in this order, and each of them is cheaper than
     * the call it replaces: a currency this adapter cannot vouch for, a secret key that is not one, a
     * customer this estate cannot name to Paystack, and — the one that never fires here, because
     * {@code BookingPayments.take} has already handled it — nothing to pay.
     *
     * <p>Nothing that comes back is caught and softened. A non-2xx, a read timeout or a
     * {@code javascript:} authorization URL all throw, and {@code BookingPayments} answers
     * {@link PaymentState#FAILED} with the whole exception at ERROR — which is the shape D44 built
     * for a real adapter, and better than a composed {@code failed} because it carries a stack trace
     * to the one line an operator will read.
     *
     * <h2>The reference sent is the booking's, and that is a decision with a date on it</h2>
     *
     * <p>The integration this was read from mints {@code "HC-" + id + "-" + random8}: it <em>had</em>
     * the domain identifier and appended fresh randomness anyway, <strong>per attempt</strong>, because
     * a payment provider will not take the same reference twice. That is not this adapter's shape and
     * the difference is deliberate rather than overlooked — but it holds for one reason only.
     *
     * <p><strong>A booking reference is already per-attempt here.</strong> {@code
     * CustomerBookingResource.create} mints a fresh {@code b-<8 hex>} on every request, and there is no
     * "retry the payment for booking X" path anywhere in this estate — the authorization happens once,
     * before the booking row exists. So the invariant Paystack cares about is satisfied today by the
     * shape of the domain rather than by a suffix, and adding one would buy nothing while giving
     * something up: sending our own reference makes Paystack a second check on a reference collision,
     * and it checks <em>before</em> the money moves. A suffix makes every reference unique at Paystack,
     * so a {@code b-} collision would be caught only by the unique constraint when the booking is
     * written — which is D41's expensive path, money committed and the booking gone, with {@code
     * voidAuthorization} still refusing here.
     *
     * <p><strong>The day it becomes a wall, and what to do then.</strong> Add any path that authorizes
     * twice for one booking — the obvious companion to D43's dead-end cancel, "pay again" — and Paystack
     * refuses the second reference. The customer gets a 502 that retrying cannot escape, and nothing in
     * the failure names a reference: it arrives as a {@code RestClientResponseException} whose message
     * happens to quote the response body, and no more. Whoever adds that path adds a per-attempt suffix
     * <strong>in the same commit</strong>, keeping {@code bookingReference} as the prefix so the booking
     * stays recoverable from the handle. {@code payment_attempt} is already built for it —
     * {@code PaymentRecorder.record}'s javadoc says two attempts against one booking may legitimately
     * carry the same reference, which describes a world this adapter cannot currently produce.
     * Backlog {@code NEW-11}.
     */
    @Override
    public PaymentOutcome authorize(PaymentIntent intent) {
        if (!SETTLES_IN.equalsIgnoreCase(intent.currency())) {
            // Composed here from this platform's own vocabulary, and it names the currency it can
            // take rather than the one it was handed: the second is a value off a booking and the
            // first is a fact about this adapter.
            LOG.error("paystack was asked to authorize booking {} in a currency this adapter cannot declare", intent.bookingReference());
            return PaymentOutcome.failed("the paystack payment adapter can only take %s".formatted(SETTLES_IN));
        }
        requireASecretKey();
        String email = contacts
            .emailOf(intent.customerLogin())
            .map(String::trim)
            .filter(address -> !address.isEmpty())
            .orElseThrow(() ->
                new UnsupportedOperationException(
                    "paystack needs the customer's email address to start a transaction and this estate holds none for " +
                        "that login — either the account store has no such account, or it has one with no address on " +
                        "it. Not the same as being unable to ask, which is ContactsUnavailable. See CustomerContacts " +
                        "(decisions.md D50, D74)"
                )
            );

        // The reference sent is this platform's own booking reference — Paystack does not issue one,
        // and this is what comes back on the callback. It is unique per attempt only because a booking
        // reference is minted per request and nothing here authorizes twice; see the method comment
        // before adding anything that does.
        InitializeResponse answered = http
            .post()
            .uri(INITIALIZE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + signingSecret())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", email, "amount", intent.amountMinor(), "reference", intent.bookingReference()))
            .retrieve()
            .body(InitializeResponse.class);

        InitializeData started = answered == null ? null : answered.data();
        if (started == null || isBlank(started.authorizationUrl()) || isBlank(started.reference())) {
            // Paystack's own message goes to the log and no further. It is a third party's prose on
            // its way to a response body, which is the fourth road D44 closed and the cheapest to
            // reopen — a decline message naming a cardholder is a real shape for one of these.
            LOG.error(
                "paystack accepted the request for booking {} without returning a payment to complete; it said: {}",
                intent.bookingReference(),
                answered == null ? "nothing" : answered.message()
            );
            return PaymentOutcome.failed("the paystack payment provider returned no payment to complete");
        }
        // pendingAt scheme-checks the URL where it is built (D43). A relayed javascript: or data: URL
        // throws here rather than reaching a browser's address bar, and is deliberately not caught:
        // an outcome is not the place to report that a provider tried something.
        return PaymentOutcome.pendingAt(started.reference(), started.authorizationUrl());
    }

    /**
     * Establishes that a callback really is Paystack, and says what it reports.
     *
     * <p>Order is load-bearing: <strong>verify, then parse, and parse the same bytes that were
     * verified.</strong> The signature covers what was sent, so a body that has been round-tripped
     * through a parser is a different body — {@code PaymentCallback} keeps the raw text for exactly
     * this, and everything below reads {@code callback.body()} once.
     *
     * <p><strong>Every refusal is the same refusal.</strong> No secret, a secret that is not a secret
     * key, no signature, a wrong signature, a body that will not parse, a body naming no payment: one
     * exception type, which the endpoint renders as one 401 with no detail. An adapter that let a
     * {@code JacksonException} escape would tell a prober that their signature was right and only
     * their JSON was wrong, which is the oracle D44's review took out of the endpoint and which an
     * adapter can put straight back.
     */
    @Override
    public PaymentOutcome readCallback(PaymentCallback callback) {
        if (!canVerifyCallbacks()) {
            // The inherited refusal, so that an absent secret reads identically whichever adapter it
            // is absent from — and so this class holds no second opinion about what that message says.
            return super.readCallback(callback);
        }
        if (!isSecretKey(signingSecret())) {
            throw new PaymentCallbackRefused(
                "the paystack adapter's configured secret is not a secret key (it must start with 'sk_'), so no callback " +
                    "signature can be verified — a 'pk_' value is the public key (decisions.md D50)"
            );
        }
        String presented = callback.header(SIGNATURE_HEADER);
        String body = callback.body();
        if (isBlank(presented) || body == null || body.isEmpty()) {
            throw new PaymentCallbackRefused("a callback claiming to be paystack carried no signature, or no body to have signed");
        }
        if (!signatureMatches(body, presented)) {
            throw new PaymentCallbackRefused("a callback claiming to be paystack did not carry this estate's signature over its body");
        }

        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException notJson) {
            // Reachable by whoever holds the signing secret and by nobody else, since the signature
            // has already verified. Still refused rather than thrown: see the method comment.
            throw new PaymentCallbackRefused("a verified paystack callback did not parse as JSON");
        }
        String reference = text(root.path("data").path("reference"));
        if (isBlank(reference)) {
            // PaymentProvider.readCallback's own rule: an outcome with no reference cannot be applied
            // to anything, so refusing is better than returning something PaymentConfirmations will
            // answer 404 to for ever.
            throw new PaymentCallbackRefused("a verified paystack callback named no payment this estate could match to a booking");
        }
        String event = text(root.path("event"));
        if (SUCCESS_EVENT.equals(event)) {
            // CAPTURED rather than AUTHORIZED: initialize is a charge rather than a two-step hold, so
            // charge.success means the money has moved. holdsMoney() is what decides whether an
            // abandoned booking needs a refund or a void, and it has to be right about that.
            return PaymentOutcome.captured(reference);
        }
        // Anything unrecognised is FAILED and never a booking-permitting state — the rule this
        // package's documentation sets for every adapter. The reference is kept: PaymentOutcome.failed
        // would drop it, and a failure naming no payment cannot cancel the booking that is waiting.
        LOG.info("paystack reported {} for payment {}", event, reference);
        return new PaymentOutcome(PaymentState.FAILED, reference, "the paystack payment provider did not report a successful charge");
    }

    /**
     * Asks Paystack what became of one transaction — {@code decisions.md} D86.
     *
     * <p>{@code GET /transaction/verify/{reference}}, with our own reference, which is the only
     * identifier this estate holds for a payment. Read-only: it moves no money and creates nothing, so
     * it is the one of the four that cannot do harm if this adapter's reading of the API is wrong.
     *
     * <p><strong>What it is for.</strong> D43 left a booking in {@code PENDING_PAYMENT} until a webhook
     * confirms the money, and a webhook that never arrives leaves it there for ever — nothing in this
     * estate sweeps it. This call is what a sweep or an operator would use to ask. Nothing calls it yet;
     * it is reachable from the registry and from nowhere else, which is deliberate and is why no
     * behaviour changes by adding it.
     *
     * <p><strong>The mapping, and the one asymmetry in it.</strong> Paystack answers
     * {@code data.status}: {@code success} is the money moved, {@code failed} and {@code abandoned} are
     * refusals, {@code pending}/{@code ongoing} mean the customer has not finished. A success maps to
     * {@link PaymentState#CAPTURED} rather than {@code AUTHORIZED} for the same reason
     * {@code readCallback} does — initialize is a charge and not a hold, and {@code holdsMoney()}
     * decides whether an abandoned booking needs a refund or a void.
     *
     * <p><strong>Every non-success keeps the reference</strong>, through the canonical constructor.
     * {@code PaymentOutcome.failed} drops it, and a failure naming no payment cannot cancel the booking
     * that is waiting — D50's review finding, and the rule this package sets for every adapter.
     *
     * <p><strong>A status this adapter does not recognise is FAILED and not PENDING.</strong> That is the
     * conservative direction only if you read it as "this estate will not claim money is on its way when
     * it cannot tell": a wrong {@code PENDING} leaves a booking waiting for ever on a payment that may
     * already have failed, while a wrong {@code FAILED} is visible and correctable. The status is logged
     * so an unrecognised value is a line to read rather than a silent bucket.
     */
    @Override
    public PaymentOutcome status(String providerReference) {
        requireASecretKey();
        if (providerReference == null || providerReference.isBlank()) {
            // Not a provider fault and not a network call: asking Paystack about nothing would be a 404
            // this adapter would then have to interpret.
            return new PaymentOutcome(PaymentState.FAILED, providerReference, "no payment reference was given to ask paystack about");
        }
        JsonNode root = getJson(VERIFY + providerReference);
        String status = text(root.path("data").path("status"));
        String reference = text(root.path("data").path("reference"));
        // OUR reference is the fallback, not the answer: a verify response that names a different
        // reference than the one asked about is a mismatch this adapter must not paper over.
        if (reference != null && !reference.isBlank() && !reference.equals(providerReference)) {
            LOG.error("paystack answered about payment {} when asked about {}", reference, providerReference);
            return new PaymentOutcome(
                PaymentState.FAILED,
                providerReference,
                "the paystack payment provider answered about a different payment than the one asked about"
            );
        }
        return switch (status == null ? "" : status) {
            case "success" -> PaymentOutcome.captured(providerReference);
            case "failed", "abandoned", "reversed" -> new PaymentOutcome(
                PaymentState.FAILED,
                providerReference,
                "the paystack payment provider reports this payment did not complete"
            );
            case "pending", "ongoing", "queued" -> PaymentOutcome.pendingOnDevice(providerReference);
            default -> {
                LOG.warn("paystack reported an unrecognised status '{}' for payment {}", status, providerReference);
                yield new PaymentOutcome(
                    PaymentState.FAILED,
                    providerReference,
                    "the paystack payment provider reported a state this estate does not recognise"
                );
            }
        };
    }

    /**
     * Gives a customer's money back — {@code decisions.md} D86.
     *
     * <p>{@code POST /refund} with {@code {transaction, amount}}. {@code transaction} takes the
     * reference, and {@code amount} is in the currency's minor unit exactly as {@code authorize} sends
     * it, so no arithmetic happens here — pesewas in, pesewas out, which is what
     * {@code MoneyIsMinorUnitsUnitTest} exists to keep true across this estate.
     *
     * <p><strong>THIS IS THE ONE CALL HERE THAT MOVES MONEY, AND IT IS WRITTEN FROM THE PUBLISHED API
     * RATHER THAN FROM EVIDENCE.</strong> D50 integrated {@code authorize} and {@code readCallback} from
     * a working integration in this workspace and refused the rest as guesswork; D86 records that the
     * architect chose to write them anyway, and that {@code hc-crowdfund-app} calls only
     * {@code /transaction/initialize}, so there was nothing in this workspace to source these from.
     * Nothing here has spoken to Paystack.
     *
     * <p>So this refuses unless an operator has said, separately from enabling the provider at all, that
     * an unverified refund path may run: {@code healthconnect.payments.paystack.refunds-enabled}, absent
     * by default. That is not ceremony — turning a provider on is a routine deployment decision, and
     * turning on a money-returning call nobody has watched work is not the same decision, and must not
     * ride along with it.
     *
     * <p><strong>A refund is asynchronous at Paystack</strong>, so a 2xx here means accepted and not
     * settled: {@code data.status} comes back {@code pending} or {@code processing} for a refund that
     * will complete later. This returns {@link PaymentState#REFUNDED} only for a status that says the
     * money is back, and {@code PENDING} otherwise — never REFUNDED on the strength of a 2xx, because
     * {@code holdsMoney()} would then be wrong in the direction that loses track of a customer's money.
     */
    @Override
    public PaymentOutcome refund(String providerReference, long amountMinor, String currency, String reason) {
        if (!SETTLES_IN.equalsIgnoreCase(currency)) {
            // The same shape authorize uses, and for the same reason: name the currency this adapter can
            // take rather than the one it was handed, because the first is a fact about the adapter and
            // the second is a value off a booking.
            LOG.error("paystack was asked to refund payment {} in a currency this adapter cannot declare", providerReference);
            return PaymentOutcome.failed("the paystack payment adapter can only take %s".formatted(SETTLES_IN));
        }
        requireASecretKey();
        if (!refundsEnabled()) {
            // IllegalStateException, not the UnsupportedOperationException a seam throws: this call is
            // written and is being withheld by configuration, which is an operator's decision to reverse
            // and not an implementer's. Both reach the customer as a 502 and change no booking.
            throw new IllegalStateException(
                "the paystack refund call is written but has never been verified against a live account, so it is off " +
                "until an operator sets healthconnect.payments.paystack.refunds-enabled=true (decisions.md D86)"
            );
        }
        if (providerReference == null || providerReference.isBlank()) {
            return new PaymentOutcome(PaymentState.FAILED, providerReference, "no payment reference was given to refund");
        }
        if (amountMinor <= 0) {
            // Refusing here rather than sending it: Paystack treats an absent amount as a FULL refund, so
            // a zero or negative that reached the wire could return everything. That is the most
            // expensive possible reading of a bad argument, and it is one line to make impossible.
            return new PaymentOutcome(
                PaymentState.FAILED,
                providerReference,
                "a refund must name a positive amount — an absent amount is a full refund at this provider"
            );
        }
        LOG.info("asking paystack to refund {} minor units of payment {}", amountMinor, providerReference);
        JsonNode root = postJson(REFUND, Map.of("transaction", providerReference, "amount", amountMinor));
        String status = text(root.path("data").path("status"));
        return switch (status == null ? "" : status) {
            case "processed", "success", "reversed" -> PaymentOutcome.refunded(providerReference);
            // ACCEPTED IS NOT DONE. A refund Paystack has taken but not settled is still money the
            // customer does not have, and holdsMoney() must not be told otherwise.
            case "pending", "processing", "awaiting-approval" -> PaymentOutcome.pendingOnDevice(providerReference);
            default -> {
                LOG.warn("paystack reported refund status '{}' for payment {}", status, providerReference);
                yield new PaymentOutcome(
                    PaymentState.FAILED,
                    providerReference,
                    "the paystack payment provider did not accept this refund"
                );
            }
        };
    }

    /**
     * There is nothing to capture — {@code decisions.md} D86.
     *
     * <p>Overridden only to say so. {@code /transaction/initialize} is a charge and not a two-step hold,
     * so the money moves when the customer completes the checkout and arrives as {@code charge.success}:
     * no authorization is ever held separately for a later capture. The interface anticipates this
     * explicitly — <em>"a provider that only does immediate charges implements authorize as a capture and
     * returns CAPTURED"</em> — which is what {@code readCallback} does.
     *
     * <p>It still throws, and the exception type is unchanged, so {@code BookingPayments} answers the
     * customer exactly as before. What changes is the sentence an operator reads: <strong>"not
     * integrated" and "this provider has no such operation" are different facts</strong>, and only the
     * second tells you not to wait for an implementation.
     */
    @Override
    public PaymentOutcome capture(String providerReference, long amountMinor, String currency) {
        throw new UnsupportedOperationException(
            "paystack has no capture: /transaction/initialize is a charge rather than a two-step hold, so the money " +
            "moves at charge.success and there is never an authorization held for a later capture (decisions.md D86)"
        );
    }

    /**
     * There is nothing to void — {@code decisions.md} D86.
     *
     * <p>The same fact as {@code capture}, on the other side of it: before the customer completes the
     * checkout nothing is held, and afterwards the operation is a {@link #refund}. So a void has no
     * Paystack equivalent in this flow at any moment.
     *
     * <p><strong>This is the call D43's dead end needs and it is the one that cannot be written.</strong>
     * A {@code PENDING_PAYMENT} booking is not the customer's to cancel, because the provider may still
     * hold a live authorization — and the exit D43 wanted was to void it. Paystack's model means there is
     * no such exit: the customer either completes the checkout or abandons it, and an abandoned one is
     * answered by {@link #status} rather than cancelled from this side. That is a fact about the provider
     * and not a gap in this adapter, which is why it is recorded here rather than left as a refusal
     * indistinguishable from an unwritten call.
     */
    @Override
    public PaymentOutcome voidAuthorization(String providerReference, String reason) {
        throw new UnsupportedOperationException(
            "paystack has no void: nothing is held before the customer completes the checkout, and afterwards the " +
            "operation is a refund — so there is no authorization to cancel at any point (decisions.md D86)"
        );
    }

    /**
     * Refuses to use a key that is not a secret key.
     *
     * <p>{@code IllegalStateException} rather than the {@code UnsupportedOperationException} the
     * unimplemented calls throw, because the two say different things to whoever reads the log: this
     * adapter is written and is holding the wrong value, which is a job for an operator rather than
     * for an implementer. Both reach the customer as the same 502 and no booking.
     */
    /**
     * One authenticated GET, parsed — {@code decisions.md} D86.
     *
     * <p>Nothing is caught. A non-2xx, a read timeout or a body that is not JSON all throw, and
     * {@code BookingPayments} answers {@link PaymentState#FAILED} with the whole exception at ERROR —
     * the shape D44 built for a real adapter, and better than a composed {@code failed} because it
     * carries a stack trace to the one line an operator will read. {@code authorize} works the same way
     * and deliberately so.
     */
    private JsonNode getJson(String path) {
        String body = http.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + signingSecret()).retrieve().body(String.class);
        return parse(path, body);
    }

    /** One authenticated POST with a JSON body, parsed. Same no-catch rule as {@link #getJson}. */
    private JsonNode postJson(String path, Map<String, Object> body) {
        String answer = http
            .post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + signingSecret())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
        return parse(path, answer);
    }

    /**
     * Parses a response, refusing an empty or unparseable one rather than handing back a node that reads
     * as "no fields" — which every {@code text(...)} below would then answer null for, and every
     * {@code switch} would take its default branch on. A missing body and a body saying nothing useful
     * must not be the same thing to this class.
     */
    private JsonNode parse(String path, String body) {
        if (isBlank(body)) {
            throw new IllegalStateException("paystack answered %s with an empty body".formatted(path));
        }
        try {
            return json.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // The path, never the body: a provider's response is somebody else's prose and D44 keeps it
            // out of anything this estate composes.
            throw new IllegalStateException("paystack answered %s with something that is not JSON".formatted(path), e);
        }
    }

    private void requireASecretKey() {
        if (!canVerifyCallbacks()) {
            throw new IllegalStateException("the paystack adapter has no secret key configured, so it cannot authorize anything");
        }
        if (!isSecretKey(signingSecret())) {
            throw new IllegalStateException(
                "the paystack adapter's configured secret is not a secret key — it must start with 'sk_'. A 'pk_' value is " +
                    "the public key, which is meant to be published to browsers and can neither authorize a transaction nor " +
                    "verify a callback signature (decisions.md D50)"
            );
        }
    }

    /**
     * Says at startup when the configured key cannot possibly work.
     *
     * <p>Separate from {@code ProviderAwaitingIntegration.announceIntegration} rather than an
     * override of it, so both run: Spring calls every {@code @PostConstruct} in a hierarchy that is
     * not overridden, and these are two different pieces of news. The whole value of this one is that
     * it fires at boot rather than at the first customer who tries to pay, which is where a
     * {@code pk_} key otherwise surfaces.
     *
     * <p>It does not refuse to start. That is D35's shape for the privacy pepper, for its reason: an
     * outage of a service behind one wrong variable has an operator paste in a plausible value, which
     * is the committed-default failure arriving by another road.
     */
    @jakarta.annotation.PostConstruct
    void announceSecretKeyShape() {
        if (canVerifyCallbacks() && !isSecretKey(signingSecret())) {
            LOG.error(
                "payments: the paystack secret is not a secret key — it must start with 'sk_'. A 'pk_' value is the public " +
                    "key. Every authorization and every callback is refused until it is corrected (decisions.md D50)"
            );
        }
    }

    /**
     * Whether the presented hex digest is the one this estate's key produces over these bytes.
     *
     * <p>Constant-time in the contents, because a comparison that stops at the first differing
     * character leaks through its timing how many leading hex digits a caller had right, and an
     * attacker who can measure that can build a valid signature a digit at a time.
     * <strong>Case is folded before the comparison, not during it</strong> — hex is case-insensitive
     * and Paystack's own tooling has been seen to shout, but {@code equalsIgnoreCase} would put the
     * short-circuit straight back.
     */
    private boolean signatureMatches(String body, String presented) {
        byte[] expected = hmacSha512Hex(body).getBytes(StandardCharsets.UTF_8);
        byte[] given = presented.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, given);
    }

    private String hmacSha512Hex(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            // HmacSHA512 is required of every JVM and the key is arbitrary bytes, so this is a broken
            // runtime rather than a bad callback. It must not become a refusal: a 401 here would read
            // as "the provider's signature is wrong" for ever.
            throw new IllegalStateException("this JVM cannot compute HMAC-SHA512", impossible);
        }
    }

    private static boolean isSecretKey(String secret) {
        return secret != null && secret.startsWith(SECRET_KEY_PREFIX);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** A JSON string field, or null — never Jackson's {@code ""} for an absent node. */
    private static String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    /** Paystack's envelope. Only the fields this adapter reads; everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InitializeResponse(boolean status, String message, InitializeData data) {}

    /**
     * The transaction Paystack started.
     *
     * <p>{@code authorization_url} is where the customer goes and {@code reference} is the durable
     * handle — D45's item 2, which is the one value D41 exists to stop being dropped.
     * {@code access_code} is deliberately not modelled: it is for Paystack's own inline checkout,
     * which this estate does not use, and a field nothing reads is a field that acquires a meaning.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InitializeData(@JsonProperty("authorization_url") String authorizationUrl, String reference) {}
}
