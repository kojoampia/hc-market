package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The zone the public verification date is rendered in — {@code decisions.md} D47.
 *
 * <p>These are the tests that stop the zone being an accident. Ghana is UTC+0 all year, so an
 * implementation that used {@code ZoneId.systemDefault()} produces the right answer on every machine
 * in this estate and the wrong one the day a container is started with a different {@code TZ} — which
 * is the shape of defect that survives every test suite and arrives in production.
 */
class VerificationBadgeDateUnitTest {

    /**
     * <strong>The zone is stated, not inherited.</strong> Run under a default zone that is genuinely
     * a different day at this instant, so the assertion can only pass if the code names one.
     *
     * <p>02:30 UTC on the 14th is still 21:30 on the <em>13th</em> in New York. An implementation
     * reading the JVM default answers {@code 2026-01-13} here.
     */
    @Test
    @DisplayName("the badge date names its zone rather than inheriting the JVM's")
    void badgeDateNamesItsZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThat(MarketplaceService.badgeDate(Instant.parse("2026-01-14T02:30:00Z"))).isEqualTo(LocalDate.of(2026, 1, 14));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * And the zone it names is the desk's — Accra, where BridgeCare reads the documents.
     *
     * <p>23:40 in Accra is already the next day three hours east, so this fails against any zone
     * further east than UTC as well as against the westward ones the test above catches. The two
     * instants bracket the day from both ends, which is the only way a single-zone conversion can be
     * pinned by observation rather than by reading the constant back.
     */
    @Test
    @DisplayName("a review recorded near midnight is dated the day the desk did it")
    void aLateReviewIsDatedTheDayTheDeskDidTheWork() {
        assertThat(MarketplaceService.badgeDate(Instant.parse("2026-01-14T23:40:00Z"))).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(MarketplaceService.BADGE_ZONE).isEqualTo(ZoneId.of("Africa/Accra"));
    }
}
