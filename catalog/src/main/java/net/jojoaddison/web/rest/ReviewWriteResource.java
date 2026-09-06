package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ReviewRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.BookingClient;
import net.jojoaddison.service.BookingClient.BookingSummary;
import net.jojoaddison.service.MarketCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Publishing a review — spec §6 {@code POST /api/reviews}, spec §9's integrity rule.
 *
 * <p>"Rejected unless a COMPLETED booking backs it — for that customer and professional,
 * unreviewed." Four conditions, and all four are checked against the booking service rather than
 * taken on trust from the request body.
 *
 * <h2>Why the review is written before the booking is flagged</h2>
 *
 * <p>The order looks backwards and is deliberate. {@code Review.bookingReference} is
 * <strong>unique</strong>, so the database is the real mutex: two concurrent reviews for one
 * booking cannot both be written, whichever order the flag is set in. Flagging first and writing
 * second would leave a booking marked reviewed with no review behind it if the write failed — a
 * booking the customer can never review again, for a review that does not exist.
 *
 * <p>This way the worst case is a review that exists with the flag unset, which the unique
 * constraint still prevents from being duplicated, and which a later call can repair.
 *
 * <h2>No delete</h2>
 *
 * <p>There is deliberately no endpoint to remove a review, for anyone, including admins. The only
 * response available to a professional is a public reply. A brokerage that can quietly delete its
 * bad reviews is not running a review system.
 *
 * <h2>The date it is published on is the marketplace's day</h2>
 *
 * <p>{@code publishedOn} is <strong>stored</strong>, and it is the only public date this service
 * writes — it appears beside a named person's words on a profile anybody can read. It goes through
 * {@link MarketCalendar} rather than {@code LocalDate.now()} (D52, NEW-12), because "no delete"
 * above is exactly what makes a wrong one permanent: there is no endpoint that could correct it and
 * no instant stored beside it to reconstruct it from, so a review dated in the container's calendar
 * instead of the estate's is wrong for as long as the row exists and cannot afterwards be told from
 * a right one.
 *
 * <p>It is also the same column {@code CatalogSeeder} writes, which since D48 dates the seeded 63 in
 * {@code SeedCalendar.SEED_ZONE}. One column, two writers: without this they are two calendars, and
 * a review published at 23:30 Accra time sorts a day ahead of seeded reviews it was written after —
 * on an endpoint whose whole contract is "newest first".
 */
@RestController
public class ReviewWriteResource {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewWriteResource.class);

    private final ReviewRepository reviews;
    private final MarketplaceQueryRepository marketplace;
    private final BookingClient booking;
    private final MarketCalendar calendar;

    public ReviewWriteResource(
        ReviewRepository reviews,
        MarketplaceQueryRepository marketplace,
        BookingClient booking,
        MarketCalendar calendar
    ) {
        this.reviews = reviews;
        this.marketplace = marketplace;
        this.booking = booking;
        this.calendar = calendar;
    }

    public record PublishReview(
        @NotBlank String bookingReference,
        @NotNull @Min(1) @Max(5) Integer stars,
        @NotBlank String body
    ) {}

    @PostMapping("/api/reviews")
    public ResponseEntity<Object> publish(
        @Valid @RequestBody PublishReview request,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        String login = SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated customer"));

        BookingSummary summary;
        try {
            summary = booking
                .findBooking(request.bookingReference(), authorization)
                // Booking answers 404 both for "no such booking" and for "not yours", so this
                // covers the "for that customer" half of spec §9 without a separate check.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such booking"));
        } catch (BookingClient.BookingUnavailable e) {
            // Not the caller's fault, and not something to paper over: accepting the review anyway
            // would mean accepting one that might be unearned.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cannot verify the booking right now", e);
        }

        if (!"COMPLETED".equals(summary.status())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "only a COMPLETED booking can be reviewed; %s is %s".formatted(summary.reference(), summary.status())
            );
        }
        if (summary.reviewed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "booking %s has already been reviewed".formatted(summary.reference()));
        }

        Professional professional = marketplace
            .findByReference(summary.professionalRef())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "the booking names a professional this catalogue does not have"));

        Review review = new Review()
            .reference("r-" + UUID.randomUUID().toString().substring(0, 8))
            .customerLogin(login)
            .authorName(summary.customerName() == null ? login : summary.customerName())
            .authorInitials(initialsOf(summary.customerName(), login))
            .stars(request.stars())
            // D52: the marketplace's day, not the container's. This value is stored, public and
            // uncorrectable — see the class javadoc.
            .publishedOn(calendar.today())
            .body(request.body())
            .bookingReference(summary.reference())
            .professional(professional);

        Review saved;
        try {
            saved = reviews.save(review);
        } catch (DataIntegrityViolationException e) {
            // The unique constraint on bookingReference fired: something else reviewed this booking
            // between the check above and this write. That is the constraint doing its job.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "booking %s has already been reviewed".formatted(summary.reference()));
        }

        if (!booking.markReviewed(summary.reference(), authorization)) {
            // The review stands — it is earned and written. The flag is a convenience for the
            // customer's "leave a review" button; the unique constraint is what actually prevents
            // a second one.
            LOG.warn("review {} written for booking {} but the booking could not be flagged", saved.getReference(), summary.reference());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(java.util.Map.of("reference", saved.getReference(), "stars", saved.getStars(), "professionalRef", summary.professionalRef()));
    }

    /** "Kojo Ampia-Addison" -> "KA". Falls back to the login when there is no display name. */
    private static String initialsOf(String name, String login) {
        String source = name == null || name.isBlank() ? login : name;
        String[] parts = source.split("[ .]+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && out.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                out.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return out.toString();
    }
}
