package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.domain.PaymentAttempt;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.repository.OutboxEventRepository;
import net.jojoaddison.repository.PaymentAttemptRepository;
import net.jojoaddison.service.CatalogClient;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /webhooks/payments/{provider}} — the asynchronous half of the payment seam, D43.
 *
 * <p>Every provider D37 chose confirms this way, so this endpoint is the only thing that will ever
 * turn a real customer's money into a booking a professional can see. It is also unauthenticated in
 * the ordinary sense — no token, because a provider cannot hold one — which makes the refusal path as
 * much a part of the contract as the success path, and is why it is asserted first.
 *
 * <p>The provider is substituted, exactly as {@code PaymentSeamIT} substitutes it and for the same
 * reason: the real bean refuses every callback by design, so a seam whose confirmation path had never
 * run would be a seam nobody knows the shape of on the day a provider is wired.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = PaymentWebhookIT.CUSTOMER)
class PaymentWebhookIT {

    static final String CUSTOMER = "kojo.customer";

    private static final String BOOKINGS = "/api/bookings";
    private static final String WEBHOOK = "/webhooks/payments/stub";
    private static final String REF = "p1";
    private static final String OWNER = "akosua.mensah";
    private static final String BODY = "{\"event\":\"charge.success\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingQueryRepository bookings;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Autowired
    private OutboxEventRepository outbox;

    @MockitoBean
    private CatalogClient catalog;

    @MockitoBean
    private PaymentProvider payments;

    @BeforeEach
    void aProviderThatAnswersPending() {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), "Africa/Accra")
        );
        when(catalog.loginOf(REF)).thenReturn(OWNER);
        when(payments.name()).thenReturn("stub");
    }

    // -------------------------------------------------------------------- who may call this --

    /**
     * The first thing to assert about a public endpoint: what a stranger gets.
     *
     * <p>401 and nothing else. Not 400 naming the missing header, not 403 confirming the provider
     * exists, not a message saying which part of the signature was wrong — an endpoint that explains
     * its refusals is an oracle for constructing a request it will not refuse. The refusal comes from
     * the provider adapter, which is where the signature scheme lives; today's real adapter refuses
     * everything, so today every caller sees this.
     */
    @Test
    @Transactional
    @DisplayName("a callback the provider will not vouch for is 401, with nothing said about why")
    void anUnverifiedCallbackIsRefused() throws Exception {
        when(payments.readCallback(any())).thenThrow(new PaymentCallbackRefused("signature does not match"));

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("signature"));
    }

    /**
     * The refusal is the <em>endpoint's</em> guarantee, not the adapter's good manners.
     *
     * <p>{@code PaymentCallbackRefused} is what an adapter throws when it has read a request and
     * decided against it. It is not what happens when the request is malformed: a body with a field
     * missing gets a {@code NullPointerException}, a body that is not JSON gets a
     * {@code JsonProcessingException}, and a next-action URL relayed as {@code javascript:} gets an
     * {@code IllegalArgumentException} out of {@code PaymentNextAction} — two of those three are
     * reachable from constructors written in this very package. Catching only the refusal meant a
     * forged malformed body answered 500 and a forged well-formed one answered 401, which is a
     * two-valued oracle telling a prober which of their attempts is structurally closer to a callback
     * this service would accept. Every failure to establish the provider is one answer.
     */
    @Test
    @Transactional
    @DisplayName("a body that makes the adapter throw is refused with the same flat 401")
    void aMalformedCallbackIsRefusedTheSameWay() throws Exception {
        when(payments.readCallback(any())).thenThrow(new NullPointerException("Cannot invoke \"String.length()\" because \"data\" is null"));

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("NullPointer"));
    }

    /**
     * A callback addressed to a provider this service is not configured for never reaches the
     * verifier, and gets the same 401.
     *
     * <p>It matters from WP-13 onwards, when there are three of them: a callback signed by one
     * provider must not be applied as another's.
     */
    @Test
    @Transactional
    @DisplayName("a callback for a provider this service does not run is refused without being read")
    void anotherProvidersCallbackIsRefused() throws Exception {
        mockMvc
            .perform(post("/webhooks/payments/someone-else").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).readCallback(any());
    }

    /**
     * Nothing under this prefix may be read. A GET that answered would be an unauthenticated read of
     * what the platform knows about somebody's payment.
     *
     * <p>The assertion is that <strong>security</strong> refuses it — 401 or 403 — rather than merely
     * that nothing answers 200. Without the {@code denyAll} in
     * {@code PaymentWebhookSecurityConfiguration} this path would still not answer a GET, because no
     * handler is mapped to one: it would answer 405, which is a different thing being right for a
     * different reason and would let the rule be deleted without a test noticing.
     */
    @Test
    @Transactional
    @DisplayName("the webhook path cannot be read, only posted to")
    void nothingHereIsReadable() throws Exception {
        mockMvc.perform(get(WEBHOOK)).andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    // ------------------------------------------------------------------------ what it does --

    /**
     * The package's reason for existing: a payment confirmed after the fact turns a booking nobody
     * had been told about into an ordinary request.
     *
     * <p>Both halves are asserted. The status moves to {@code REQUESTED}, so the professional's inbox
     * — which asks for that status and no other — starts returning it. And {@code booking.requested}
     * appears in the outbox <strong>now</strong>, exactly once, which is what makes messaging open the
     * conversation and raise the notification at the moment there is something true to say.
     */
    @Test
    @Transactional
    @DisplayName("a confirmed payment turns a pending booking into a request, and announces it then")
    void aConfirmationMakesTheBookingReal() throws Exception {
        String reference = bookPending("prov-w1");
        assertThat(eventsAbout(reference)).isEmpty();

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("applied"));

        assertThat(statusOf(reference)).isEqualTo("REQUESTED");
        assertThat(eventsAbout(reference)).extracting(OutboxEvent::getType).containsExactly("healthconnect.booking.requested");
        assertThat(attempts.findByProviderReferenceOrderByRecordedAtDesc("prov-w1").get(0).getState()).isEqualTo("CAPTURED");
    }

    /**
     * The professional's side of the same decision, asserted from the endpoint they actually use.
     *
     * <p>D43 permits a booking to exist while its payment is pending on the condition that it is not a
     * request anybody can act on. This is that condition: the inbox is empty while the payment is
     * pending and holds the booking once it is confirmed. The filter and the withheld event are two
     * mechanisms for one promise, and this is the one a professional would notice.
     */
    @Test
    @Transactional
    @DisplayName("the professional's inbox ignores a pending booking and shows it once it is paid")
    void theProfessionalSeesNothingUntilTheMoneyIsConfirmed() throws Exception {
        String reference = bookPending("prov-w2");

        mockMvc
            .perform(get("/api/pro/requests").with(user(OWNER)))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(reference));

        mockMvc.perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());

        mockMvc
            .perform(get("/api/pro/requests").with(user(OWNER)))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains(reference));
    }

    /**
     * The duplicate, which is the ordinary case rather than the exotic one: every one of these
     * providers retries until it gets a 2xx and some send a second copy anyway.
     *
     * <p>200, and nothing happens twice — one event, one status. A 409 would be more precise about
     * what occurred and would have the provider retry until it gave up and filed the payment as
     * undelivered, which is the expensive way to be right.
     */
    @Test
    @Transactional
    @DisplayName("the same callback twice is 200 and changes nothing the second time")
    void aDuplicateCallbackIsIdempotent() throws Exception {
        String reference = bookPending("prov-w3");

        mockMvc.perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("already applied"));

        assertThat(statusOf(reference)).isEqualTo("REQUESTED");
        assertThat(eventsAbout(reference)).hasSize(1);
    }

    /**
     * The customer declined the prompt, or the payment failed.
     *
     * <p>The booking is cancelled by the platform — neither party chose this — and carries <em>no</em>
     * late-cancellation flag: a 50% fee for a booking nobody paid for and no professional ever saw
     * would be indefensible, and {@code Cancel} would have computed one for any appointment inside the
     * free window. Nothing is published, because the first thing anybody downstream would hear about
     * this booking would be its cancellation.
     */
    @Test
    @DisplayName("a payment that fails cancels the booking quietly, with no fee")
    @Transactional
    void aFailedPaymentAbandonsTheBooking() throws Exception {
        String reference = bookPendingSoon("prov-w4");
        // The canonical constructor rather than PaymentOutcome.declined, because a refusal that comes
        // back through a webhook has to carry the handle: the platform finds the payment by nothing
        // else, and a decline with a null reference describes a payment it cannot match to a booking.
        when(payments.readCallback(any())).thenReturn(
            new PaymentOutcome(net.jojoaddison.service.payment.PaymentState.DECLINED, "prov-w4", "customer cancelled the prompt")
        );

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("applied"));

        Booking cancelled = bookings.findByReference(reference).orElseThrow();
        assertThat(cancelled.getStatus().name()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelledBy().name()).isEqualTo("PLATFORM");
        assertThat(cancelled.getLateCancellation()).isFalse();
        assertThat(eventsAbout(reference)).isEmpty();
    }

    /**
     * The mistake the next adapter's author will make, caught at the endpoint instead of stranding a
     * booking — {@code decisions.md} D49, as reviewed.
     *
     * <p>{@code PaymentOutcome.failed(reason)} is public, it is the obvious thing to write for a
     * declined payment, and it <strong>nulls the reference</strong>. D49 found and fixed that in the
     * Paystack adapter; nothing stopped it recurring. Whoever writes Hubtel or MoMo writes the same
     * line, and before this guard the consequence was silent and permanent: the confirmation named no
     * payment, matched no {@code payment_attempt} row, answered the provider 404, and left every failed
     * payment's booking in {@code PENDING_PAYMENT} for ever — under a WARN blaming the provider for
     * naming a reference this service never issued, when the callback had named one and the adapter
     * had dropped it.
     *
     * <p>401, because the invariant belongs to {@link net.jojoaddison.service.payment.PaymentProvider}
     * {@code #readCallback} and its own javadoc already said an implementation that cannot extract a
     * reference must refuse rather than return. An adapter that returns one anyway has not established
     * the provider, so it gets the endpoint's one answer — and its author sees it on the first callback
     * rather than in a report about bookings that never left {@code PENDING_PAYMENT}.
     */
    @Test
    @Transactional
    @DisplayName("an adapter that returns an outcome naming no payment is refused, not applied to nothing")
    void anOutcomeThatNamesNoPaymentIsRefused() throws Exception {
        String reference = bookPending("prov-w12");
        when(payments.readCallback(any())).thenReturn(PaymentOutcome.failed("the customer's card was declined"));

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("declined"));

        // The booking is untouched, which is the half that was permanent: nothing else ever revisits
        // PENDING_PAYMENT, so a booking left there by a dropped handle is left there for good.
        assertThat(statusOf(reference)).isEqualTo("PENDING_PAYMENT");
    }

    /**
     * A handle this service never issued. 404, so a provider replaying against a rebuilt database
     * eventually stops, and a log line for the operator.
     */
    @Test
    @Transactional
    @DisplayName("a callback naming a payment this service never made is 404")
    void anUnknownHandleIsNotFound() throws Exception {
        when(payments.readCallback(any())).thenReturn(PaymentOutcome.captured("prov-never-issued"));

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.result").value("not found"));
    }

    /**
     * The race the sequence makes possible: authorizing happens before the booking row is written
     * (D31/D41), so a provider that confirms instantly can arrive first.
     *
     * <p>404 rather than a silent success, because the provider's retry is what resolves it — and the
     * attempt row still records what the provider said, so the fact is not lost while the platform
     * waits to be able to act on it.
     */
    @Test
    @Transactional
    @DisplayName("a callback that overtakes its own booking asks the provider to try again")
    void aCallbackBeforeItsBookingAsksForARetry() throws Exception {
        attempts.save(pendingAttemptFor("b-not-yet", "prov-w5"));
        when(payments.readCallback(any())).thenReturn(PaymentOutcome.captured("prov-w5"));

        mockMvc.perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());

        assertThat(attempts.findByProviderReferenceOrderByRecordedAtDesc("prov-w5").get(0).getState()).isEqualTo("CAPTURED");
    }

    /**
     * Money that arrived after this platform gave up on it, which is not a race and no retry fixes.
     *
     * <p>The provider cancelled the pending payment when the booking could not be created, and then
     * confirmed it anyway — the customer approved the prompt in the gap. That is money committed for a
     * booking that does not exist, so the row is flagged for a person: it is the only column an
     * operator queries, and nothing retries into a provider on its own.
     */
    @Test
    @Transactional
    @DisplayName("a payment confirmed after it was released is flagged for a person")
    void moneyAfterAReleaseNeedsAPerson() throws Exception {
        PaymentAttempt released = pendingAttemptFor("b-gone", "prov-w6");
        released.setState("VOIDED");
        released.setResolvedAt(Instant.now());
        attempts.save(released);
        when(payments.readCallback(any())).thenReturn(PaymentOutcome.captured("prov-w6"));

        mockMvc.perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());

        PaymentAttempt after = attempts.findByProviderReferenceOrderByRecordedAtDesc("prov-w6").get(0);
        assertThat(after.isNeedsAttention()).isTrue();
        assertThat(after.getAttentionNote()).contains("after it was released");
        assertThat(after.getAttentionNote()).doesNotContain(CUSTOMER);
    }

    /**
     * One handle, two attempt rows — the case {@code PaymentAttemptRepository} returns a {@code List}
     * for, and which taking the newest row got wrong.
     *
     * <p>{@code PaymentRecorder.record}'s own javadoc says why the list exists: "two attempts against
     * one booking may legitimately carry the same" provider reference. A provider that reuses a handle
     * across a retry therefore leaves the platform with several rows and one callback, and the newest
     * of them is a guess rather than a match. Here the newest belongs to a booking that was never
     * written and the older one to a customer waiting on their phone: picking by recency confirms
     * nothing, answers the provider 404, and leaves a paid booking sitting in {@code PENDING_PAYMENT}
     * for ever — the provider's retries all landing on the same wrong row.
     *
     * <p>Matching on the booking that is actually waiting is not a heuristic: at most one booking per
     * handle can be in {@code PENDING_PAYMENT}, because the transition out of it is one-way. Recency
     * remains the fallback for the ordinary case, where there is one row and nothing to choose between.
     */
    @Test
    @Transactional
    @DisplayName("a reused handle is matched to the booking that is waiting, not to the newest row")
    void areusedHandleFindsTheBookingThatIsWaiting() throws Exception {
        String waiting = bookPending("prov-w9");
        // A later attempt carrying the same handle, for a booking that never made it to the database.
        // Recorded after the first, so it is the one findByProviderReferenceOrderByRecordedAtDesc
        // hands back first.
        PaymentAttempt newer = pendingAttemptFor("b-never-written", "prov-w9");
        newer.setRecordedAt(Instant.now().plusSeconds(60));
        attempts.save(newer);

        mockMvc
            .perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("applied"));

        assertThat(statusOf(waiting)).isEqualTo("REQUESTED");
    }

    /**
     * The other shape a released payment comes in, and the one the first version of this could not
     * see: a release that <em>failed</em>.
     *
     * <p>{@code BookingPayments.flag} deliberately leaves {@code state} alone — D41, so the operator
     * can still see what the provider last reported — and sets {@code needs_attention} instead. So a
     * row this platform tried and failed to void carries {@code PENDING}, which is exactly what a row
     * that has never been touched carries. Reading the state alone therefore filed the worst case in
     * the estate — money committed, booking gone, and the cancellation we attempted did not work — as a
     * benign race at INFO, and the ERROR a person is meant to act on never fired.
     */
    @Test
    @Transactional
    @DisplayName("money confirmed after a release that failed is flagged too, not filed as a race")
    void moneyAfterAFailedReleaseNeedsAPersonAsWell() throws Exception {
        PaymentAttempt stuck = pendingAttemptFor("b-gone-2", "prov-w7");
        // The row BookingPayments.flag leaves behind: still PENDING, because the provider never
        // agreed to cancel it, and flagged because a person has to.
        stuck.setNeedsAttention(true);
        stuck.setAttentionNote("release of stub payment prov-w7 threw IllegalStateException");
        attempts.save(stuck);
        when(payments.readCallback(any())).thenReturn(PaymentOutcome.captured("prov-w7"));

        mockMvc.perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());

        PaymentAttempt after = attempts.findByProviderReferenceOrderByRecordedAtDesc("prov-w7").get(0);
        assertThat(after.isNeedsAttention()).isTrue();
        assertThat(after.getAttentionNote()).contains("after it was released");
    }

    /**
     * A released payment whose callback carries no money must not overwrite the release.
     *
     * <p>{@code VOIDED} is this platform's own record that it gave the authorization back. A provider
     * following up with {@code FAILED} — which is the ordinary end of a prompt nobody approved — used
     * to be written straight over it, so the one fact an operator needs while reconciling ("did we
     * cancel this, or did it just die?") was destroyed by the callback that confirmed we had. Nothing
     * is owed here and nothing needs flagging; the row is simply left as it is.
     */
    @Test
    @Transactional
    @DisplayName("a failure reported after a void does not erase the record of the void")
    void aReleasedPaymentKeepsItsReleaseState() throws Exception {
        PaymentAttempt released = pendingAttemptFor("b-gone-3", "prov-w8");
        released.setState("VOIDED");
        released.setResolvedAt(Instant.now());
        attempts.save(released);
        when(payments.readCallback(any())).thenReturn(
            new PaymentOutcome(net.jojoaddison.service.payment.PaymentState.FAILED, "prov-w8", "the prompt expired")
        );

        mockMvc.perform(post(WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());

        PaymentAttempt after = attempts.findByProviderReferenceOrderByRecordedAtDesc("prov-w8").get(0);
        assertThat(after.getState()).isEqualTo("VOIDED");
        assertThat(after.isNeedsAttention()).isFalse();
    }

    // ------------------------------------ what the customer may do while the money is undecided --

    /**
     * The preview must not quote a fee against money nobody has taken.
     *
     * <p>{@code cancellation-preview} exists to fill the prototype's modal: "cancelling now costs you
     * X". It had no status guard, so for a booking in {@code PENDING_PAYMENT} whose appointment is
     * inside the free-cancellation window it answered {@code lateCancellation: true} and the full
     * price of the service — a bill, shown to a customer who has paid nothing, for cancelling a booking
     * the endpoint next door will refuse to cancel at all. Both halves are wrong on their own and the
     * pair of them is worse: the screen quotes a charge and then cannot carry it out.
     *
     * <p>It now answers exactly what {@code /cancel} would: if the cancellation is not legal there is
     * nothing to preview, and 409 says so.
     */
    @Test
    @Transactional
    @DisplayName("cancelling a booking whose payment has not arrived has no preview, and no fee")
    void aPendingBookingHasNoCancellationFee() throws Exception {
        String reference = bookPendingSoon("prov-w10");

        mockMvc
            .perform(get(BOOKINGS + "/" + reference + "/cancellation-preview"))
            .andExpect(status().isConflict())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("15000"));
    }

    /**
     * {@code PENDING_PAYMENT} has exactly two exits and the customer holds neither — D43, as amended
     * by the WP-11 review.
     *
     * <p>This asserts a decision rather than fixing a defect, so it pins the 409 the state machine
     * already gives. The customer's "cancel" is deliberately <em>not</em> wired to
     * {@code PENDING_PAYMENT}: at the moment they press it the provider is still holding a live
     * authorization — a page open, or a prompt on their phone that they can approve a minute later —
     * and a cancel that moved the booking without cancelling the payment at the provider is precisely
     * D41's defect, money taken for a booking that does not exist. Abandoning is the provider's
     * callback to report, and releasing the payment first is WP-13's job, alongside the provider that
     * can actually be asked to do it. Recorded in D43 so the dead end is a decision somebody took.
     *
     * <p>It is red against the obvious shortcut: adding {@code PENDING_PAYMENT} to {@code Cancel.from()}.
     */
    @Test
    @Transactional
    @DisplayName("the customer cannot cancel a booking out of PENDING_PAYMENT; only the provider ends it")
    void aPendingBookingIsNotTheCustomersToCancel() throws Exception {
        String reference = bookPendingSoon("prov-w11");

        mockMvc.perform(post(BOOKINGS + "/" + reference + "/cancel").with(csrf())).andExpect(status().isConflict());

        assertThat(statusOf(reference)).isEqualTo("PENDING_PAYMENT");
        // And the payment is untouched, which is the reason for the refusal rather than a side effect
        // of it: nothing here has asked the provider to stop.
        assertThat(attempts.findByProviderReferenceOrderByRecordedAtDesc("prov-w11").get(0).getState()).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------------- helpers --

    /**
     * Books through the real endpoint with a provider that answers pending, and returns the reference.
     *
     * <p>Through the endpoint rather than by saving a row, because what is under test includes the
     * withheld event and the state the resource chose, and a hand-built booking would assert the test's
     * own idea of both.
     */
    private String bookPending(String handle) throws Exception {
        return bookPending(handle, LocalDate.now().plusDays(9), "16:00");
    }

    /**
     * The same, for an appointment inside the free-cancellation window.
     *
     * <p>Midnight tomorrow is between zero and twenty-four hours away whatever time the suite runs, so
     * {@code BookingWorkflow.isLate} would answer true for it. That is what makes the "no fee" half of
     * the abandonment test mean anything: with an appointment nine days out, a {@code Cancel} would
     * also have produced {@code lateCancellation = false} and the assertion would pass against the
     * transition it exists to rule out.
     */
    private String bookPendingSoon(String handle) throws Exception {
        return bookPending(handle, LocalDate.now().plusDays(1), "00:00");
    }

    private String bookPending(String handle, LocalDate date, String time) throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.pendingOnDevice(handle));
        when(payments.readCallback(any())).thenReturn(PaymentOutcome.captured(handle));

        var request = new LinkedHashMap<String, Object>();
        request.put("professionalRef", REF);
        request.put("serviceRef", "s1b");
        request.put("customerName", "Kojo Customer");
        request.put("scheduledDate", date.toString());
        request.put("scheduledTime", time);
        request.put("deliveryMode", "ONLINE");
        mockMvc
            .perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(request)))
            .andExpect(status().isCreated());

        ArgumentCaptor<PaymentIntent> intent = ArgumentCaptor.forClass(PaymentIntent.class);
        org.mockito.Mockito.verify(payments).authorize(intent.capture());
        return intent.getValue().bookingReference();
    }

    /** A payment attempt with no booking behind it — the shape {@code PaymentRecorder} writes. */
    private static PaymentAttempt pendingAttemptFor(String bookingReference, String handle) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setBookingReference(bookingReference);
        attempt.setProvider("stub");
        attempt.setProviderReference(handle);
        attempt.setState("PENDING");
        attempt.setAmountMinor(15000L);
        attempt.setCurrency("GHS");
        attempt.setRecordedAt(Instant.now());
        attempt.setNeedsAttention(false);
        return attempt;
    }

    private String statusOf(String reference) {
        return bookings.findByReference(reference).orElseThrow().getStatus().name();
    }

    private List<OutboxEvent> eventsAbout(String reference) {
        return outbox.findAll().stream().filter(e -> reference.equals(e.getAggregateRef())).toList();
    }
}
