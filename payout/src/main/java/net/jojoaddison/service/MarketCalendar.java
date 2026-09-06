package net.jojoaddison.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * What day it is, on the marketplace's own calendar — {@code decisions.md} D51.
 *
 * <p>An {@link java.time.Instant} is a point on the line and carries no calendar; a
 * {@link LocalDate} is a day and cannot exist without one. Every step between the two picks a zone,
 * and there is no such thing as picking one silently — only picking one and not writing it down
 * (D47). This is where it is written down for everything that happens at <em>run time</em>.
 *
 * <h2>What it decides</h2>
 *
 * <ul>
 *   <li><strong>payout</strong> — {@code ledger.earned_on}, on all three paths that write it: a
 *       completed booking, a late-cancellation fee and a dispute reversal. And the "today" the
 *       month-to-date slice is measured from, on both the earnings and the overview endpoints.
 *   <li><strong>booking</strong> — the default first day of the professional's schedule window.
 * </ul>
 *
 * <h2>Africa/Accra, and why it is not the professional's zone</h2>
 *
 * <p>{@code MARKET_ZONE} is the brokerage's calendar, which is the same choice catalog's
 * {@code MarketplaceService.BADGE_ZONE} makes for a verification date and the seeders' {@code
 * SeedCalendar.SEED_ZONE} makes for the shift — three constants, three different questions, one
 * answer, and they are deliberately not one shared thing (D51).
 *
 * <p>D21 gives a <em>professional's</em> zone the wall clock of an <strong>appointment</strong>,
 * because that is where the service is delivered. An earning is not delivered anywhere: it is the
 * brokerage writing its books, which D21 puts squarely in its other category beside {@code raisedAt}
 * and {@code completedAt}. Two further reasons it could not be the professional's zone even if the
 * category were arguable. Payout has no professional zone to read — {@code Ledger} carries none, and
 * asking another service for one on every write would make a ledger row depend on a round trip. And
 * a rendered "today" must be read in the <strong>same calendar as the column it slices</strong>, or
 * a month-to-date total is bounded in one calendar over rows dated in another, which is the
 * seeder-versus-consumer disagreement this class exists to end, rebuilt on the read side.
 *
 * <p>What that means near midnight, stated rather than discovered: a session completed at 23:30 UTC
 * is earned on that day and not the next, wherever the container is started; and on a month's last
 * day it stays in the month it was completed in rather than moving into the next one and vanishing
 * from a month-to-date tile. That was the defect. Ghana is UTC+0 all year and has never observed
 * daylight saving, so the <em>value</em> has not changed on any machine this estate has ever run on —
 * what changed is that it is stated, and can no longer be moved by a {@code TZ} line somebody adds
 * to a compose file without thinking about the ledger.
 *
 * <h2>Why this is a bean, when SeedCalendar is a static</h2>
 *
 * <p>Because the defect is on a <strong>write</strong> path, and the only way to stand at 23:30 UTC
 * while a ledger row is being written is to hand the writer its clock. {@link #at(Clock)} is that
 * seam, so the tests assert on {@code earned_on} itself rather than on a helper the writer might not
 * be using. {@code SeedCalendar} could keep its static shape because a seeder's whole output is one
 * number and the seam sits inside it.
 *
 * <p>Only the <strong>instant</strong> is ever taken from the clock. The calendar is always
 * {@link #MARKET_ZONE}, so a clock carrying {@code systemDefaultZone()} changes nothing here — which
 * is why the tests fix their clocks to zones that are not Accra, and to zones on both sides of it: a
 * clock already holding the right zone cannot tell an implementation that names one from an
 * implementation that merely inherits one.
 *
 * <h2>Copied, and diffed</h2>
 *
 * <p>There is no shared library here — five standalone Maven projects with no aggregator pom — so
 * this file and its test are <strong>copied byte-identically into payout and booking</strong>, and
 * CI diffs the copies, exactly as {@code SubjectPseudonym} (D35) and {@code SeedCalendar} (D48) are.
 * Edit one and you must edit the other, comments included. CI also asserts that this file and
 * {@code SeedCalendar} in the same service name the <em>same</em> zone, because a service whose seed
 * and whose runtime disagree about the calendar is NEW-10 itself and neither diff would see it.
 */
@Component
public class MarketCalendar {

    /**
     * The marketplace's calendar.
     *
     * <p>Indistinguishable from {@code UTC} by observation, and always will be, so the name is the
     * whole point: it says a calendar was chosen rather than leaving a reader to discover that one
     * was not.
     */
    public static final ZoneId MARKET_ZONE = ZoneId.of("Africa/Accra");

    private final Clock clock;

    /**
     * The one Spring uses, and the only public constructor, so there is nothing for the container to
     * choose between.
     *
     * <p>{@link Clock#systemUTC()} rather than {@code systemDefaultZone()}: the JVM's zone is
     * precisely what must not reach this class, and a clock carrying it would put the defect back
     * one indirection away from where anybody would look for it.
     */
    public MarketCalendar() {
        this(Clock.systemUTC());
    }

    private MarketCalendar(Clock clock) {
        this.clock = clock;
    }

    /**
     * A calendar pinned to a fixed clock — the seam a test stands at Accra's midnight through.
     *
     * <p>Safe to hand any clock, including one carrying the JVM's zone: {@link #today()} reads the
     * instant and nothing else.
     */
    public static MarketCalendar at(Clock clock) {
        return new MarketCalendar(clock);
    }

    /** Today, in {@link #MARKET_ZONE}. The instant comes from the clock; the calendar never does. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), MARKET_ZONE);
    }
}
