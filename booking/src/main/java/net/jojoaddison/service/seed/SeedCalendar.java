package net.jojoaddison.service.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * How far every seeded date moves, and whose calendar decides it — {@code decisions.md} D48.
 *
 * <p>The seed file is written against one fixed demo day, {@code $meta.demoToday}. Unless
 * {@code healthconnect.seed.anchor-dates} is set, every date in it is shifted by
 * {@code today - demoToday}, so a demo run months later still shows "tomorrow" and a live
 * month-to-date figure. That is <em>one number</em> per service, computed once, applied to
 * everything that service writes.
 *
 * <h2>Why this is a file and not a line</h2>
 *
 * <p>Four services compute that number independently and their dates have to agree with each other:
 * catalog's {@code availability_slot.slot_date} is read against booking's {@code scheduled_date}, and
 * payout's {@code ledger.earned_on} against booking's {@code completed_at}, which the
 * lifetime-earnings aggregate sums over. A uniform offset in all four is exactly what
 * {@code anchor-dates=false} means and is harmless. A one-day disagreement between two of them is
 * corruption that nothing in this estate detects, because none of the counts move — so the estate
 * stops being seed-exact against itself while every check stays green.
 *
 * <p>There is no shared library here — five standalone Maven projects with no aggregator pom — so
 * this file is <strong>copied byte-identically into catalog, booking, messaging and payout</strong>
 * and CI diffs the four copies. That is the {@code SubjectPseudonym} arrangement (D35) applied to the
 * same class of problem: a derivation whose outputs must match across services that cannot share
 * code. Edit one and you must edit all four, comments included.
 *
 * <h2>The zone is stated, not inherited</h2>
 *
 * <p>{@link #SEED_ZONE} is {@code Africa/Accra}, the estate's calendar — the choice D47 made for the
 * verification badge, for the same reason. The line this replaced read {@code LocalDate.now()}, which
 * takes the JVM default: right in a container with no {@code TZ}, right on a workstation in Accra,
 * and wrong on the workstation this was written on, whose date runs ahead of Accra's from 22:00 UTC
 * to midnight in summer. Ghana is UTC+0 all year and has never observed daylight saving, so the value
 * has not changed. What changed is that it is written down and can no longer be moved by an
 * environment variable nobody was thinking about the seed when they set.
 *
 * <h2>What this does not close</h2>
 *
 * <p>Pinning the zone makes the four agree on <em>which</em> calendar. It does not make them read it
 * at the same moment: they are started in parallel by one {@code compose up} and each evaluates this
 * when its own context is ready, so a boot straddling Accra midnight can still hand two services
 * different days. That residual is seconds wide, dev-only — quality anchors — and deliberately not
 * closed by a shared "today" variable; D48 argues the trade and names what would reopen it.
 */
final class SeedCalendar {

    /**
     * The calendar {@code $meta.demoToday} is expressed in and the shift is measured against.
     *
     * <p>Indistinguishable from {@code UTC} by observation, and always will be, so the name is the
     * whole point: it says a calendar was chosen rather than leaving a reader to discover that one
     * was not.
     */
    static final ZoneId SEED_ZONE = ZoneId.of("Africa/Accra");

    private SeedCalendar() {}

    /**
     * The shift a seeder applies, measured on the wall clock of {@link #SEED_ZONE}.
     *
     * <p>Only the <em>instant</em> is taken from the clock; the calendar is always {@code SEED_ZONE}.
     * That is why this is {@link Clock#systemUTC()} rather than {@code systemDefaultZone()} — the
     * JVM's zone is precisely what must not reach this calculation, and a clock carrying it would put
     * it straight back.
     */
    static long shiftDays(LocalDate demoToday, boolean anchorDates) {
        return shiftDays(demoToday, anchorDates, Clock.systemUTC());
    }

    /**
     * The seam. Package-private and taking a {@link Clock}, so a test can stand at Accra's midnight
     * instead of hoping to be run near one.
     *
     * <p>The seeders take a boolean and no clock, and this is the only place in any of the four that
     * reads the time at all — which is what makes "no seeder reads the clock directly" a grep CI can
     * run.
     */
    static long shiftDays(LocalDate demoToday, boolean anchorDates, Clock clock) {
        return anchorDates ? 0 : ChronoUnit.DAYS.between(demoToday, today(clock));
    }

    /** The seeders' idea of today: the instant on the clock, read in the estate's calendar. */
    static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), SEED_ZONE);
    }
}
