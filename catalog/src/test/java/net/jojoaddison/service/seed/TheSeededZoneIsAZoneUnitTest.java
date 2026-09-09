package net.jojoaddison.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * The catalogue's own answer to backlog NEW-20 — {@code decisions.md} D60.
 *
 * <p>NEW-20 says the question belongs to catalog as well as to booking, because a zone written here
 * is what a booking copies and what the public profile renders. It gets a smaller answer than
 * booking's, for one reason that is a fact rather than a preference: <strong>catalog has no write
 * path for {@code Professional.zoneId} at all.</strong> The generated {@code ProfessionalResource}
 * is deleted (CLAUDE.md's delete table), {@code ProfessionalService} has no caller anywhere in the
 * service, and {@link CatalogSeeder} is the sole writer of the column. A validating boundary here
 * would be a guard on a door that does not exist — untested by construction, and drifting from
 * whatever door is eventually built.
 *
 * <p>So what is checkable today is the one live writer, and this is it: the constant it writes must
 * be a zone tzdb can read. That is one typo away from being false, and the typo would reach
 * {@code booking.zone_id} on every seeded estate.
 *
 * <p><strong>This does not make booking's parse redundant, and must not be read as covering it.</strong>
 * D22's rule is that booking establishes for itself anything it is going to trust with money, and
 * the late-cancellation boundary is read from booking's own column. A test in catalog is evidence
 * about catalog's seeder, not a guarantee about a value in flight.
 */
class TheSeededZoneIsAZoneUnitTest {

    @Test
    void theSoleWriterOfAProfessionalsZoneWritesAZoneThatCanBeRead() {
        assertThatCode(() -> ZoneId.of(CatalogSeeder.DEFAULT_ZONE_ID)).doesNotThrowAnyException();
        assertThat(CatalogSeeder.DEFAULT_ZONE_ID).isEqualTo("Africa/Accra");
    }
}
