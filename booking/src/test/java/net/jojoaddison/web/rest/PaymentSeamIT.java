package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PaymentAttempt;
import net.jojoaddison.repository.BookingRepository;
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
}
