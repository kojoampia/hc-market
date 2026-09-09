package net.jojoaddison.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Booking's second writer of {@code zone_id} — {@code decisions.md} D60, backlog NEW-20.
 *
 * <p>{@link net.jojoaddison.service.CapturedZone} parses what the catalogue offers, and that covers
 * {@code POST /api/bookings}. {@link BookingSeeder} is the other writer of the same column, and it
 * writes a compile-time constant rather than anything off a wire — so it needs a constant that
 * parses, not a boundary. This is that check, and it is the reason the CI sweep beside it may allow
 * a {@code .zoneId(DEFAULT_ZONE_ID)} line without allowing an unparsed one.
 *
 * <p>Deliberately not routed through {@code CapturedZone}: a seeder does not answer HTTP, and
 * putting a 502 in its path would express nothing except that the constant is still spelled right,
 * which is what this asserts directly.
 */
class TheSeededZoneIsAZoneUnitTest {

    @Test
    void theSeededBookingZoneIsAZoneThatCanBeRead() {
        assertThatCode(() -> ZoneId.of(BookingSeeder.DEFAULT_ZONE_ID)).doesNotThrowAnyException();
        assertThat(BookingSeeder.DEFAULT_ZONE_ID).isEqualTo("Africa/Accra");
    }
}
