package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.service.CatalogClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/bookings} — the two things about a create that the caller does not get to decide:
 * what it costs, and <em>whose it is</em>.
 *
 * <p>The price half is {@code decisions.md} D22. This class covers the ownership half, D28: until
 * then {@code professionalLogin} was stored exactly as the browser sent it, so a request carrying a
 * truthful {@code professionalRef} beside somebody else's login put a real booking into an inbox it
 * did not belong to — and nothing downstream disagreed, because every derived figure follows
 * faithfully from the login that was stored. It is a misdelivery bug, not a wrong-number bug, which
 * is why no amount of reconciliation would have surfaced it.
 *
 * <p>{@link CatalogClient} is mocked rather than stubbed over HTTP: what is under test is the
 * resource's reaction to each of the catalogue's three possible answers, and standing up a second
 * service to produce them would test Spring's client instead.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = CustomerBookingCreateIT.CUSTOMER)
class CustomerBookingCreateIT {

    static final String CUSTOMER = "kojo.customer";

    private static final String URL = "/api/bookings";
    private static final String REF = "p1";
    private static final String OWNER = "akosua.mensah";
    private static final String IMPOSTOR = "someone.else";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingRepository bookings;

    @MockitoBean
    private CatalogClient catalog;

    @BeforeEach
    void catalogueAnswersNormally() {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), "Africa/Accra")
        );
        when(catalog.loginOf(REF)).thenReturn(OWNER);
    }

    private String body(String professionalLogin) throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("professionalRef", REF);
        request.put("professionalLogin", professionalLogin);
        request.put("customerName", "Kojo Customer");
        request.put("serviceRef", "s1b");
        request.put("scheduledDate", LocalDate.now().plusDays(9).toString());
        request.put("scheduledTime", "16:00");
        request.put("deliveryMode", "ONLINE");
        return om.writeValueAsString(request);
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @Transactional
    void storesTheCataloguesLoginWhenTheRequestAgrees() throws Exception {
        String reference = created(body(OWNER));
        assertThat(bookings.findAll().stream().filter(b -> reference.equals(b.getReference())).map(Booking::getProfessionalLogin))
            .containsExactly(OWNER);
    }

    /**
     * The request may omit the field entirely. It is not the source of truth any more, so requiring
     * it would only make the caller guess at something the server is about to establish anyway.
     */
    @Test
    @Transactional
    void fillsTheLoginInWhenTheRequestOmitsIt() throws Exception {
        String reference = created(body(null));
        assertThat(bookings.findAll().stream().filter(b -> reference.equals(b.getReference())).map(Booking::getProfessionalLogin))
            .containsExactly(OWNER);
    }

    /**
     * <strong>The hole D28 closes.</strong> 409 rather than a silent correction, matching how a
     * stale price is handled: the realistic cause is a profile read before an ownership change, and
     * quietly rewriting a caller's data teaches it nothing. Nothing is written either way.
     */
    @Test
    @Transactional
    void aLoginThatIsNotTheOwnersIsRefusedAndNothingIsWritten() throws Exception {
        long before = bookings.count();
        send(body(IMPOSTOR)).andExpect(status().isConflict());
        assertThat(bookings.count()).isEqualTo(before);
    }

    /** And the refusal must not become the disclosure the endpoint exists to avoid. */
    @Test
    @Transactional
    void theRefusalDoesNotNameTheRealOwner() throws Exception {
        String message = send(body(IMPOSTOR)).andReturn().getResponse().getContentAsString();
        assertThat(message).doesNotContain(OWNER);
    }

    /**
     * 503, not 500 and certainly not a booking: nothing is broken, the owner simply cannot be
     * established right now. Same treatment the price call gets, and for the same reason — a guessed
     * owner is permanent, a retry is not.
     */
    @Test
    @Transactional
    void aCatalogueThatCannotBeAskedIs503() throws Exception {
        when(catalog.loginOf(REF)).thenThrow(new CatalogClient.CatalogUnavailable("down"));
        send(body(OWNER)).andExpect(status().isServiceUnavailable());
    }

    /**
     * decisions.md D21: the booking's wall clock belongs to the PROFESSIONAL's zone, captured from
     * the same answer that priced it and stored rather than resolved later.
     */
    @Test
    @Transactional
    void storesTheProfessionalsZone() throws Exception {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), "Europe/London")
        );

        String reference = created(body(OWNER));
        assertThat(bookings.findAll().stream().filter(b -> reference.equals(b.getReference())).map(Booking::getZoneId))
            .containsExactly("Europe/London");
    }

    /**
     * A catalogue one release behind sends no zone. Ghana is UTC+0 all year, so falling back cannot
     * make the time wrong today — and refusing would fail every booking in the estate over a field
     * that changes nothing. The price and the owner deliberately get no such latitude.
     */
    @Test
    @Transactional
    void fallsBackToAccraWhenTheCatalogueSendsNoZone() throws Exception {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), null)
        );

        String reference = created(body(OWNER));
        assertThat(bookings.findAll().stream().filter(b -> reference.equals(b.getReference())).map(Booking::getZoneId))
            .containsExactly("Africa/Accra");
    }

    /** A reference the catalogue does not know is 404 — the customer's profile link is stale. */
    @Test
    @Transactional
    void anUnknownProfessionalIs404() throws Exception {
        when(catalog.loginOf(REF)).thenThrow(new CatalogClient.UnknownOffering("no such professional: p1"));
        send(body(OWNER)).andExpect(status().isNotFound());
    }

    private String created(String json) throws Exception {
        String response = send(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readValue(response, Map.class).get("reference").toString();
    }
}
