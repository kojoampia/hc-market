package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D17's v1, asserted — {@code decisions.md} D87, backlog NEW-45.
 *
 * <p>The professional supplies their own link, the platform relays it, and it is revealed an hour
 * before. Four things have to be true and each is a separate decision, so each is a separate test: an
 * aggregate "the link came back" would pass for a rule that checked one of them.
 */
class TheMeetingLinkIsRevealedAnHourBeforeUnitTest {

    private static final String LINK = "https://meet.example.test/abc-defg-hij";
    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");

    /** 10:00 on 2026-10-01 in Accra. */
    private static final Instant SESSION = LocalDate.of(2026, 10, 1).atTime(LocalTime.of(10, 0)).atZone(ACCRA).toInstant();

    private static Booking booking(DeliveryMode mode, BookingStatus status, String link) {
        Booking b = new Booking();
        b.setScheduledDate(LocalDate.of(2026, 10, 1));
        b.setScheduledTime(LocalTime.of(10, 0));
        b.setZoneId(ACCRA.getId());
        b.setDeliveryMode(mode);
        b.setStatus(status);
        b.setMeetingLink(link);
        return b;
    }

    private static BookingWorkflow workflow() {
        return new BookingWorkflow(null, null, null, 24);
    }

    @Test
    @DisplayName("an hour before the session, and after, the link is revealed")
    void revealedFromAnHourBefore() {
        Booking b = booking(DeliveryMode.ONLINE, BookingStatus.CONFIRMED, LINK);

        assertThat(workflow().meetingLinkFor(b, SESSION.minus(Duration.ofHours(1)))).contains(LINK);
        assertThat(workflow().meetingLinkFor(b, SESSION.minus(Duration.ofMinutes(30)))).contains(LINK);
        // STAYS revealed after the start: a customer who joins late, or whose call drops, needs it more
        // than one who is early. Deliberate, not an oversight in the arithmetic.
        assertThat(workflow().meetingLinkFor(b, SESSION.plus(Duration.ofMinutes(45)))).contains(LINK);
    }

    @Test
    @DisplayName("before that hour it is withheld, and the boundary is exact")
    void withheldUntilTheHour() {
        Booking b = booking(DeliveryMode.ONLINE, BookingStatus.CONFIRMED, LINK);

        assertThat(workflow().meetingLinkFor(b, SESSION.minus(Duration.ofHours(2)))).isEmpty();
        // One second before the window opens. The boundary is asserted on both sides because
        // `isBefore` is the whole rule, and an off-by-one here is an hour of a promise not kept.
        assertThat(workflow().meetingLinkFor(b, SESSION.minus(Duration.ofHours(1)).minusSeconds(1))).isEmpty();
    }

    /**
     * A link on an in-person booking is somebody's mistake, and relaying it would send a customer to a
     * video call for a home visit. Refused on the delivery mode, so the mistake cannot leak.
     */
    @Test
    @DisplayName("only an ONLINE booking reveals a link, whatever is stored on the others")
    void onlyOnlineReveals() {
        for (DeliveryMode mode : new DeliveryMode[] { DeliveryMode.IN_PERSON, DeliveryMode.HOME_VISIT }) {
            Booking b = booking(mode, BookingStatus.CONFIRMED, LINK);
            assertThat(workflow().meetingLinkFor(b, SESSION.minus(Duration.ofMinutes(10)))).as("%s", mode).isEmpty();
        }
        assertThat(workflow().meetingLinkFor(booking(DeliveryMode.ONLINE, BookingStatus.CONFIRMED, LINK), SESSION)).contains(LINK);
    }

    /**
     * The case the prototype does not speak to, decided conservatively: a cancelled booking has no
     * session, so a revealed link to a room nobody will be in is worse than a customer having to ask.
     *
     * <p>Asserted over EVERY status rather than the two that are live, so a status added later has to
     * decide for itself instead of inheriting "yes".
     */
    @Test
    @DisplayName("only a live booking reveals a link, and every status is checked")
    void onlyALiveBookingReveals() {
        for (BookingStatus status : BookingStatus.values()) {
            Booking b = booking(DeliveryMode.ONLINE, status, LINK);
            boolean live = status == BookingStatus.REQUESTED || status == BookingStatus.CONFIRMED;

            assertThat(workflow().meetingLinkFor(b, SESSION))
                .as("%s must %sreveal the link", status, live ? "" : "not ")
                .isEqualTo(live ? java.util.Optional.of(LINK) : java.util.Optional.empty());
        }
    }

    @Test
    @DisplayName("no link is the normal case and not a failure")
    void noLinkIsNormal() {
        assertThat(workflow().meetingLinkFor(booking(DeliveryMode.ONLINE, BookingStatus.CONFIRMED, null), SESSION)).isEmpty();
        assertThat(workflow().meetingLinkFor(booking(DeliveryMode.ONLINE, BookingStatus.CONFIRMED, "  "), SESSION)).isEmpty();
    }

    /**
     * THE ZONE IS THE BOOKING'S OWN, not the marketplace's — D58's ratification, which behaves
     * identically today and is wrong for the one case it exists for.
     *
     * <p>Driven with a booking in a zone that is NOT Accra, so the two answers differ: at 10:00 in
     * Sao_Paulo (UTC-3) the session is 13:00 UTC, and an instant 30 minutes before that is still two
     * and a half hours before the same wall clock read in Accra. A rule using MARKET_ZONE would withhold
     * the link here.
     */
    @Test
    @DisplayName("the hour is measured in the booking's own zone, not the marketplace's")
    void theHourIsInTheBookingsZone() {
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
        Booking b = booking(DeliveryMode.ONLINE, BookingStatus.CONFIRMED, LINK);
        b.setZoneId(saoPaulo.getId());
        Instant sessionThere = LocalDate.of(2026, 10, 1).atTime(LocalTime.of(10, 0)).atZone(saoPaulo).toInstant();

        assertThat(workflow().meetingLinkFor(b, sessionThere.minus(Duration.ofMinutes(30))))
            .as("half an hour before the session in its OWN zone")
            .contains(LINK);
        assertThat(workflow().meetingLinkFor(b, SESSION.minus(Duration.ofMinutes(30))))
            .as("half an hour before the same wall clock in Accra is hours early there, so still withheld")
            .isEmpty();
    }
}
