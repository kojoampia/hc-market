package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/bookings/{ref}/cancellation-preview} in the booking's own zone — {@code
 * decisions.md} D58, backlog NEW-19.
 *
 * <p>This is the customer-visible half of the item. The endpoint quotes the hours remaining and
 * whether a 50% late-cancellation fee applies, and it converted the appointment with {@code
 * ZoneOffset.UTC} — so the hour at which a cancellation became late was the marketplace's rather than
 * the professional's. {@code TheAppointmentIsInTheBookingsZoneTest} pins the derivation;
 * <strong>this pins the endpoint</strong>, and neither can see the other's failure: the resource held
 * its own copy of the conversion until D58, so the workflow could be correct while this answer was
 * not, which is exactly the shape NEW-19 was filed as.
 *
 * <h2>Why the assertions are shaped this way</h2>
 *
 * <p>{@code cancellationPreview} reads {@code Instant.now()} and there is no seam to fix it — {@code
 * MarketCalendar} is a copied-and-diffed file (D51) and giving this one resource a clock is a change
 * to a different question. So the appointments are anchored to the top of the current UTC hour and
 * placed <strong>hours away from the 24-hour boundary</strong> in both spellings, and each case
 * asserts a verdict the UTC spelling gets backwards. No margin here is under six hours.
 *
 * <p>{@code Pacific/Kiritimati} (+14) and {@code Pacific/Honolulu} (-10) are 24 hours apart, observe
 * no daylight saving, and are neither of them {@code Africa/Accra} — which is GMT with no offset, so
 * a booking in the estate's own zone cannot distinguish either implementation.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = TheCancellationBoundaryIsInTheBookingsZoneIT.CUSTOMER)
class TheCancellationBoundaryIsInTheBookingsZoneIT {

    static final String CUSTOMER = "esi.cancels";

    private static final String EAST = "Pacific/Kiritimati";
    private static final String WEST = "Pacific/Honolulu";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingRepository bookings;

    /**
     * A booking whose wall clock is {@code hours} from the top of this UTC hour, read in {@code zone}.
     *
     * <p>The wall clock is what is stored, so the same number of hours means a different instant in
     * each zone — which is the whole point. Truncating to the hour keeps the two fixtures in a case
     * exactly 24 hours apart rather than 24 hours and a few milliseconds.
     */
    private String booking(String reference, String zone, long hours) {
        LocalDateTime wallClock = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).plusHours(hours);
        bookings.save(
            new Booking()
                .reference(reference)
                .customerLogin(CUSTOMER)
                .customerName("Esi Cancels")
                .professionalRef("p1")
                .professionalLogin("akosua.mensah")
                .serviceRef("s1a")
                .serviceName("Nutrition assessment")
                .priceMinor(28_000L)
                .currency("GHS")
                .scheduledDate(wallClock.toLocalDate())
                .scheduledTime(wallClock.toLocalTime())
                .zoneId(zone)
                .deliveryMode(DeliveryMode.ONLINE)
                .status(BookingStatus.REQUESTED)
                .careSummaryShared(false)
                .raisedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .reviewed(false)
        );
        return reference;
    }

    private JsonNode preview(String reference) throws Exception {
        String body = mockMvc
            .perform(get("/api/bookings/" + reference + "/cancellation-preview"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return om.readTree(body);
    }

    /**
     * 30 hours of wall clock is 16 hours away in Kiritimati and 30 in UTC, so the fee applies in the
     * booking's own calendar and did not in the spelling this replaced. The margin either side of
     * the 24-hour window is 8 hours and 6 hours.
     */
    @Test
    @Transactional
    @DisplayName("east of UTC: a cancellation is late when the booking's own zone says it is")
    void eastOfUtcIsLate() throws Exception {
        JsonNode preview = preview(booking("b-zone-east", EAST, 30));

        assertThat(preview.get("lateCancellation").asBoolean()).as("16h away in %s, 30h read as UTC", EAST).isTrue();
        assertThat(preview.get("hoursUntilAppointment").asLong()).as("hours quoted to the customer").isBetween(15L, 16L);
    }

    /**
     * The same test pointing the other way, and the direction that costs a customer money: 20 hours
     * of wall clock is inside the window read as UTC — a 50% fee — and 30 hours away in Honolulu,
     * which is free. Margins of 4 and 6 hours.
     */
    @Test
    @Transactional
    @DisplayName("west of UTC: a cancellation is free when the booking's own zone says it is")
    void westOfUtcIsFree() throws Exception {
        JsonNode preview = preview(booking("b-zone-west", WEST, 20));

        assertThat(preview.get("lateCancellation").asBoolean()).as("30h away in %s, 20h read as UTC", WEST).isFalse();
        assertThat(preview.get("hoursUntilAppointment").asLong()).as("hours quoted to the customer").isBetween(29L, 30L);
    }

    /**
     * The two zones are 24 hours apart, so two bookings written with the <em>same</em> wall clock are
     * a day apart on the line. Read in UTC both answers are identical — which is the defect stated as
     * one number.
     *
     * <p>Not exactly 24 because {@code hoursUntilAppointment} truncates and the two requests read the
     * clock a few milliseconds apart; 23 is the only other value it can take, and the spelling this
     * replaced gives 0.
     */
    @Test
    @Transactional
    @DisplayName("the same wall clock in two zones is a day apart, not the same moment")
    void theSameWallClockIsNotTheSameMoment() throws Exception {
        long east = preview(booking("b-zone-pair-east", EAST, 40)).get("hoursUntilAppointment").asLong();
        long west = preview(booking("b-zone-pair-west", WEST, 40)).get("hoursUntilAppointment").asLong();

        assertThat(west - east).as("Kiritimati is 14h ahead of UTC and Honolulu 10h behind").isBetween(23L, 24L);
    }
}
