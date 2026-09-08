package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import net.jojoaddison.domain.Booking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whose clock an appointment keeps — {@code decisions.md} D58, backlog NEW-19.
 *
 * <p>{@link BookingWorkflow#scheduledAt} converted an appointment's wall clock with {@code
 * ZoneOffset.UTC} and never read {@code Booking.zoneId}. Spec §13 #8's ratification (D55) made that a
 * defect: an appointment belongs to the professional's calendar, and the booking's own column is
 * where that calendar was captured.
 *
 * <h2>Why the zones here are not Accra, and why there are two of them</h2>
 *
 * <p>Every zone in every estate is {@code Africa/Accra}, which is GMT with no offset and no DST — so
 * a booking in Accra <strong>cannot tell</strong> an implementation that reads its zone from one that
 * inherits UTC. Neither can one zone on its own: at some hours an eastward zone agrees with UTC's
 * verdict by accident. So the fixtures sit on both sides, and each asserts a verdict that the UTC
 * spelling gets <em>backwards</em> rather than merely a different instant.
 *
 * <p>{@code Pacific/Kiritimati} (+14) and {@code Pacific/Honolulu} (-10) observe no daylight saving
 * and never have, so the instants asserted below cannot move under a tzdb update — which a zone with
 * DST rules could do without anything here being wrong.
 *
 * <p>The clock is a fixed {@code Instant} in 2026 passed to {@code isLate}, never a real one, and the
 * appointments are anchored dates rather than offsets from now: nothing here can agree with a wrong
 * implementation by coincidence.
 */
class TheAppointmentIsInTheBookingsZoneTest {

    private static final int WINDOW_HOURS = 24;

    /** Collaborators are unused by isLate and scheduledAt; passing null keeps this a unit test. */
    private final BookingWorkflow workflow = new BookingWorkflow(null, null, null, WINDOW_HOURS);

    /** 09:00 UTC on the 25th. The window therefore closes at 09:00 UTC on the 26th. */
    private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");

    private static Booking on(String date, String time, String zone) {
        return new Booking().reference("b-zone").scheduledDate(LocalDate.parse(date)).scheduledTime(LocalTime.parse(time)).zoneId(zone);
    }

    @Test
    @DisplayName("an appointment east of UTC happens 14 hours before the same wall clock in UTC")
    void eastOfUtc() {
        Booking booking = on("2026-08-26", "20:00", "Pacific/Kiritimati");
        assertThat(workflow.scheduledAt(booking)).isEqualTo(Instant.parse("2026-08-26T06:00:00Z"));
    }

    @Test
    @DisplayName("an appointment west of UTC happens 10 hours after the same wall clock in UTC")
    void westOfUtc() {
        Booking booking = on("2026-08-26", "05:00", "Pacific/Honolulu");
        assertThat(workflow.scheduledAt(booking)).isEqualTo(Instant.parse("2026-08-26T15:00:00Z"));
    }

    /**
     * 20:00 on the 26th is 35 hours away read in UTC and 21 hours away read in Kiritimati, so the
     * fee applies in the booking's own calendar and does not in the estate's old spelling. The
     * verdict is the whole quantity a customer is charged on, which is why this asserts it and not
     * only the instant above.
     */
    @Test
    @DisplayName("east of UTC: late in the booking's zone, and not late read as UTC")
    void theVerdictFollowsTheZoneEastward() {
        assertThat(workflow.isLate(on("2026-08-26", "20:00", "Pacific/Kiritimati"), NOW)).as("21h out in Kiritimati").isTrue();
        assertThat(workflow.isLate(on("2026-08-26", "20:00", "UTC"), NOW)).as("35h out read as UTC").isFalse();
    }

    /**
     * The same test pointing the other way, and the direction that costs a customer money: 05:00 on
     * the 26th is 20 hours away in UTC — inside the window, a 50% fee — and 30 hours away in
     * Honolulu, which is free.
     */
    @Test
    @DisplayName("west of UTC: free in the booking's zone, and late read as UTC")
    void theVerdictFollowsTheZoneWestward() {
        assertThat(workflow.isLate(on("2026-08-26", "05:00", "Pacific/Honolulu"), NOW)).as("30h out in Honolulu").isFalse();
        assertThat(workflow.isLate(on("2026-08-26", "05:00", "UTC"), NOW)).as("20h out read as UTC").isTrue();
    }

    /**
     * Accra is the answer for every booking in every estate today and is <em>not</em> evidence of
     * anything on its own — it is here so that the fix is pinned as behaviour-preserving for the
     * 298 rows that exist, which is the reason D58 could be taken while nothing is deployed.
     */
    @Test
    @DisplayName("Africa/Accra reads exactly as UTC did, which is why today's estate cannot see this")
    void accraIsUnchanged() {
        assertThat(workflow.scheduledAt(on("2026-08-26", "07:00", "Africa/Accra"))).isEqualTo(Instant.parse("2026-08-26T07:00:00Z"));
    }

    /**
     * {@code zone_id} is not-null with no column default and nothing in this service has ever
     * written anything but {@code Africa/Accra}, so neither of these is reachable today. The column
     * is free text all the same, and {@code ZoneId.of} throws on a name tzdb does not know — which
     * without the guard would make one unreadable row a 500 on both {@code /cancellation-preview}
     * and {@code /cancel}, leaving a booking nobody can cancel.
     */
    @Test
    @DisplayName("a zone that cannot be read falls back to the marketplace's calendar rather than throwing")
    void anUnreadableZoneFallsBack() {
        Instant expected = Instant.parse("2026-08-26T07:00:00Z");
        assertThat(workflow.scheduledAt(on("2026-08-26", "07:00", "Mars/Olympus"))).as("not a tzdb name").isEqualTo(expected);
        assertThat(workflow.scheduledAt(on("2026-08-26", "07:00", "  "))).as("blank").isEqualTo(expected);
        assertThat(workflow.scheduledAt(on("2026-08-26", "07:00", null))).as("null").isEqualTo(expected);
    }
}
