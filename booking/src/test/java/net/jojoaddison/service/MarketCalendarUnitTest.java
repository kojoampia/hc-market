package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The calendar every runtime date is read in — {@code decisions.md} D51.
 *
 * <p>Copied byte-identically into payout and booking beside {@link MarketCalendar} itself, and
 * diffed by CI, because what these tests pin is that <strong>the two services agree</strong>. A
 * known answer asserted in one proves nothing about the other; asserted identically in both, it is
 * the same cross-service contract {@code SeedCalendarUnitTest} is for the seed.
 *
 * <p>Every test here supplies its own {@link Clock}. Without one there is no way to stand at Accra's
 * midnight, and a fix to a date defect that can only be tested by being run at the right hour is not
 * a fix — it is a coincidence with a test beside it.
 */
class MarketCalendarUnitTest {

    /**
     * <strong>The zone is named, not inherited</strong> — the eastward end of the day.
     *
     * <p>23:30 in Accra is already tomorrow in every zone east of UTC, so an implementation reading
     * the JVM default answers the 6th here. The default is forced to one, so the assertion cannot be
     * satisfied by the machine happening to be on GMT — which is the whole reason {@code
     * LocalDate.now()} survived in three ledger writes and two renders: it is correct on every
     * machine in this estate and in CI, and wrong on the workstation this was written on for two
     * hours every summer evening.
     */
    @Test
    @DisplayName("late in Accra's evening is still Accra's day, under an eastward default zone")
    void lateEveningIsStillAccrasDay() {
        underDefaultZone("Pacific/Kiritimati", () -> assertThat(MarketCalendar.at(at("2026-09-05T23:30:00Z")).today()).isEqualTo(LocalDate.of(2026, 9, 5)));
    }

    /**
     * The same statement from the other end, and it is a separate test deliberately.
     *
     * <p>02:30 in Accra is still yesterday far enough west, so this is red against a JVM default
     * behind UTC exactly as its partner is red against one ahead. Between them nothing but a stated
     * zone passes. Two tests rather than two assertions in one, because the first assertion to fail
     * hides the second and a guard nobody can watch fire is worth very little.
     *
     * <p><strong>Its clock is westward too, and that is the second dimension.</strong> There are two
     * zones an implementation can wrongly inherit — the JVM's, which {@link #underDefaultZone}
     * brackets from both ends, and the <em>clock's own</em>, which {@code LocalDate.now(clock)} would
     * read. A clock east of Accra catches only the eastward case: at 02:30 UTC a Berlin clock says
     * 04:30 on the same day and agrees with Accra by accident. Fixed here to a zone behind UTC,
     * {@code LocalDate.now(clock)} answers the 4th and this goes red.
     */
    @Test
    @DisplayName("early in Accra's morning is still Accra's day, under a westward default zone")
    void earlyMorningIsStillAccrasDay() {
        underDefaultZone(
            "America/New_York",
            () -> assertThat(MarketCalendar.at(at("2026-09-05T02:30:00Z", "America/New_York")).today()).isEqualTo(LocalDate.of(2026, 9, 5))
        );
    }

    /**
     * And the zone the two instants above bracket is spelled {@code Africa/Accra}.
     *
     * <p>Honest about what it is: a spelling check. No observation can separate {@code Africa/Accra}
     * from {@code UTC}, because they have never differed and Ghana has no daylight saving — so the
     * only way to pin <em>which</em> of the two was chosen is to read the constant back. D47's badge
     * tests and D48's seed tests carry the same assertion for the same reason.
     */
    @Test
    @DisplayName("the calendar is the marketplace's, spelled out")
    void theCalendarIsTheMarketplaces() {
        assertThat(MarketCalendar.MARKET_ZONE).isEqualTo(ZoneId.of("Africa/Accra"));
    }

    /**
     * The bean Spring builds really does go through the same reading, rather than the fixed-clock
     * factory being a second implementation the tests exercise and nothing else reaches.
     *
     * <p>Read either side of the call and asserted between, because the answer moves at Accra
     * midnight and a test about midnight should not contain one. Same arrangement D47 applied to
     * {@code thePublicDateIsADate} after it flaked for a millisecond a day.
     */
    @Test
    @DisplayName("the no-argument constructor is the same calendar with a system clock in it")
    void theNoArgumentConstructorIsTheSameCalendarWithASystemClock() {
        LocalDate before = LocalDate.now(MarketCalendar.MARKET_ZONE);
        LocalDate actual = new MarketCalendar().today();
        LocalDate after = LocalDate.now(MarketCalendar.MARKET_ZONE);

        assertThat(actual).isBetween(before, after);
    }

    /**
     * A clock's own zone is never read, which is what makes {@link MarketCalendar#at(Clock)} safe to
     * hand anything — including the one thing that must never decide a date here.
     *
     * <p>Three clocks on the same instant in three zones, one of them the JVM's, all answering the
     * same day. Red against any implementation that takes the calendar from the clock.
     */
    @Test
    @DisplayName("only the instant comes from the clock, never its zone")
    void onlyTheInstantComesFromTheClock() {
        Instant instant = Instant.parse("2026-09-05T23:30:00Z");

        assertThat(MarketCalendar.at(Clock.fixed(instant, ZoneId.of("Pacific/Kiritimati"))).today())
            .isEqualTo(MarketCalendar.at(Clock.fixed(instant, ZoneId.of("America/New_York"))).today())
            .isEqualTo(MarketCalendar.at(Clock.fixed(instant, ZoneId.systemDefault())).today())
            .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    /** A clock east of Accra — the default, because the eastward window is the one that was live. */
    private static Clock at(String instant) {
        return at(instant, "Europe/Berlin");
    }

    private static Clock at(String instant, String clockZone) {
        // The clock's own zone is deliberately NOT Accra: MarketCalendar must take the instant from
        // it and the calendar from itself, and a clock already carrying the right zone could not
        // tell the difference between an implementation that does that and one that does not. Which
        // SIDE of Accra it sits on is the caller's choice, because an implementation reading the
        // clock's zone is only caught by a clock on the failing side — see earlyMorningIsStillAccrasDay.
        return Clock.fixed(Instant.parse(instant), ZoneId.of(clockZone));
    }

    private static void underDefaultZone(String zone, Runnable assertion) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            assertion.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
