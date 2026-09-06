package net.jojoaddison.service.seed;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.service.MarketCalendar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seed's calendar and the runtime's are the same calendar — NEW-10, {@code decisions.md} D51.
 *
 * <p>This is the assertion NEW-10 actually is, and no other check in this repository can make it.
 * CI diffs {@link SeedCalendar} across the four seeded services and {@link MarketCalendar} across the
 * three that render or write at run time, so each family is held together — but both diffs stay green
 * while one family says {@code Africa/Accra} and the other says {@code UTC}, which is a service whose
 * seeded rows and whose live rows are dated in two calendars.
 *
 * <p><strong>In two services that is literally one column with two writers.</strong> In payout,
 * {@code ledger.earned_on} is written by {@code PayoutSeeder} and by the two event consumers; in
 * catalog, {@code review.published_on} is written by {@code CatalogSeeder} and by
 * {@code ReviewWriteResource} when a customer publishes one (D52). Rows in the same column dated in
 * two calendars is NEW-10 exactly, and no count moves when it happens.
 *
 * <p>It is a unit test rather than a CI grep deliberately. The disagreement is between two Java
 * constants in one build, so the build is where it can be observed rather than inferred from source
 * text — and {@code ./mvnw verify} finds it before a push does.
 *
 * <p>Copied byte-identically into payout, booking and catalog beside {@code MarketCalendarUnitTest},
 * and diffed with it, for the same reason those are: an assertion made in one service says nothing
 * about the others.
 *
 * <p>Lives in {@code net.jojoaddison.service.seed} because {@code SeedCalendar} is package-private
 * and should stay so — the four copies are the seeders' business, and widening a constant's
 * visibility so that a test elsewhere can read it would make the seed calendar look like something
 * the rest of the service may use.
 */
class SeedAndMarketCalendarsAgreeUnitTest {

    @Test
    @DisplayName("the seed and the runtime read the estate's dates in one calendar")
    void theSeedAndTheRuntimeAgree() {
        assertThat(MarketCalendar.MARKET_ZONE)
            .as("a service whose seeded dates and whose live dates are in different calendars is NEW-10 itself")
            .isEqualTo(SeedCalendar.SEED_ZONE);
    }
}
