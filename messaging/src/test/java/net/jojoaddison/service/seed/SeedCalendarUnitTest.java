package net.jojoaddison.service.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The calendar every seeded date is shifted in — {@code decisions.md} D48.
 *
 * <p>Copied byte-identically into catalog, booking, messaging and payout beside {@link SeedCalendar}
 * itself, and diffed by CI, because what these tests pin is that <strong>the four services agree</strong>.
 * A known answer asserted in one service proves nothing about the other three; asserted identically
 * in all four, it is the closest thing this repository has to a cross-service contract.
 *
 * <p>Every test here supplies its own {@link Clock}. The seeders read the time in exactly one place
 * and this is the seam into it — without one there is no way to stand at Accra's midnight, and a fix
 * to a date defect that can only be tested by being run at the right hour is not a fix.
 */
class SeedCalendarUnitTest {

    /** {@code $meta.demoToday} in {@code deploy/demo/seed-data.json}. */
    private static final LocalDate DEMO_TODAY = LocalDate.of(2026, 8, 10);

    /**
     * <strong>The defect, stated as a measurement.</strong> Two instants on the same Accra day, twelve
     * hours apart, must produce the same shift — because the shift is what every seeded date moves by,
     * and two services seeding either side of noon must write dates that line up.
     *
     * <p>The JVM default is forced east of UTC for the duration, which is what makes this fail against
     * the line it replaced. At 23:40 UTC on the 5th it is already 01:40 on the <em>6th</em> in central
     * Europe, so {@code LocalDate.now()} answers the 6th and the shift comes back one larger than it
     * did at noon on the same day. That is a one-day disagreement between whichever services happened
     * to seed either side of 22:00 UTC.
     */
    @Test
    @DisplayName("the same Accra day shifts the same, whatever hour the seed is loaded at")
    void theSameDayShiftsTheSameWhateverHourTheSeedIsLoadedAt() {
        underDefaultZone("Europe/Berlin", () -> {
            long atNoon = SeedCalendar.shiftDays(DEMO_TODAY, false, at("2026-09-05T12:00:00Z"));
            long lateEvening = SeedCalendar.shiftDays(DEMO_TODAY, false, at("2026-09-05T23:40:00Z"));

            assertThat(lateEvening).as("a seed loaded at 23:40 in Accra is the same day as one loaded at noon").isEqualTo(atNoon);
            assertThat(atNoon).isEqualTo(26);
        });
    }

    /**
     * <strong>The zone is named, not inherited</strong> — the eastward end of the day.
     *
     * <p>23:40 in Accra is already the 6th in every zone east of UTC, so an implementation reading the
     * JVM default answers {@code 2026-09-06} here. The default is forced to one, so the assertion
     * cannot be satisfied by the machine happening to be on GMT — which is the whole reason the line
     * this replaced survived: it is correct on every machine in this estate and in CI.
     */
    @Test
    @DisplayName("late in Accra's evening is still Accra's day, under an eastward default zone")
    void lateEveningIsStillAccrasDay() {
        underDefaultZone("Pacific/Kiritimati", () -> assertThat(SeedCalendar.today(at("2026-09-05T23:40:00Z"))).isEqualTo(LocalDate.of(2026, 9, 5)));
    }

    /**
     * The same statement from the other end, and it is a separate test deliberately.
     *
     * <p>02:30 in Accra is still the 4th far enough west, so this is red against a JVM default that is
     * behind UTC exactly as its partner is red against one ahead. Between them nothing but a stated
     * zone passes. They are two tests rather than two assertions in one because the first assertion to
     * fail hides the second, and a guard nobody can watch fire is the class of test D47's review spent
     * its time on.
     *
     * <p><strong>Its clock is westward too, and that is the second dimension.</strong> There are two
     * zones an implementation can wrongly inherit — the JVM's, which {@link #underDefaultZone} brackets
     * from both ends, and the <em>clock's own</em>, which {@code LocalDate.now(clock)} would read. A
     * clock fixed to {@code Europe/Berlin} is east of Accra, so it catches the eastward case and leaves
     * this one green: at 02:30 UTC a Berlin clock says 04:30 on the same day and agrees by accident.
     * Fixed here to a zone behind UTC, {@code LocalDate.now(clock)} answers the 4th and this goes red —
     * so the pair now brackets the clock's zone as well as the JVM's.
     */
    @Test
    @DisplayName("early in Accra's morning is still Accra's day, under a westward default zone")
    void earlyMorningIsStillAccrasDay() {
        underDefaultZone(
            "America/New_York",
            () -> assertThat(SeedCalendar.today(at("2026-09-05T02:30:00Z", "America/New_York"))).isEqualTo(LocalDate.of(2026, 9, 5))
        );
    }

    /**
     * And the zone the two instants above bracket is spelled {@code Africa/Accra}.
     *
     * <p>Honest about what it is: a spelling check. No observation can separate {@code Africa/Accra}
     * from {@code UTC}, because they have never differed and Ghana has no daylight saving — so the
     * only way to pin <em>which</em> of the two was chosen is to read the constant back. D47's badge
     * tests carry the same assertion for the same reason.
     */
    @Test
    @DisplayName("the calendar is the estate's, spelled out")
    void theCalendarIsTheEstates() {
        assertThat(SeedCalendar.SEED_ZONE).isEqualTo(ZoneId.of("Africa/Accra"));
    }

    /**
     * Anchoring does not consult the clock at all — the property means "load the dates exactly as
     * written", and a shift of zero computed from a clock is not the same promise as no shift.
     *
     * <p>This is what the quality box runs on all four services, and it is why the defect this class
     * fixes was latent there rather than live.
     */
    @Test
    @DisplayName("anchored dates ignore the clock entirely")
    void anchoredDatesIgnoreTheClock() {
        assertThat(SeedCalendar.shiftDays(DEMO_TODAY, true, at("2031-04-01T09:00:00Z"))).isZero();
    }

    /**
     * The production entry point really does go through the seam, rather than the seam being a second
     * implementation the tests exercise and the seeders never reach.
     *
     * <p>Read either side of the call and asserted between, because the answer moves at Accra midnight
     * and a test about midnight should not contain one. Same arrangement D47 applied to
     * {@code thePublicDateIsADate} after it flaked for a millisecond a day.
     */
    @Test
    @DisplayName("the no-clock overload is the seam with a system clock in it")
    void theNoClockOverloadIsTheSeamWithASystemClock() {
        long before = ChronoUnit.DAYS.between(DEMO_TODAY, LocalDate.now(SeedCalendar.SEED_ZONE));
        long actual = SeedCalendar.shiftDays(DEMO_TODAY, false);
        long after = ChronoUnit.DAYS.between(DEMO_TODAY, LocalDate.now(SeedCalendar.SEED_ZONE));

        assertThat(actual).isBetween(before, after);
    }

    /** A clock east of Accra — the default, because the eastward window is the one that was live. */
    private static Clock at(String instant) {
        return at(instant, "Europe/Berlin");
    }

    private static Clock at(String instant, String clockZone) {
        // The clock's own zone is deliberately NOT Accra: SeedCalendar must take the instant from it
        // and the calendar from itself, and a clock already carrying the right zone could not tell
        // the difference between an implementation that does that and one that does not. Which SIDE
        // of Accra it sits on is the caller's choice, because an implementation reading the clock's
        // zone is only caught by a clock on the failing side — see earlyMorningIsStillAccrasDay.
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
