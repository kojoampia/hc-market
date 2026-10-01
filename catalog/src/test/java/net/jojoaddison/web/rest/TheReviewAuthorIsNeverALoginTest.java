package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A review's public author is never an account login — NEW-81, {@code decisions.md} D104.
 *
 * <p><strong>The live path is the one where {@code customerName} is PRESENT</strong>, and that is why
 * every case here drives the resource rather than testing a helper. NEW-81 read the defect as the
 * {@code == null ? login} fallback; booking's {@code CustomerBookingResource} writes
 * {@code request.customerName() == null || isBlank() ? login : request.customerName()}, so a booking
 * created without a display name stores the <em>login</em> in {@code customerName} and catalog receives
 * it non-null. The null branch is unreachable from {@code POST /api/bookings}, so a fix to that branch
 * alone changes nothing at all while every assertion written to the item's own description passes.
 * {@link #theLoginLaunderedAsADisplayNameIsNotPublished()} is the case that says so: it is red against
 * the code this test was written over.
 *
 * <p><strong>The control is not decoration.</strong> A resource that redacted every author would
 * satisfy all four refusal cases, and the 63 seeded reviews carry real names a reader is meant to see —
 * so {@link #aGenuineDisplayNameIsPublishedUnchanged()} and
 * {@link #aGenuineDisplayNameStillYieldsItsOwnInitials()} pin the other direction.
 *
 * <p><strong>The expected values are literals rather than the constants that produce them.</strong>
 * There is deliberately no endpoint to edit or delete a review, so every value this resource stores is
 * permanent and uncorrectable — D52's property, applied to something that identifies a person. Pinning
 * the literal makes re-wording the label red, which is the point: it forces whoever re-words it to
 * notice that the rows already written cannot be re-worded with it.
 *
 * <p>The assertions are on the {@link Review} handed to the repository, for the same reason
 * {@code ReviewIsPublishedOnTheMarketplacesDayTest} gives: a resource that has the rule available and
 * does not call it is red here, which a passing test of the rule on its own cannot see.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TheReviewAuthorIsNeverALoginTest {

    /** The workstation's own test login, which is what p1's profile renders today. */
    private static final String LOGIN = "kojo.ampia.addison";

    /** What a reviewer with no supplied display name is called. */
    private static final String ANONYMOUS = "A BridgeCare customer";

    /** What an ERASED reviewer is called — a different state, and it must stay a different value. */
    private static final String ERASED = "[erased]";

    @AfterEach
    void clearTheSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ the four refusal cases --

    @Test
    @DisplayName("a login laundered into customerName is not published as the author's name")
    void theLoginLaunderedAsADisplayNameIsNotPublished() {
        Review written = publishReviewedBy(LOGIN);

        assertThat(written.getAuthorName()).isNotEqualTo(LOGIN).doesNotContain(LOGIN).isEqualTo(ANONYMOUS);
    }

    @Test
    @DisplayName("a customerName differing from the login only in case is still the login")
    void theLoginInAnotherCaseIsStillTheLogin() {
        Review written = publishReviewedBy("Kojo.Ampia.Addison");

        assertThat(written.getAuthorName()).isEqualTo(ANONYMOUS);
    }

    @Test
    @DisplayName("a customerName that is the login with whitespace round it is still the login")
    void theLoginWithWhitespaceIsStillTheLogin() {
        Review written = publishReviewedBy("  " + LOGIN + "\t");

        assertThat(written.getAuthorName()).isEqualTo(ANONYMOUS);
    }

    @Test
    @DisplayName("an absent customerName does not fall back to the login")
    void anAbsentDisplayNameDoesNotFallBackToTheLogin() {
        Review written = publishReviewedBy(null);

        assertThat(written.getAuthorName()).isNotEqualTo(LOGIN).isEqualTo(ANONYMOUS);
    }

    @Test
    @DisplayName("a blank customerName does not fall back to the login")
    void aBlankDisplayNameDoesNotFallBackToTheLogin() {
        Review written = publishReviewedBy("   ");

        assertThat(written.getAuthorName()).isNotEqualTo(LOGIN).isEqualTo(ANONYMOUS);
    }

    // --------------------------------------------------------------------------- the controls --

    @Test
    @DisplayName("a genuine display name is published exactly as it was supplied")
    void aGenuineDisplayNameIsPublishedUnchanged() {
        Review written = publishReviewedBy("Selina Amoah");

        assertThat(written.getAuthorName()).isEqualTo("Selina Amoah");
    }

    // ------------------------------------------------------------------------------- initials --

    @Test
    @DisplayName("no display name means no initials, rather than initials taken from the login")
    void anAnonymousReviewerHasNoInitials() {
        assertThat(publishReviewedBy(LOGIN).getAuthorInitials()).isNull();
        assertThat(publishReviewedBy(null).getAuthorInitials()).isNull();
        assertThat(publishReviewedBy("   ").getAuthorInitials()).isNull();
    }

    @Test
    @DisplayName("a genuine display name still yields its own initials")
    void aGenuineDisplayNameStillYieldsItsOwnInitials() {
        assertThat(publishReviewedBy("Selina Amoah").getAuthorInitials()).isEqualTo("SA");
    }

    // ---------------------------------------------------------------------- the two stand-ins --

    /**
     * "Nobody supplied a name" and "this person has been erased" are two different facts about a
     * review, and a reader of the table or of the wire must be able to tell them apart — the same rule
     * that keeps an unrated professional's {@code rating} null rather than {@code 0.0}. If these two
     * values ever converge, an erasure becomes indistinguishable from a booking made without a name.
     */
    @Test
    @DisplayName("the anonymous author and the erased author are different values")
    void theTwoStandInsDoNotCollapse() {
        assertThat(ANONYMOUS).isNotEqualTo(ERASED);
        assertThat(publishReviewedBy(LOGIN).getAuthorName()).isNotEqualTo(ERASED);
    }

    // ----------------------------------------------------------------------------------------- --

    /**
     * Publishes one review for a booking whose {@code customerName} is as given, and answers the row.
     *
     * <p>The mocks are built per call rather than injected as fields, because three of the cases below
     * publish more than once and {@code verify(reviews).save(…)} is an exactly-once assertion — a
     * shared mock would fail the second publication on its invocation count rather than on anything
     * this test is about.
     */
    private Review publishReviewedBy(String customerName) {
        ReviewRepository reviews = Mockito.mock(ReviewRepository.class);
        MarketplaceQueryRepository marketplace = Mockito.mock(MarketplaceQueryRepository.class);
        BookingClient booking = Mockito.mock(BookingClient.class);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(LOGIN, null, List.of()));
        when(booking.findBooking(anyString(), anyString())).thenReturn(
            Optional.of(new BookingSummary("b-2f8c11a4", LOGIN, customerName, "p1", "COMPLETED", false))
        );
        when(marketplace.findByReference("p1")).thenReturn(Optional.of(new Professional()));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(booking.markReviewed(anyString(), anyString())).thenReturn(true);

        ReviewWriteResource resource = new ReviewWriteResource(reviews, marketplace, booking, new MarketCalendar());
        resource.publish(new ReviewWriteResource.PublishReview("b-2f8c11a4", 5, "Punctual, patient and clear."), "Bearer token");

        ArgumentCaptor<Review> written = ArgumentCaptor.forClass(Review.class);
        Mockito.verify(reviews).save(written.capture());
        Review row = written.getValue();
        // The rest of the row is not this test's subject, but a published date is what every other
        // assertion here rests on being a real write rather than a half-built builder.
        assertThat(row.getPublishedOn()).isNotNull().isAfter(LocalDate.of(2020, 1, 1));
        return row;
    }
}
