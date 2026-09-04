package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.domain.PaymentAttempt;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.repository.OutboxEventRepository;
import net.jojoaddison.repository.PaymentAttemptRepository;
import net.jojoaddison.service.BookingCreator;
import net.jojoaddison.service.CatalogClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment seam at its one call site — {@code decisions.md} D15/D31.
 *
 * <p>No provider exists yet, so the branch that matters cannot be exercised by the estate as it
 * stands: the real bean always answers {@code OFF_PLATFORM} and every booking is created. That is
 * precisely why these tests substitute a provider. A seam whose refusal path has never run is a seam
 * nobody knows the shape of, and the day a provider is added is the wrong day to find out that a
 * declined payment produces a booking anyway.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "kojo.customer")
class PaymentSeamIT {

    private static final String URL = "/api/bookings";
    private static final String REF = "p1";
    private static final String OWNER = "akosua.mensah";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Autowired
    private OutboxEventRepository outbox;

    @MockitoBean
    private CatalogClient catalog;

    @MockitoBean
    private PaymentProvider payments;

    /**
     * A spy, not a mock: every other test in this class needs the real creator, and only the two
     * compensation tests need it to fall over. Stubbing it per-test keeps them in one class and one
     * Spring context with the tests they are a variation on.
     */
    @MockitoSpyBean
    private BookingCreator creator;

    @BeforeEach
    void catalogueAnswersNormally() {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), "Africa/Accra")
        );
        when(catalog.loginOf(REF)).thenReturn(OWNER);
        // The stub stands in for a configured provider, so it has to have a name: it is written to
        // payment_attempt.provider, and a mock's default null would fail the not-null constraint.
        when(payments.name()).thenReturn("stub");
    }

    private org.springframework.test.web.servlet.ResultActions send() throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("professionalRef", REF);
        request.put("serviceRef", "s1b");
        request.put("customerName", "Kojo Customer");
        request.put("scheduledDate", LocalDate.now().plusDays(9).toString());
        request.put("scheduledTime", "16:00");
        request.put("deliveryMode", "ONLINE");
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(request)));
    }

    /** Today's real behaviour: the customer pays the professional directly and the booking is made. */
    @Test
    @Transactional
    @DisplayName("an off-platform payment is not an obstacle")
    void offPlatformCreatesTheBooking() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.offPlatform());
        send().andExpect(status().isCreated());
    }

    @Test
    @Transactional
    @DisplayName("an authorized payment creates the booking")
    void authorizedCreatesTheBooking() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-123"));
        send().andExpect(status().isCreated());
    }

    /**
     * The branch the seam exists for. 402, and — the half that matters — <strong>no row</strong>: a
     * booking without its money blocks a professional's diary for a session nobody paid for.
     */
    @Test
    @Transactional
    @DisplayName("a declined payment is 402 and writes nothing")
    void declinedWritesNothing() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.declined("insufficient funds"));
        long before = bookings.count();

        send().andExpect(status().isPaymentRequired());

        assertThat(bookings.count()).isEqualTo(before);
    }

    /**
     * A provider that fell over is not a customer who cannot pay. Distinguished because the client's
     * next move differs: retry the same instrument, rather than find another one.
     */
    @Test
    @Transactional
    @DisplayName("a provider failure is 502, not 402")
    void providerFailureIsNotADecline() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.failed("gateway timeout"));
        long before = bookings.count();

        send().andExpect(status().isBadGateway());

        assertThat(bookings.count()).isEqualTo(before);
    }

    /**
     * The intent must carry the catalogue's price, not the request's.
     *
     * <p>D22 established that {@code priceMinor} comes from the catalogue because the browser used to
     * decide what a booking cost. A payment seam is the second place that number becomes real money,
     * so it is worth asserting that the amount the provider is asked to take is the priced one and
     * not something reconstructed from the request on the way past.
     */
    @Test
    @Transactional
    @DisplayName("the intent carries the catalogue's price, in minor units")
    void intentCarriesThePricedAmount() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.offPlatform());

        send().andExpect(status().isCreated());

        ArgumentCaptor<PaymentIntent> intent = ArgumentCaptor.forClass(PaymentIntent.class);
        org.mockito.Mockito.verify(payments).authorize(intent.capture());
        assertThat(intent.getValue().amountMinor()).isEqualTo(15000L);
        assertThat(intent.getValue().currency()).isEqualTo("GHS");
        assertThat(intent.getValue().customerLogin()).isEqualTo("kojo.customer");
        assertThat(intent.getValue().bookingReference()).startsWith("b-");
    }

    // ------------------------------------------------ the handle, and giving the money back --

    /**
     * The sharpest defect this seam had — {@code decisions.md} D41.
     *
     * <p>The provider's reference was read for its state and dropped. It is the first argument of
     * {@code capture}, {@code refund}, {@code voidAuthorization} and {@code status}, it is issued by
     * somebody else and derivable from nothing, and there was no table and no log line holding it. So
     * the day a real provider first answered {@code AUTHORIZED}, the money was committed and the
     * platform held nothing to complete the lifecycle with.
     */
    @Test
    @Transactional
    @DisplayName("the provider's handle is kept, not read and dropped")
    void theHandleIsStored() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-a1"));

        send().andExpect(status().isCreated());

        PaymentAttempt attempt = onlyAttempt();
        assertThat(attempt.getProviderReference()).isEqualTo("prov-a1");
        assertThat(attempt.getProvider()).isEqualTo("stub");
        assertThat(attempt.getState()).isEqualTo("AUTHORIZED");
        assertThat(attempt.getAmountMinor()).isEqualTo(15000L);
        assertThat(attempt.getCurrency()).isEqualTo("GHS");
        assertThat(attempt.isNeedsAttention()).isFalse();
        assertThat(attempt.getResolvedAt()).isNull();
    }

    /**
     * The other half of the same rule: a row is written when there is a handle to write, and only
     * then.
     *
     * <p>Today's estate is entirely off-platform — the customer pays the professional directly — so
     * recording every outcome would fill a table with one contentless row per booking and make the
     * "is there money against this booking?" question answer yes for every booking in the estate.
     * There is no fact to keep, so nothing is kept.
     */
    @Test
    @Transactional
    @DisplayName("an off-platform booking writes no payment row, because there is no handle")
    void offPlatformStoresNothing() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.offPlatform());

        send().andExpect(status().isCreated());

        assertThat(attemptsForThisBooking()).isEmpty();
    }

    /**
     * Money taken, no booking — and now something is done about it.
     *
     * <p>{@code creator.create} throwing after a successful authorization left the customer charged
     * for a booking that does not exist, with nothing anywhere naming the payment. It is the central
     * defect seen from the other side, and it could not be fixed before the handle was kept: there
     * was nothing to void the authorization with.
     */
    @Test
    @Transactional
    @DisplayName("a booking that cannot be created gives the money back")
    void creationFailureReleasesTheAuthorization() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-a2"));
        when(payments.voidAuthorization(anyString(), anyString())).thenReturn(PaymentOutcome.voided("prov-a2"));
        org.mockito.Mockito.doThrow(new IllegalStateException("the outbox insert failed")).when(creator).create(any(), anyString());

        send().andExpect(status().isInternalServerError());

        org.mockito.Mockito.verify(payments).voidAuthorization(org.mockito.ArgumentMatchers.eq("prov-a2"), anyString());
        PaymentAttempt attempt = onlyAttempt();
        assertThat(attempt.getState()).isEqualTo("VOIDED");
        assertThat(attempt.getResolvedAt()).isNotNull();
        assertThat(attempt.isNeedsAttention()).isFalse();
    }

    /**
     * A provider that has already settled cannot void, and every provider that distinguishes the two
     * calls refuses one for the other. So the compensation is chosen by state — and the refund is
     * where the currency had to be added, because a refund in the wrong currency is a second wrong
     * transaction rather than a rejected one.
     */
    @Test
    @Transactional
    @DisplayName("money already captured is refunded rather than voided, with its currency")
    void capturedMoneyIsRefunded() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.captured("prov-a3"));
        when(payments.refund(anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString())).thenReturn(
            PaymentOutcome.refunded("prov-a3")
        );
        org.mockito.Mockito.doThrow(new IllegalStateException("the outbox insert failed")).when(creator).create(any(), anyString());

        send().andExpect(status().isInternalServerError());

        org.mockito.Mockito.verify(payments).refund(
            org.mockito.ArgumentMatchers.eq("prov-a3"),
            org.mockito.ArgumentMatchers.eq(15000L),
            org.mockito.ArgumentMatchers.eq("GHS"),
            anyString()
        );
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).voidAuthorization(anyString(), anyString());
        assertThat(onlyAttempt().getState()).isEqualTo("REFUNDED");
    }

    /**
     * The case a person has to be told about: the money is committed, the booking does not exist, and
     * giving it back did not work either.
     *
     * <p>Nothing retries — a second automatic attempt against a provider that has just failed is how
     * one stuck payment becomes several — so the row is marked instead, and {@code needs_attention}
     * is the column an operator queries. The note names the provider, the reference and the failure's
     * class, and deliberately not the provider's message: that is the route by which a customer's
     * details arrive in a table the erasure sweep does not visit.
     */
    @Test
    @Transactional
    @DisplayName("a release that fails is flagged for a person, not retried")
    void aFailedReleaseIsFlagged() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.authorized("prov-a4"));
        when(payments.voidAuthorization(anyString(), anyString())).thenThrow(new IllegalStateException("provider unreachable"));
        org.mockito.Mockito.doThrow(new IllegalStateException("the outbox insert failed")).when(creator).create(any(), anyString());

        send().andExpect(status().isInternalServerError());

        PaymentAttempt attempt = onlyAttempt();
        assertThat(attempt.isNeedsAttention()).isTrue();
        assertThat(attempt.getAttentionNote()).isEqualTo("release of stub payment prov-a4 threw IllegalStateException");
        // Still AUTHORIZED: that is what the provider last told us, and overwriting it would lose the
        // one fact the person clearing this up needs.
        assertThat(attempt.getState()).isEqualTo("AUTHORIZED");
        assertThat(attempt.getAttentionNote()).doesNotContain("kojo.customer");
    }

    // ------------------------------------------------------- the payment that is not decided yet --

    /**
     * D43's decision, at the endpoint that takes it: <strong>a booking may exist while its payment is
     * pending</strong>, and it exists in a state nobody has been told about.
     *
     * <p>Three assertions, and the third is the one that matters. The row is written, so the customer
     * has something to come back to and the erasure sweep has something to find. Its status is
     * {@code PENDING_PAYMENT}, which {@code /api/pro/requests} does not ask for. And <strong>no
     * {@code booking.requested} is in the outbox</strong> — the professional is told when the money is
     * confirmed and not before, which is the whole reason the state can be permitted to exist at all.
     * A status filter alone would not do it: the event is what reaches messaging, and no query of
     * messaging's is under this service's control.
     */
    @Test
    @Transactional
    @DisplayName("a pending payment creates a booking nobody has been told about")
    void pendingCreatesASilentBooking() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.pendingAt("prov-p1", "https://checkout.example.com/pay/abc"));

        send()
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.payment.state").value("PENDING"))
            .andExpect(jsonPath("$.payment.action").value("VISIT_URL"))
            .andExpect(jsonPath("$.payment.url").value("https://checkout.example.com/pay/abc"));

        String reference = referenceOfThisBooking();
        assertThat(bookings.findAll().stream().filter(b -> reference.equals(b.getReference())).findFirst())
            .get()
            .extracting(b -> b.getStatus().name())
            .isEqualTo("PENDING_PAYMENT");
        assertThat(eventsAbout(reference)).isEmpty();
    }

    /**
     * The mobile-money half of the same answer: there is nowhere to send the customer, and that is a
     * next action rather than a missing one.
     */
    @Test
    @Transactional
    @DisplayName("a phone prompt is reported as a next action with no url")
    void pendingOnDeviceHasNoUrl() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.pendingOnDevice("prov-p2"));

        send()
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.payment.action").value("AWAIT_DEVICE_PROMPT"))
            .andExpect(jsonPath("$.payment.url").doesNotExist());
    }

    /**
     * The asynchronous shape of D41's defect, and the reason {@code release} cannot test
     * {@code holdsMoney()} alone.
     *
     * <p>A pending payment holds nothing — yet — and the customer is looking at a prompt they can
     * approve a minute after the booking failed to be created. Left alone, that is money taken for a
     * booking that does not exist, confirmed by a webhook that will never find one. So the pending
     * payment is cancelled at the provider on the same path an authorization is voided on.
     */
    @Test
    @Transactional
    @DisplayName("a pending payment is cancelled at the provider when the booking cannot be created")
    void pendingIsReleasedToo() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.pendingOnDevice("prov-p3"));
        when(payments.voidAuthorization(anyString(), anyString())).thenReturn(PaymentOutcome.voided("prov-p3"));
        org.mockito.Mockito.doThrow(new IllegalStateException("the outbox insert failed")).when(creator).create(any(), anyString());
        org.mockito.Mockito.doThrow(new IllegalStateException("the outbox insert failed"))
            .when(creator)
            .createAwaitingPayment(any(), anyString());

        send().andExpect(status().isInternalServerError());

        org.mockito.Mockito.verify(payments).voidAuthorization(org.mockito.ArgumentMatchers.eq("prov-p3"), anyString());
        assertThat(onlyAttempt().getState()).isEqualTo("VOIDED");
    }

    // ------------------------------------------------------------- nothing to pay, and no answer --

    /**
     * WP-12's reason to exist, at the endpoint — {@code decisions.md} D44.
     *
     * <p>Two seeded professionals offer a service at {@code priceMinor: 0}. Every provider D37 chose
     * refuses an authorization for 0, so the day one is configured every free booking in the estate
     * becomes a 402 or a 502 depending on how that provider phrases its refusal — and nothing here
     * would have gone red beforehand, because the unconfigured provider says {@code OFF_PLATFORM} to
     * any amount at all. Confirmed as {@code expected:<201> but was:<402>} before the guard existed.
     *
     * <p>The first assertion is the substance: <strong>no provider is asked</strong>. The
     * booking is created in {@code REQUESTED} rather than {@code PENDING_PAYMENT} — nothing would ever
     * confirm a payment that was never started — {@code booking.requested} is published, so the
     * professional hears about it exactly as they do for a priced booking, and no
     * {@code payment_attempt} row is written because no handle came back (D41).
     */
    @Test
    @Transactional
    @DisplayName("a free booking is created without asking any provider to authorize nothing")
    void aFreeBookingAsksNoProvider() throws Exception {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Community walking group", 0L, "GHS", true), "Africa/Accra")
        );
        // What a configured provider actually does with an amount of 0, and the reason this test was
        // red before the guard existed: a refusal here made the booking a 402 rather than a booking.
        when(payments.authorize(any())).thenReturn(PaymentOutcome.declined("amount must be greater than zero"));

        String body = send()
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.priceMinor").value(0))
            .andExpect(jsonPath("$.payment").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).authorize(any());
        String reference = om.readTree(body).path("reference").asText();
        assertThat(attempts.findByBookingReferenceOrderByRecordedAtAsc(reference)).isEmpty();
        // The recorder qualifies the name; what matters here is that the professional's half of the
        // estate is told about a free booking exactly as it is told about a priced one.
        assertThat(eventsAbout(reference)).extracting(OutboxEvent::getType).containsExactly("healthconnect.booking.requested");
    }

    /**
     * An adapter that throws answers the customer the way a provider that failed does — D44.
     *
     * <p>{@code FAILED} and its 502 existed already; there was no route to them from an exception, so
     * a provider whose client timed out produced a 500 and a stack trace while a provider that
     * answered {@code FAILED} produced a 502 and a retry. The 500 is the wrong answer twice over: it
     * says this platform is broken when a third party is, and it is the one shape of response a client
     * is entitled to treat as "do not try that again".
     *
     * <p>And the provider's own words stay out of the body, for the reason D41 keeps them out of
     * {@code attention_note} and D43 keeps them off the next action: an exception message from a
     * payment provider is where a phone number or a cardholder name arrives unannounced.
     */
    @Test
    @Transactional
    @DisplayName("a provider that throws is a 502, not a 500, and does not quote itself")
    void aThrowingProviderIsNotAServerError() throws Exception {
        when(payments.authorize(any())).thenThrow(new IllegalStateException("MTN refused 0244123456 for Ama Mensah"));
        long before = bookings.count();

        String body = send().andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();

        assertThat(bookings.count()).isEqualTo(before);
        assertThat(body).doesNotContain("Ama Mensah").doesNotContain("0244123456");
    }

    /**
     * The same rule on the path a provider actually takes — {@code decisions.md} D44, as reviewed.
     *
     * <p>The test above pins the <em>thrown</em> path, where {@code BookingPayments} composes the
     * reason itself. This one pins the <em>answered</em> path, which is the common case and was the
     * hole: {@code PaymentOutcome.declined(reason)} takes an adapter-authored string and
     * {@code authorizePayment} relayed it verbatim into a {@code ResponseStatusException}, from where
     * {@code ExceptionTranslator} renders it as the ProblemDetail's {@code detail}.
     *
     * <p>The failure this prevents is one line of WP-13: a Paystack adapter writing
     * {@code declined(response.path("message").asText())}, Paystack answering with the cardholder's
     * name and the phone number it texted, and that landing in a 402 body and in every client that
     * logs a ProblemDetail. {@code getCustomizedErrorDetails} redacts only package names and
     * {@code DataAccessException}, and only under {@code prod}, so nothing downstream saves this.
     */
    @Test
    @Transactional
    @DisplayName("a decline does not quote the provider back to the customer")
    void aDeclineDoesNotQuoteTheProvider() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.declined("Declined — card ending 4242, Ama Mensah, 0244123456"));

        String body = send().andExpect(status().isPaymentRequired()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Ama Mensah").doesNotContain("0244123456").doesNotContain("4242");
        // Composed from the state, so the customer is still told which of the two answers this was.
        assertThat(body).contains("declined");
    }

    /** And a provider that answered {@code FAILED} in words of its own is the same hazard. */
    @Test
    @Transactional
    @DisplayName("an answered provider failure does not quote the provider either")
    void anAnsweredFailureDoesNotQuoteTheProvider() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.failed("upstream rejected the token for Ama Mensah on 0244123456"));

        String body = send().andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Ama Mensah").doesNotContain("0244123456");
    }

    /**
     * {@code NOTHING_TO_PAY} is not a provider's to give — {@code decisions.md} D44, as reviewed.
     *
     * <p>The state is documented as the one value in the enum no provider reports, and
     * {@link PaymentOutcome#nothingToPay()} is a public factory on the record every adapter
     * constructs. Nothing stopped an adapter mapping an unknown status onto it, and the consequence
     * is the worst shape available here: {@code permitsBooking()} is true and the state is not
     * {@code PENDING}, so a ₵150.00 booking is created in {@code REQUESTED},
     * {@code booking.requested} is published, the professional is told, no {@code payment_attempt}
     * row exists because no handle came back — and no money moved. Nothing in the estate disagrees
     * with anything.
     *
     * <p>So it is refused where the amount is known. {@code FAILED}, and therefore 502: it is a
     * provider answering something this platform cannot use, which is exactly what that state means.
     */
    @Test
    @Transactional
    @DisplayName("a provider may not declare a priced booking free")
    void aProviderCannotDeclareAPricedBookingFree() throws Exception {
        when(payments.authorize(any())).thenReturn(PaymentOutcome.nothingToPay());
        long before = bookings.count();

        send().andExpect(status().isBadGateway());

        assertThat(bookings.count()).isEqualTo(before);
    }

    // ------------------------------------------------------------------------- helpers --

    /**
     * The payment rows for the booking this test just tried to make.
     *
     * <p>Found through the intent the resource passed the provider, because the reference is minted
     * inside the request and nothing else knows it.
     *
     * <p>These rows are committed rather than rolled back with the test, and that is the behaviour
     * under test rather than an accident: {@code PaymentRecorder} is {@code REQUIRES_NEW} precisely
     * so that a handle survives the failure of the booking it was taken for. Each test's reference is
     * a fresh UUID, so the rows left behind cannot be seen by another test.
     */
    private List<PaymentAttempt> attemptsForThisBooking() {
        ArgumentCaptor<PaymentIntent> intent = ArgumentCaptor.forClass(PaymentIntent.class);
        org.mockito.Mockito.verify(payments).authorize(intent.capture());
        return attempts.findByBookingReferenceOrderByRecordedAtAsc(intent.getValue().bookingReference());
    }

    private PaymentAttempt onlyAttempt() {
        List<PaymentAttempt> found = attemptsForThisBooking();
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    /** The reference minted inside the request, read back off the intent the provider was given. */
    private String referenceOfThisBooking() {
        ArgumentCaptor<PaymentIntent> intent = ArgumentCaptor.forClass(PaymentIntent.class);
        org.mockito.Mockito.verify(payments).authorize(intent.capture());
        return intent.getValue().bookingReference();
    }

    /** Every outbox row about one booking. The seeded estate is full of them, so filter by reference. */
    private List<OutboxEvent> eventsAbout(String reference) {
        return outbox.findAll().stream().filter(e -> reference.equals(e.getAggregateRef())).toList();
    }
}
