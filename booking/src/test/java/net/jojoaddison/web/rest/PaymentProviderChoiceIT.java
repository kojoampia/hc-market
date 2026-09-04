package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PaymentAttempt;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.repository.PaymentAttemptRepository;
import net.jojoaddison.service.CatalogClient;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * An estate with more than one payment provider — {@code decisions.md} D45.
 *
 * <p>Every other payment test in this repository substitutes <em>one</em> provider, because until
 * D45 there could only be one. D37 chose three and made the customer choose between them, and the
 * questions that opens cannot be asked of a single-provider context: which adapter is asked, which is
 * refused, what happens when the client names nothing, and which adapter a callback is handed to.
 *
 * <p>Two stubs stand in for two configured providers. They are named {@code alpha} and {@code beta}
 * rather than after real ones deliberately: nothing here has ever spoken to Paystack, Hubtel or MTN
 * MoMo, and a test named after one of them would read as evidence about that provider when it is
 * evidence about this estate's wiring. The three real adapters refuse everything by design and are
 * covered in {@code ProviderAwaitingIntegrationUnitTest}.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "kojo.customer")
// Imported explicitly rather than relied on as a nested @TestConfiguration: @IntegrationTest names
// its configuration classes, and Spring Boot's auto-detection of inner test configuration only runs
// when none are named. Without this the context starts with one provider and every test here fails
// on an unsatisfied @Qualifier — which reads as a broken registry rather than a missing import.
@org.springframework.context.annotation.Import(PaymentProviderChoiceIT.TwoConfiguredProviders.class)
class PaymentProviderChoiceIT {

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

    // Qualified by name rather than by type: there are three PaymentProvider beans in this context
    // — these two and the fallback — which is the arrangement being tested.
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("alpha")
    private PaymentProvider alpha;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("beta")
    private PaymentProvider beta;

    /**
     * Two providers beside the fallback, which is what a configured estate looks like.
     *
     * <p>Declared as {@code @Bean}s in a nested {@code @TestConfiguration} — the one shape D44 warned
     * against and D45 made harmless, so this arrangement is itself a small assertion that nothing
     * injects a {@code PaymentProvider} by type any more.
     */
    @TestConfiguration
    static class TwoConfiguredProviders {

        @Bean
        PaymentProvider alpha() {
            return mock(PaymentProvider.class);
        }

        @Bean
        PaymentProvider beta() {
            return mock(PaymentProvider.class);
        }
    }

    @BeforeEach
    void twoProvidersAndAPricedService() {
        // Beans rather than @MockitoBean, so nothing resets them between tests.
        reset(alpha, beta);
        when(alpha.name()).thenReturn("alpha");
        when(beta.name()).thenReturn("beta");
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), "Africa/Accra")
        );
        when(catalog.loginOf(REF)).thenReturn(OWNER);
    }

    private org.springframework.test.web.servlet.ResultActions book(String provider) throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("professionalRef", REF);
        request.put("serviceRef", "s1b");
        request.put("customerName", "Kojo Customer");
        request.put("scheduledDate", LocalDate.now().plusDays(9).toString());
        request.put("scheduledTime", "16:00");
        request.put("deliveryMode", "ONLINE");
        if (provider != null) {
            request.put("paymentProvider", provider);
        }
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(request)));
    }

    // ------------------------------------------------------------------- the customer's choice --

    /**
     * The package's reason to exist, at the endpoint.
     *
     * <p>The customer named {@code beta}, so {@code beta} raised the prompt and {@code alpha} was not
     * asked anything at all. Three things are asserted because three different things could be wrong:
     * the money went to the named provider, the response tells the customer whose prompt to expect,
     * and {@code payment_attempt} records who is holding it — a row naming the wrong provider is a
     * reconciliation that never resolves.
     */
    @Test
    @Transactional
    @DisplayName("the provider the customer names is the one that takes the money")
    void theChoiceReachesThatProviderAndNoOther() throws Exception {
        when(beta.authorize(any())).thenReturn(PaymentOutcome.pendingOnDevice("prov-b1"));

        book("beta")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.payment.provider").value("beta"))
            .andExpect(jsonPath("$.payment.action").value("AWAIT_DEVICE_PROMPT"));

        verify(beta).authorize(any());
        verify(alpha, never()).authorize(any());
        assertThat(attempts.findAll().stream().filter(a -> "prov-b1".equals(a.getProviderReference())).toList())
            .singleElement()
            .extracting(PaymentAttempt::getProvider)
            .isEqualTo("beta");
    }

    /**
     * A name this estate does not offer is a 409, and nothing happens.
     *
     * <p>409 rather than 400 for the same reason D22 answers a stale price with one: the realistic
     * cause is a client holding a list the estate has since changed, and its next move — re-read and
     * ask again — is also the right move when the name was never valid. The refusal happens before any
     * provider is asked for money, so there is no authorization to give back.
     */
    @Test
    @Transactional
    @DisplayName("a provider this estate does not offer is refused, and no money is asked for")
    void anUnofferedProviderIsAConflict() throws Exception {
        long before = bookings.count();

        book("some-other-wallet")
            .andExpect(status().isConflict())
            // The offer is this estate's own configuration and is safe to say. The name that was
            // asked for is not echoed — that is a stranger's string on its way to a response body.
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("alpha", "beta"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("some-other-wallet"));

        assertThat(bookings.count()).isEqualTo(before);
        verify(alpha, never()).authorize(any());
        verify(beta, never()).authorize(any());
    }

    /**
     * With two providers, naming neither is a 400 that says what there is to choose from.
     *
     * <p>The platform must not pick who takes the customer's money. Picking the first would make that
     * depend on bean registration order, which is exactly the property the registry removed; picking
     * the fallback would create a booking with no money behind it and tell the professional about it.
     *
     * <p>The body lists the providers because there is no endpoint that publishes them — see D45 on
     * why one was not built — so this refusal is a client's only way to learn the names.
     */
    @Test
    @Transactional
    @DisplayName("naming no provider where there are two is a 400 listing them")
    void aChoiceIsRequired() throws Exception {
        long before = bookings.count();

        book(null)
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("alpha", "beta"));

        assertThat(bookings.count()).isEqualTo(before);
        verify(alpha, never()).authorize(any());
        verify(beta, never()).authorize(any());
    }

    /**
     * A free booking is made without naming anybody, even here — {@code decisions.md} D44 and D45.
     *
     * <p>Two of the eighteen seeded professionals offer a service at {@code priceMinor: 0}. Requiring
     * a payment provider for a booking that costs nothing would make every one of those uncreatable
     * the day a second provider was configured, which is D44's defect returning by a different road.
     * The zero-amount guard runs before the choice is resolved, and this is what says so at the
     * endpoint.
     */
    @Test
    @Transactional
    @DisplayName("a free booking is created without naming a provider, even where one would be required")
    void aFreeBookingNeedsNoChoice() throws Exception {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Community walking group", 0L, "GHS", true), "Africa/Accra")
        );

        book(null).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED")).andExpect(
            jsonPath("$.payment").doesNotExist()
        );

        verify(alpha, never()).authorize(any());
        verify(beta, never()).authorize(any());
    }

    // ------------------------------------------------------------------ the callback's provider --

    /**
     * A callback is read by the adapter it is addressed to, and by no other — {@code decisions.md}
     * D43/D45.
     *
     * <p>D43 wrote the refusal for "a callback addressed to a provider this service is not configured
     * for" and it could not do any real work: there was one provider, so the comparison had one
     * possible right answer. Here the estate runs two, and handing {@code beta}'s body to
     * {@code alpha}'s verifier is how a callback signed by one provider gets applied as another's —
     * either as a refusal of something genuine, or, with an adapter that is careless about which
     * secret it checks, as an acceptance of something forged.
     *
     * <p>Both stubs refuse, so the answer is the flat 401 either way. What is asserted is
     * <em>which</em> adapter was asked.
     */
    @Test
    @Transactional
    @DisplayName("a callback is handed to the provider named in its path and to no other")
    void aCallbackReachesTheAdapterItNames() throws Exception {
        when(beta.readCallback(any())).thenThrow(new PaymentCallbackRefused("signature does not match"));

        mockMvc
            .perform(post("/webhooks/payments/beta").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"x\"}"))
            .andExpect(status().isUnauthorized());

        verify(beta).readCallback(any());
        verify(alpha, never()).readCallback(any());
    }

    /**
     * A callback addressed to a provider this estate does not run reaches no adapter at all.
     *
     * <p>The same 401, from one step earlier. It matters that nothing is asked: an adapter that is
     * handed a body has to decide about it, and the safest decision is one it is never asked to make.
     */
    @Test
    @Transactional
    @DisplayName("a callback for a provider this estate does not run is refused before anything reads it")
    void aCallbackForNobodyIsRefusedUnread() throws Exception {
        mockMvc
            .perform(
                post("/webhooks/payments/some-other-wallet").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"x\"}")
            )
            .andExpect(status().isUnauthorized());

        verify(alpha, never()).readCallback(any());
        verify(beta, never()).readCallback(any());
    }
}
