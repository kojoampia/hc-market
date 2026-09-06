package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ReviewRepository;
import net.jojoaddison.service.BookingClient;
import net.jojoaddison.service.BookingClient.BookingSummary;
import net.jojoaddison.service.MarketCalendar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A review is published on the marketplace's day — NEW-12, {@code decisions.md} D52.
 *
 * <p><strong>The highest-ranked of catalog's four, and the only stored date among them.</strong>
 * {@code review.published_on} is written once, shown publicly beside a named person's words, and has
 * no endpoint that could ever correct it — there is deliberately no way to edit or delete a review.
 * Nor is there an {@code Instant} stored beside it to reconstruct the right day from, so a row
 * written in the container's calendar instead of the estate's cannot afterwards be told from a right
 * one. That is the same asymmetry D51 gave for {@code ledger.earned_on}, on a column a customer can
 * read.
 *
 * <p>It is also the second writer of a column {@code CatalogSeeder} already writes in
 * {@code SeedCalendar.SEED_ZONE}. One column, two calendars, is NEW-10 itself —
 * {@code SeedAndMarketCalendarsAgreeUnitTest} is the assertion that they stay one calendar, and this
 * is the assertion that this writer uses it.
 *
 * <p>The assertion is on the {@link Review} handed to the repository rather than on
 * {@link MarketCalendar} directly, so it is red against a resource that has the calendar injected and
 * does not call it — which is the mutation a passing helper test cannot see.
 *
 * <p>Bracketed from both sides in two tests, for D48's review's reason: an eastward clock alone would
 * leave the westward half unwatched, because at 02:30 UTC a Berlin clock says 04:30 the same day and
 * agrees with Accra by accident.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewIsPublishedOnTheMarketplacesDayTest {

    /**
     * Dated 2021 deliberately — see {@code MarketCalendarUnitTest}'s class javadoc. Against a date
     * the real clock could also be, a resource that ignores the injected clock and calls
     * {@code LocalDate.now()} would still pass on whichever of the two directions happened to agree
     * with the machine that day. Five years past, both are red on every day of the year.
     */
    private static final Instant LATE_EVENING = Instant.parse("2021-09-05T23:30:00Z");
    private static final Instant EARLY_MORNING = Instant.parse("2021-09-05T02:30:00Z");
    private static final LocalDate ACCRAS_DAY = LocalDate.of(2021, 9, 5);

    @Mock
    private ReviewRepository reviews;

    @Mock
    private MarketplaceQueryRepository marketplace;

    @Mock
    private BookingClient booking;

    @AfterEach
    void clearTheSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a review published late in Accra's evening is dated that day, not tomorrow")
    void lateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> assertThat(publishedOn(LATE_EVENING, "Europe/Berlin")).isEqualTo(ACCRAS_DAY));
    }

    @Test
    @DisplayName("a review published early in Accra's morning is dated that day, not yesterday")
    void earlyMorning() {
        underDefaultZone("America/New_York", () -> assertThat(publishedOn(EARLY_MORNING, "America/New_York")).isEqualTo(ACCRAS_DAY));
    }

    /** Publishes one review and answers with the date the row was actually given. */
    private LocalDate publishedOn(Instant instant, String clockZone) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("yaa.boakye", null, List.of()));
        when(booking.findBooking(anyString(), anyString())).thenReturn(
            Optional.of(new BookingSummary("b-2f8c11a4", "yaa.boakye", "Yaa Boakye", "p1", "COMPLETED", false))
        );
        when(marketplace.findByReference("p1")).thenReturn(Optional.of(new Professional()));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(booking.markReviewed(anyString(), anyString())).thenReturn(true);

        // The clock's own zone sits on the same side of Accra as the test using it, so an
        // implementation reading the clock's zone rather than the calendar's is caught here too.
        ReviewWriteResource resource = new ReviewWriteResource(
            reviews,
            marketplace,
            booking,
            MarketCalendar.at(Clock.fixed(instant, ZoneId.of(clockZone)))
        );
        resource.publish(new ReviewWriteResource.PublishReview("b-2f8c11a4", 5, "Punctual, patient and clear."), "Bearer token");

        ArgumentCaptor<Review> written = ArgumentCaptor.forClass(Review.class);
        org.mockito.Mockito.verify(reviews).save(written.capture());
        return written.getValue().getPublishedOn();
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
