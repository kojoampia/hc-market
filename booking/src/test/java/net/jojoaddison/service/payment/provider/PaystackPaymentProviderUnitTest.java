package net.jojoaddison.service.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.jojoaddison.service.payment.CustomerContacts;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentNextAction;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProviderProperties;
import net.jojoaddison.service.payment.PaymentState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The first adapter in this estate that speaks to its provider — {@code decisions.md} D50.
 *
 * <h2>Against a real socket, not a mocked client</h2>
 *
 * <p>The stub below is a JDK {@code HttpServer} on loopback with an ephemeral port. That is more
 * work than substituting the request factory and it buys the thing this package most needs: the
 * request that is asserted on is the one that went over a wire, headers, JSON encoding, content type
 * and all. D45's whole argument for refusing to write this adapter blind was that a wire format
 * checked only against the assumption that produced it is not checked at all — a test that mocks the
 * HTTP client away is a weaker version of the same problem. Nothing here reaches the network:
 * {@code InetAddress.getLoopbackAddress()} and a port the OS picks.
 *
 * <h2>The signature tests are the ones that matter</h2>
 *
 * <p>A wrong status mapping creates a booking for money that never arrived. A wrong signature check
 * accepts a stranger's POST as a payment provider, on the one endpoint in this estate that takes no
 * token. So there are four of them and only one is the happy path: a body <em>forged</em> under
 * another key, a body <em>tampered</em> with after signing, a signature absent, and a verified body
 * that is not JSON. All four must give {@link PaymentCallbackRefused}, which the endpoint renders as
 * one flat 401 — anything that distinguishes them from outside is an oracle (D43/D44).
 */
class PaystackPaymentProviderUnitTest {

    private static final String SECRET = "sk_test_notarealkey";

    /** A booking reference of the shape {@code CustomerBookingResource.create} mints. */
    private static final String BOOKING_REF = "b-9f2c1d4e";

    private StubPaystack paystack;

    @BeforeEach
    void startStub() throws IOException {
        paystack = new StubPaystack();
    }

    @AfterEach
    void stopStub() {
        paystack.stop();
    }

    // ---------------------------------------------------------------- authorize

    /**
     * The whole of D43's answer for Paystack: {@code PENDING}, the durable handle, and a redirect.
     *
     * <p>{@code authorize} cannot truthfully say more. The customer has not paid anything at the
     * moment this returns; they have been given somewhere to go. The booking that follows is written
     * in {@code PENDING_PAYMENT} and {@code booking.requested} is withheld until the callback below
     * confirms the money.
     */
    @Test
    @DisplayName("authorize answers PENDING with the provider's handle and a redirect to visit")
    void authorizeReturnsPendingWithARedirect() {
        paystack.willAnswer(
            200,
            """
            {"status":true,"message":"Authorization URL created","data":{
              "authorization_url":"https://checkout.paystack.com/0peioxfhpn",
              "access_code":"0peioxfhpn",
              "reference":"%s"}}""".formatted(BOOKING_REF)
        );

        PaymentOutcome outcome = provider(SECRET, contactsFor("ama.mensah@example.test")).authorize(intent(15_000L, "GHS"));

        assertThat(outcome.state()).isEqualTo(PaymentState.PENDING);
        assertThat(outcome.providerReference()).isEqualTo(BOOKING_REF);
        assertThat(outcome.nextAction().kind()).isEqualTo(PaymentNextAction.Kind.VISIT_URL);
        assertThat(outcome.nextAction().url()).isEqualTo("https://checkout.paystack.com/0peioxfhpn");
    }

    /**
     * The request Paystack is actually sent, asserted field by field.
     *
     * <p>Three of these have a cost if they drift and no other test would catch it. The
     * <strong>amount is minor units</strong> and this estate already stores pesewas, so a unit
     * conversion here would be a factor-of-100 error in a real charge. The <strong>reference is ours
     * and is sent</strong> rather than issued by Paystack — it is what the callback comes back
     * carrying, and D41's whole point is that the handle must be findable. And the key travels as
     * {@code Bearer}, in the {@code Authorization} header, which is the difference between an
     * authorized call and a 401 nobody sees until a customer tries to pay.
     */
    @Test
    @DisplayName("authorize posts /transaction/initialize with the amount in pesewas and our own reference")
    void authorizePostsTheDocumentedRequest() throws Exception {
        paystack.willAnswer(200, initialized("https://checkout.paystack.com/abc", BOOKING_REF));

        provider(SECRET, contactsFor("ama.mensah@example.test")).authorize(intent(28_000L, "GHS"));

        StubPaystack.Received sent = paystack.only();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/transaction/initialize");
        assertThat(sent.header("Authorization")).isEqualTo("Bearer " + SECRET);
        assertThat(sent.header("Content-Type")).startsWith("application/json");

        JsonNode body = new ObjectMapper().readTree(sent.body());
        assertThat(body.path("email").asText()).isEqualTo("ama.mensah@example.test");
        assertThat(body.path("amount").asLong()).isEqualTo(28_000L);
        assertThat(body.path("reference").asText()).isEqualTo(BOOKING_REF);
    }

    /**
     * D50's open decision, and the reason this adapter cannot take a payment on today's estate.
     *
     * <p>Paystack's initialize requires an email address; {@code PaymentIntent} carries a login and
     * no contact details, deliberately; and nothing in this repository implements
     * {@link CustomerContacts}, because standing up an endpoint that returns a person's email by
     * login is a disclosure decision the payment seam may not take alone. So the refusal is
     * <strong>before the round trip</strong>: no request is made, no reference is minted, and
     * {@code BookingPayments} turns the throw into a 502 and no booking.
     */
    @Test
    @DisplayName("no contact detail means no round trip and no booking")
    void authorizeRefusesWhenTheEstateCannotSayWhoIsPaying() {
        assertThatThrownBy(() -> provider(SECRET, CustomerContacts.unanswered()).authorize(intent(15_000L, "GHS")))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("D50");

        assertThat(paystack.received()).isEmpty();
    }

    /**
     * A blank address is the same absence wearing a value.
     *
     * <p>An implementation that answers {@code Optional.of("")} for a customer with no email on file
     * would otherwise send Paystack an empty {@code email}, and whatever Paystack does with that is
     * not something this platform should find out at a customer's expense.
     */
    @Test
    @DisplayName("a blank contact detail is treated as no contact detail")
    void authorizeRefusesABlankEmail() {
        assertThatThrownBy(() -> provider(SECRET, contactsFor("   ")).authorize(intent(15_000L, "GHS"))).isInstanceOf(
            UnsupportedOperationException.class
        );

        assertThat(paystack.received()).isEmpty();
    }

    /**
     * The misconfiguration that is invisible until somebody pays — the crowdfund lesson, imported.
     *
     * <p>Paystack issues {@code pk_}/{@code sk_} across {@code test_}/{@code live_} and lists them
     * side by side, so pasting the public key into the secret's slot is an easy slip. Nothing
     * downstream catches it: the service starts, offers Paystack, and 401s at
     * {@code /transaction/initialize} the first time a customer picks it. Refused here instead, with
     * no round trip, and announced at startup besides.
     */
    @Test
    @DisplayName("a public key in the secret's slot is refused before it can 401 at Paystack")
    void authorizeRefusesAPublicKey() {
        assertThatThrownBy(() -> provider("pk_test_notarealkey", contactsFor("ama@example.test")).authorize(intent(15_000L, "GHS")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sk_");

        assertThat(paystack.received()).isEmpty();
    }

    /** {@code sk_live_} is a correctly configured production key and must not be refused. */
    @Test
    @DisplayName("a live secret key is accepted — the two axes are independent")
    void aLiveSecretKeyIsUsable() {
        paystack.willAnswer(200, initialized("https://checkout.paystack.com/abc", BOOKING_REF));

        PaymentOutcome outcome = provider("sk_live_notarealkey", contactsFor("ama@example.test")).authorize(intent(15_000L, "GHS"));

        assertThat(outcome.state()).isEqualTo(PaymentState.PENDING);
    }

    /**
     * The currency is the Paystack account's property, and this adapter has no evidence it can be
     * set per transaction — so it refuses to guess.
     *
     * <p>Every price in this estate is GHS and the whole of the working evidence for this
     * integration sends no currency field at all. A booking denominated in something else would
     * therefore be charged as that many pesewas of whatever the merchant account settles in, which
     * is a silent mis-charge rather than a rejected call. {@code FAILED} and no round trip.
     *
     * <p>The stub is primed with a <strong>usable</strong> answer, and that is what makes the first
     * assertion mean anything. Against the default {@code {}} body the adapter answers {@code FAILED}
     * anyway — no {@code data}, nothing to complete — so a version of this test that let the round trip
     * happen would have passed on {@code isEqualTo(FAILED)} alone with the guard deleted, on the
     * strength of the wrong failure. Primed this way, deleting the guard makes the outcome
     * {@code PENDING} and every line here red.
     */
    @Test
    @DisplayName("a currency this adapter cannot vouch for is FAILED, not guessed at")
    void authorizeRefusesACurrencyItCannotVouchFor() {
        paystack.willAnswer(200, initialized("https://checkout.paystack.com/abc", BOOKING_REF));

        PaymentOutcome outcome = provider(SECRET, contactsFor("ama@example.test")).authorize(intent(15_000L, "NGN"));

        assertThat(outcome.state()).isEqualTo(PaymentState.FAILED);
        assertThat(outcome.reason()).contains("GHS");
        assertThat(paystack.received()).isEmpty();
    }

    /**
     * A 200 whose body is not usable is a failure, not a booking.
     *
     * <p>{@code status:false} with a message is how Paystack reports a business refusal on a
     * successful HTTP call, and a missing {@code data} is what a partial outage looks like. Either
     * way there is no handle and no URL, so there is nothing a webhook could ever match — the one
     * thing that must not happen is a bookable outcome.
     */
    @Test
    @DisplayName("a 200 with no authorization url and no reference is FAILED")
    void anUnusableAnswerIsFailed() {
        paystack.willAnswer(200, "{\"status\":false,\"message\":\"Invalid key\"}");

        PaymentOutcome outcome = provider(SECRET, contactsFor("ama@example.test")).authorize(intent(15_000L, "GHS"));

        assertThat(outcome.state()).isEqualTo(PaymentState.FAILED);
        assertThat(outcome.state().permitsBooking()).isFalse();
    }

    /**
     * The provider's own words do not travel into the outcome — {@code decisions.md} D44.
     *
     * <p>Paystack's {@code message} is the fourth road by which a third party's prose could reach a
     * response body, and it is composed away here as well as at the boundary. The reason names this
     * platform's view of what happened and nothing that came off the wire.
     */
    @Test
    @DisplayName("Paystack's own message is not copied into the outcome")
    void aProvidersMessageStaysInTheLog() {
        paystack.willAnswer(200, "{\"status\":false,\"message\":\"Declined — card ending 4242, Ama Mensah, 0244123456\"}");

        PaymentOutcome outcome = provider(SECRET, contactsFor("ama@example.test")).authorize(intent(15_000L, "GHS"));

        assertThat(outcome.reason()).doesNotContain("4242").doesNotContain("Ama Mensah").doesNotContain("0244123456");
    }

    /**
     * D43's scheme check, reached from the direction it was written for.
     *
     * <p>The URL comes from a third party and ends in a browser's address bar, so a {@code
     * javascript:} one would make the outcome a redirect gadget. {@code PaymentNextAction} refuses to
     * be constructed with it; what this pins is that the adapter does not catch that and turn it into
     * something bookable instead.
     *
     * <p>The type is named rather than left at {@code RuntimeException}. Every exception this adapter
     * can throw is one — the no-email {@code UnsupportedOperationException} and the not-a-secret-key
     * {@code IllegalStateException} among them — so the loose form would go on passing if the scheme
     * check were deleted and something else threw for an unrelated reason.
     */
    @Test
    @DisplayName("a relayed javascript: url never becomes a bookable outcome")
    void aNonWebRedirectIsRefused() {
        paystack.willAnswer(200, initialized("javascript:alert(document.cookie)", BOOKING_REF));

        assertThatThrownBy(() -> provider(SECRET, contactsFor("ama@example.test")).authorize(intent(15_000L, "GHS")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http");
    }

    /**
     * An unreachable provider throws, which {@code BookingPayments} turns into {@code FAILED} and a
     * 502 — the shape D44 built for exactly this.
     *
     * <p>{@code RestClientResponseException} rather than {@code RuntimeException}, for the reason on
     * the test above: the loose form is satisfied by any failure at all, including the ones that happen
     * before a request is made, so it would still pass if this adapter stopped calling Paystack.
     */
    @Test
    @DisplayName("a provider that answers with an error throws rather than answering something bookable")
    void aProviderErrorIsNotABooking() {
        paystack.willAnswer(500, "{\"status\":false}");

        assertThatThrownBy(() -> provider(SECRET, contactsFor("ama@example.test")).authorize(intent(15_000L, "GHS"))).isInstanceOf(
            RestClientResponseException.class
        );
    }

    // ---------------------------------------------------------------- readCallback

    /**
     * The happy path, and the one place a Paystack callback is allowed to move a booking.
     *
     * <p>{@code CAPTURED} rather than {@code AUTHORIZED}: Paystack's initialize is a charge rather
     * than a two-step hold, and {@code charge.success} means the money has moved. The state has to
     * say so, because {@code holdsMoney()} is what decides whether a booking that later fails to
     * exist needs a refund or a void.
     */
    @Test
    @DisplayName("a correctly signed charge.success is CAPTURED, carrying the handle")
    void aSignedSuccessIsCaptured() {
        String body = """
            {"event":"charge.success","data":{"reference":"%s","status":"success"}}""".formatted(BOOKING_REF);

        PaymentOutcome outcome = provider(SECRET, CustomerContacts.unanswered()).readCallback(signed(body, SECRET));

        assertThat(outcome.state()).isEqualTo(PaymentState.CAPTURED);
        assertThat(outcome.providerReference()).isEqualTo(BOOKING_REF);
    }

    /** Hex is case-insensitive and the comparison folds case before comparing, never during. */
    @Test
    @DisplayName("an upper-case hex signature verifies")
    void caseIsFoldedNotBranchedOn() {
        String body = """
            {"event":"charge.success","data":{"reference":"%s"}}""".formatted(BOOKING_REF);
        PaymentCallback shouted = new PaymentCallback(
            "paystack",
            Map.of("x-paystack-signature", hmacSha512Hex(SECRET, body).toUpperCase(Locale.ROOT)),
            body
        );

        assertThat(provider(SECRET, CustomerContacts.unanswered()).readCallback(shouted).state()).isEqualTo(PaymentState.CAPTURED);
    }

    /**
     * <strong>Forged.</strong> A body somebody else signed with a key that is not ours — the whole
     * of what an attacker can do, since the payload is guessable and the endpoint takes no token.
     */
    @Test
    @DisplayName("a body signed under another key is refused")
    void aForgedCallbackIsRefused() {
        String body = """
            {"event":"charge.success","data":{"reference":"%s"}}""".formatted(BOOKING_REF);

        assertThatThrownBy(() ->
            provider(SECRET, CustomerContacts.unanswered()).readCallback(signed(body, "sk_test_someoneelse"))
        ).isInstanceOf(PaymentCallbackRefused.class);
    }

    /**
     * <strong>Tampered.</strong> A signature Paystack really did produce, presented over a body it
     * did not — a replayed callback with the reference swapped for somebody else's booking. This is
     * the one a check that verified the signature against a re-serialised or partially read body
     * would let through.
     */
    @Test
    @DisplayName("a genuine signature over a different body is refused")
    void aTamperedCallbackIsRefused() {
        String signedBody = """
            {"event":"charge.success","data":{"reference":"%s"}}""".formatted(BOOKING_REF);
        String swapped = """
            {"event":"charge.success","data":{"reference":"b-somebodyelse"}}""";
        PaymentCallback tampered = new PaymentCallback(
            "paystack",
            Map.of("x-paystack-signature", hmacSha512Hex(SECRET, signedBody)),
            swapped
        );

        assertThatThrownBy(() -> provider(SECRET, CustomerContacts.unanswered()).readCallback(tampered)).isInstanceOf(
            PaymentCallbackRefused.class
        );
    }

    /**
     * <strong>Malformed.</strong> A signature that is not the right length, and one that is not hex at
     * all — the two shapes a prober reaches for after "none" and "wrong", and the two this file did not
     * have.
     *
     * <p>Both are refused correctly today and would be refused by a great many wrong implementations
     * too, since {@code MessageDigest.isEqual} over the two byte arrays cannot match either. That is
     * exactly the "refused by construction" D45 says not to settle for: the property is not enforced
     * anywhere, so it survives only as long as nobody adds a length check or a
     * {@code HexFormat.parseHex} in front of the comparison. Either would reintroduce something this
     * pair notices — a short-circuit that answers before the digest is computed, or a
     * {@code NumberFormatException} escaping as a 500 where a 401 belongs, which is the oracle the
     * unparseable-body test guards from the other side.
     */
    @Test
    @DisplayName("a signature of the wrong length and one that is not hex are both refused, and refused the same way")
    void aMalformedSignatureIsRefused() {
        String body = """
            {"event":"charge.success","data":{"reference":"%s"}}""".formatted(BOOKING_REF);
        PaystackPaymentProvider adapter = provider(SECRET, CustomerContacts.unanswered());

        // Right alphabet, far too short: a SHA-512 digest is 128 hex characters.
        assertThatThrownBy(() -> adapter.readCallback(withSignature("deadbeef", body))).isInstanceOf(PaymentCallbackRefused.class);
        // Right length, not hex at all — the input a parse-then-compare implementation would throw on.
        assertThatThrownBy(() -> adapter.readCallback(withSignature("zz".repeat(64), body))).isInstanceOf(PaymentCallbackRefused.class);
    }

    @Test
    @DisplayName("a callback with no signature header is refused")
    void anUnsignedCallbackIsRefused() {
        String body = """
            {"event":"charge.success","data":{"reference":"%s"}}""".formatted(BOOKING_REF);

        assertThatThrownBy(() ->
            provider(SECRET, CustomerContacts.unanswered()).readCallback(new PaymentCallback("paystack", Map.of(), body))
        ).isInstanceOf(PaymentCallbackRefused.class);
    }

    /**
     * A body that verifies and then will not parse gets the same refusal, not a 500.
     *
     * <p>Reachable: whoever holds the signing secret can send anything. Letting a
     * {@code JsonProcessingException} escape would publish the difference between "your signature was
     * wrong" and "your signature was right and your JSON was not", which is the oracle D44's review
     * removed from the endpoint and which an adapter can put straight back.
     */
    @Test
    @DisplayName("a verified body that is not JSON is refused, not exploded over")
    void aVerifiedButUnparseableBodyIsRefused() {
        String notJson = "event=charge.success&reference=" + BOOKING_REF;

        assertThatThrownBy(() -> provider(SECRET, CustomerContacts.unanswered()).readCallback(signed(notJson, SECRET))).isInstanceOf(
            PaymentCallbackRefused.class
        );
    }

    /**
     * A verified callback naming no payment is refused rather than returned.
     *
     * <p>{@code PaymentProvider.readCallback}'s own rule: an outcome without a reference cannot be
     * applied to anything, and {@code PaymentConfirmations} would look it up as null and answer
     * {@code UNKNOWN_PAYMENT} — a 404 that asks the provider to retry something that can never work.
     */
    @Test
    @DisplayName("a verified callback that names no payment is refused")
    void aCallbackWithNoReferenceIsRefused() {
        assertThatThrownBy(() ->
            provider(SECRET, CustomerContacts.unanswered()).readCallback(signed("{\"event\":\"charge.success\"}", SECRET))
        ).isInstanceOf(PaymentCallbackRefused.class);
    }

    /**
     * Anything that is not a successful charge is {@code FAILED} — <strong>and it keeps the
     * handle</strong>.
     *
     * <p>The keeping is the substance. {@code PaymentOutcome.failed(reason)} discards the reference,
     * and {@code PaymentConfirmations} finds the {@code payment_attempt} row <em>by</em> the
     * reference — so a failure outcome built that way names no payment, answers
     * {@code UNKNOWN_PAYMENT}, and leaves the customer's booking sitting in {@code PENDING_PAYMENT}
     * for ever with Paystack retrying a callback that can never land. D41's dropped handle, arriving
     * on the failure path.
     */
    @Test
    @DisplayName("a failed charge is FAILED and still names the payment it is about")
    void aFailedChargeKeepsTheHandle() {
        String body = """
            {"event":"charge.failed","data":{"reference":"%s"}}""".formatted(BOOKING_REF);

        PaymentOutcome outcome = provider(SECRET, CustomerContacts.unanswered()).readCallback(signed(body, SECRET));

        assertThat(outcome.state()).isEqualTo(PaymentState.FAILED);
        assertThat(outcome.state().permitsBooking()).isFalse();
        assertThat(outcome.providerReference()).isEqualTo(BOOKING_REF);
    }

    /**
     * An event nobody has mapped is {@code FAILED}, never something that permits a booking.
     *
     * <p>{@code service.payment.provider}'s package documentation states this as the rule for every
     * adapter, on the grounds that mapping an unrecognised status onto "fine" is the quietest failure
     * available in this seam. The residual it costs is named in D50: an unrelated Paystack event
     * quoting a reference this platform issued cancels a booking that is still waiting. That is the
     * recoverable direction — the customer books again — where the other one is money taken for a
     * booking nobody made.
     */
    @Test
    @DisplayName("an event this adapter does not know is FAILED, not a booking")
    void anUnmappedEventIsFailed() {
        String body = """
            {"event":"charge.pending","data":{"reference":"%s"}}""".formatted(BOOKING_REF);

        assertThat(provider(SECRET, CustomerContacts.unanswered()).readCallback(signed(body, SECRET)).state()).isEqualTo(
            PaymentState.FAILED
        );
    }

    /**
     * Absent and wrong-shaped secrets refuse identically from outside — D45's rule, which an adapter
     * with a real signature check is the first thing able to break.
     */
    @Test
    @DisplayName("no secret and a public key both refuse a callback, with the same type")
    void anUnusableSecretRefusesCallbacks() {
        String body = """
            {"event":"charge.success","data":{"reference":"%s"}}""".formatted(BOOKING_REF);
        PaymentCallback anything = new PaymentCallback("paystack", Map.of("x-paystack-signature", "00".repeat(64)), body);

        assertThatThrownBy(() -> provider(null, CustomerContacts.unanswered()).readCallback(anything))
            .isInstanceOf(PaymentCallbackRefused.class)
            .hasMessageContaining("no signing secret");
        assertThatThrownBy(() -> provider("pk_test_notarealkey", CustomerContacts.unanswered()).readCallback(anything))
            .isInstanceOf(PaymentCallbackRefused.class)
            .hasMessageContaining("sk_");
    }

    // ---------------------------------------------------------------- the startup account

    /**
     * The estate's startup log has to stop saying this adapter refuses everything.
     *
     * <p>D45's warning was true of all three adapters and became false for this one the moment it was
     * built. The account is composed from {@code integratedCalls()} now, so it cannot say the
     * opposite of the truth without this going red.
     */
    @Test
    @DisplayName("paystack declares the two calls it implements; the other two adapters declare none")
    void theStartupAccountIsTrue() {
        assertThat(provider(SECRET, CustomerContacts.unanswered()).integratedCalls()).containsExactlyInAnyOrder(
            "authorize",
            "readCallback"
        );
        assertThat(new HubtelPaymentProvider(settings(SECRET, null)).integratedCalls()).isEmpty();
        assertThat(new MtnMomoPaymentProvider(settings(SECRET, null)).integratedCalls()).isEmpty();
    }

    /**
     * Both startup announcements are annotated so that the container actually makes them.
     *
     * <p>The same class of defect `PaymentProviders.theGuardRunsAtStartup` pins, and D45's review
     * found for real on the gateway's webhook permit: a method the tests call themselves passes every
     * test whether or not anything running ever calls it. Neither of these is a guard — the refusals
     * live in {@code authorize} and {@code readCallback} and are tested above — but the whole value of
     * the {@code pk_} check is that it fires at boot rather than at the first customer who tries to
     * pay, and without the annotation it fires nowhere.
     *
     * <p>Two methods rather than one override, deliberately: Spring runs every {@code @PostConstruct}
     * in a hierarchy that is not overridden, so the inherited account and this adapter's own key check
     * are two pieces of news that both get made. Overriding {@code announceIntegration} would have
     * silently replaced the first with the second.
     */
    @Test
    @DisplayName("both startup announcements are annotated, or they are made to nobody")
    void theStartupAnnouncementsRunAtStartup() throws NoSuchMethodException {
        assertThat(PaystackPaymentProvider.class.getDeclaredMethod("announceSecretKeyShape").getAnnotation(PostConstruct.class))
            .as("announceSecretKeyShape only fires at boot if @PostConstruct is on it")
            .isNotNull();
        assertThat(ProviderAwaitingIntegration.class.getDeclaredMethod("announceIntegration").getAnnotation(PostConstruct.class))
            .as("announceIntegration only fires at boot if @PostConstruct is on it")
            .isNotNull();
        assertThat(PaystackPaymentProvider.class.getDeclaredMethods())
            .as("overriding announceIntegration would replace the inherited announcement rather than adding to it")
            .noneMatch(method -> "announceIntegration".equals(method.getName()));
    }

    /**
     * Everything that is not the booking path still refuses — {@code decisions.md} D50.
     *
     * <p>The working evidence for this integration covers {@code initialize} and the webhook, and
     * nothing else. {@code capture}, {@code refund}, {@code voidAuthorization} and {@code status} are
     * therefore left exactly as D45 left them rather than written from what a Paystack API plausibly
     * looks like. The consequence is stated in D50 and is not silent: a {@code PENDING_PAYMENT}
     * booking whose creation then fails cannot have its payment cancelled, so
     * {@code BookingPayments.release} flags the row for a person.
     */
    @Test
    @DisplayName("capture, refund, void and status are still seams and still refuse")
    void everythingOutsideTheBookingPathStillRefuses() {
        PaystackPaymentProvider adapter = provider(SECRET, contactsFor("ama@example.test"));

        assertThatThrownBy(() -> adapter.capture("ref", 1L, "GHS")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.refund("ref", 1L, "GHS", "why")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.voidAuthorization("ref", "why")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.status("ref")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(paystack.received()).isEmpty();
    }

    // ---------------------------------------------------------------- fixtures

    private PaystackPaymentProvider provider(String secret, CustomerContacts contacts) {
        return new PaystackPaymentProvider(settings(secret, paystack.baseUrl()), RestClient.builder(), new ObjectMapper(), contacts);
    }

    private static PaymentProviderProperties.Provider settings(String secret, String baseUrl) {
        PaymentProviderProperties.Provider settings = new PaymentProviderProperties.Provider();
        settings.setEnabled(true);
        settings.setSecret(secret);
        settings.setBaseUrl(baseUrl);
        settings.setTimeoutMs(2000);
        return settings;
    }

    private static CustomerContacts contactsFor(String email) {
        return login -> Optional.of(email);
    }

    private static PaymentIntent intent(long amountMinor, String currency) {
        return new PaymentIntent(BOOKING_REF, "ama.mensah", amountMinor, currency, "Follow-up consultation");
    }

    private static String initialized(String url, String reference) {
        return """
        {"status":true,"message":"Authorization URL created","data":{
          "authorization_url":"%s","access_code":"0peioxfhpn","reference":"%s"}}""".formatted(url, reference);
    }

    /** A callback carrying the signature Paystack would compute over exactly these bytes. */
    private static PaymentCallback signed(String body, String withSecret) {
        return withSignature(hmacSha512Hex(withSecret, body), body);
    }

    /** A callback carrying whatever is handed to it, for the signatures nobody could have computed. */
    private static PaymentCallback withSignature(String presented, String body) {
        return new PaymentCallback("paystack", Map.of("x-paystack-signature", presented), body);
    }

    private static String hmacSha512Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Paystack, as far as a socket on loopback can stand in for it.
     *
     * <p>It records every request rather than only the last, because two of the tests above assert
     * that <strong>no</strong> request was made — a refusal before the round trip is the whole
     * behaviour there, and a stub that only remembers the most recent call cannot tell "nothing was
     * sent" from "something was sent and then something else was".
     */
    private static final class StubPaystack {

        private final HttpServer server;
        private final List<Received> received = new ArrayList<>();

        private int status = 200;
        private String body = "{}";

        StubPaystack() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        void willAnswer(int status, String body) {
            this.status = status;
            this.body = body;
        }

        String baseUrl() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        List<Received> received() {
            return List.copyOf(received);
        }

        Received only() {
            assertThat(received).hasSize(1);
            return received.get(0);
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                byte[] sent = exchange.getRequestBody().readAllBytes();
                Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.isEmpty() ? null : values.get(0)));
                received.add(
                    new Received(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        headers,
                        new String(sent, StandardCharsets.UTF_8)
                    )
                );
                byte[] answer = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, answer.length);
                exchange.getResponseBody().write(answer);
            }
        }

        record Received(String method, String path, Map<String, String> headers, String body) {
            String header(String name) {
                return headers.get(name);
            }
        }
    }
}
