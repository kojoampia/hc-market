package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * {@code POST /api/bookings} — what reaches {@code booking.zone_id}, through the endpoint.
 *
 * <p>{@code decisions.md} D60, backlog NEW-20. {@code TheZoneACatalogueOffersIsParsedAtCaptureTest}
 * pins the derivation; this pins the door, and the pair is D58's arrangement for the same column's
 * read side. They are genuinely two tests rather than one written twice: the resource can pass the
 * offering's raw string straight into the builder while {@link net.jojoaddison.service.CapturedZone}
 * remains perfectly correct, which is precisely the state the estate was in until D60, and only
 * this class is red for it.
 *
 * <p>{@link CatalogClient} is mocked for {@code CustomerBookingCreateIT}'s reason: what is under
 * test is the reaction to a catalogue answer, and standing up a second service to produce a
 * malformed zone would test Spring's client instead.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = TheZoneACatalogueOffersIsParsedAtCaptureIT.CUSTOMER)
class TheZoneACatalogueOffersIsParsedAtCaptureIT {

    static final String CUSTOMER = "ama.customer";

    private static final String URL = "/api/bookings";
    private static final String REF = "p1";
    private static final String OWNER = "akosua.mensah";

    /** Not Accra: in Accra a captured string and a captured zone are the same five characters. */
    private static final String FAR_EAST = "Pacific/Kiritimati";

    /** The right name in the wrong case — what a hand-corrected row actually looks like. */
    private static final String UNREADABLE = "africa/accra";

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
        offering("Africa/Accra");
        when(catalog.loginOf(REF)).thenReturn(OWNER);
    }

    private void offering(String zoneId) {
        when(catalog.priceOf(anyString(), anyString())).thenReturn(
            new CatalogClient.Offering(new CatalogClient.ServiceView("s1b", "Follow-up", 15000L, "GHS", true), zoneId)
        );
    }

    private String body() throws Exception {
        var request = new LinkedHashMap<String, Object>();
        request.put("professionalRef", REF);
        request.put("customerName", "Ama Customer");
        request.put("serviceRef", "s1b");
        request.put("scheduledDate", LocalDate.now().plusDays(9).toString());
        request.put("scheduledTime", "16:00");
        request.put("deliveryMode", "ONLINE");
        return om.writeValueAsString(request);
    }

    private org.springframework.test.web.servlet.ResultActions send() throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()));
    }

    /**
     * A professional genuinely outside Ghana keeps their own calendar, spelled as the catalogue
     * spelled it. This is the case {@code Booking.zoneId} exists for (D21, ratified as D55), and a
     * capture that parsed and then "tidied" would be a worse defect than the one D60 closes.
     */
    @Test
    @Transactional
    void aReadableZoneIsStoredExactlyAsOffered() throws Exception {
        offering(FAR_EAST);

        String reference = created();

        assertThat(zoneOf(reference)).isEqualTo(FAR_EAST);
    }

    /**
     * <strong>The defect.</strong> Before D60 this was a 201 and the row held {@code africa/accra},
     * which {@code BookingWorkflow.scheduledAt} then read as Africa/Accra for ever — at WARN, on the
     * boundary that decides a 50% late-cancellation fee, for a professional whose actual calendar
     * nobody afterwards knows.
     */
    @Test
    @Transactional
    void aZoneTzdbCannotReadIsRefusedAndNothingIsWritten() throws Exception {
        offering(UNREADABLE);
        long before = bookings.count();

        send().andExpect(status().isBadGateway());

        assertThat(bookings.count()).isEqualTo(before);
    }

    /**
     * And the refusal carries neither the catalogue's string nor the reference the caller sent —
     * D44 and D45. The value is free text off another service's wire; the reference tells the
     * caller only what it already said.
     */
    @Test
    @Transactional
    void theRefusalCarriesNeitherTheCataloguesStringNorTheReference() throws Exception {
        offering(UNREADABLE);

        String body = send().andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(UNREADABLE).doesNotContain(REF);
    }

    /**
     * Absent is a catalogue one release behind, not a wrong answer — the marketplace's calendar,
     * 201, exactly as before D60. {@code CustomerBookingCreateIT} covers null; this covers blank,
     * which is the shape a column defaulted to the empty string produces.
     */
    @Test
    @Transactional
    void aBlankZoneStillFallsBackToTheMarketplacesCalendar() throws Exception {
        offering("   ");

        String reference = created();

        assertThat(zoneOf(reference)).isEqualTo("Africa/Accra");
    }

    /**
     * Whatever reached the column is readable by the same parser the cancellation boundary uses.
     * That is the property NEW-20 is about, asserted on a row rather than on a return value.
     */
    @Test
    @Transactional
    void whatReachesTheColumnCanBeReadByTheBoundary() throws Exception {
        offering(FAR_EAST);

        assertThat(ZoneId.of(zoneOf(created()))).isEqualTo(ZoneId.of(FAR_EAST));
    }

    private String zoneOf(String reference) {
        return bookings
            .findAll()
            .stream()
            .filter(b -> reference.equals(b.getReference()))
            .map(Booking::getZoneId)
            .findFirst()
            .orElseThrow();
    }

    private String created() throws Exception {
        String response = send().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readValue(response, Map.class).get("reference").toString();
    }
}
