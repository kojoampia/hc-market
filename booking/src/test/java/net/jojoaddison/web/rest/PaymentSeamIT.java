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
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.repository.BookingRepository;
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

    @MockitoBean
    private CatalogClient catalog;

    @MockitoBean
    private PaymentProvider payments;

    @BeforeEach
    void catalogueAnswersNormally() {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), "Africa/Accra")
        );
        when(catalog.loginOf(REF)).thenReturn(OWNER);
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
}
