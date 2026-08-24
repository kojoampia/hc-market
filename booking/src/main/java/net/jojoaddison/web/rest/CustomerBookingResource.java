package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.repository.BookingHistoryRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.BookingMapper;
import net.jojoaddison.service.BookingWorkflow;
import net.jojoaddison.service.BookingCreator;
import net.jojoaddison.service.BookingTransition;
import net.jojoaddison.service.dto.BookingDtos.BookingDetail;
import net.jojoaddison.service.dto.BookingDtos.BookingView;
import net.jojoaddison.service.dto.BookingDtos.CancelRequest;
import net.jojoaddison.service.dto.BookingDtos.CancellationPreview;
import net.jojoaddison.service.dto.BookingDtos.CreateBooking;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * The customer's side of bookings — spec §6, "Public / customer".
 *
 * <h2>Ownership</h2>
 *
 * <p>Every endpoint resolves the customer from the JWT subject and refuses any booking that is not
 * theirs, with a <strong>404 rather than a 403</strong>. A 403 confirms the reference exists, which
 * turns {@code /api/bookings/{ref}} into an oracle for enumerating other people's booking
 * references; 404 tells an attacker nothing they did not already know.
 */
@RestController
@RequestMapping("/api/bookings")
public class CustomerBookingResource {

    private final BookingWorkflow bookings;
    private final BookingQueryRepository repository;
    private final BookingHistoryRepository history;
    private final BookingMapper mapper;
    private final BookingCreator creator;

    public CustomerBookingResource(
        BookingWorkflow bookings,
        BookingQueryRepository repository,
        BookingHistoryRepository history,
        BookingMapper mapper,
        BookingCreator creator
    ) {
        this.bookings = bookings;
        this.repository = repository;
        this.history = history;
        this.mapper = mapper;
        this.creator = creator;
    }

    /** Wizard step 4 — creates a booking in {@code REQUESTED}. */
    @PostMapping
    public ResponseEntity<BookingView> create(@Valid @RequestBody CreateBooking request) {
        String login = currentLogin();
        Booking booking = new Booking()
            // Short, unique, and not guessable in sequence — a booking reference ends up in URLs
            // and emails, and b1/b2/b3 would let anyone walk the estate's bookings by hand.
            .reference("b-" + UUID.randomUUID().toString().substring(0, 8))
            .customerLogin(login)
            .customerName(request.customerName() == null || request.customerName().isBlank() ? login : request.customerName())
            .professionalRef(request.professionalRef())
            // Carried on the booking so the professional's inbox never has to ask catalog who
            // this ref belongs to. Supplied by the client from the profile it just read.
            .professionalLogin(request.professionalLogin())
            .serviceRef(request.serviceRef())
            .serviceName(request.serviceName())
            .priceMinor(request.priceMinor())
            .currency(request.currency() == null ? "GHS" : request.currency())
            .scheduledDate(request.scheduledDate())
            .scheduledTime(request.scheduledTime())
            .deliveryMode(DeliveryMode.valueOf(request.deliveryMode()))
            .status(BookingStatus.REQUESTED)
            .customerNote(request.customerNote())
            .onBehalfOf(request.onBehalfOf())
            .visitAddress(request.visitAddress())
            .careSummaryShared(Boolean.TRUE.equals(request.careSummaryShared()))
            .raisedAt(Instant.now())
            .reviewed(false);
        // Saved through BookingCreator so the row and its booking.requested event share one
        // transaction — the same guarantee every transition gets.
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toView(creator.create(booking, login)));
    }

    /** My bookings — the prototype's four tabs are four calls to this one query. */
    @GetMapping("/mine")
    public List<BookingView> mine(@RequestParam(required = false) BookingStatus status) {
        return mapper.toViews(bookings.forCustomer(currentLogin(), status));
    }

    @GetMapping("/{ref}")
    public BookingDetail one(@PathVariable String ref) {
        Booking booking = mineOr404(ref);
        return new BookingDetail(mapper.toView(booking), mapper.toHistory(history.findByBookingId(booking.getId())));
    }

    /**
     * What cancelling would cost, without cancelling. The prototype shows the fee before the
     * customer commits, which is the entire point of the modal.
     */
    @GetMapping("/{ref}/cancellation-preview")
    public CancellationPreview cancellationPreview(@PathVariable String ref) {
        Booking booking = mineOr404(ref);
        Instant now = Instant.now();
        Instant scheduled = booking.getScheduledDate().atTime(safeTime(booking.getScheduledTime())).toInstant(ZoneOffset.UTC);
        long hours = Duration.between(now, scheduled).toHours();
        return new CancellationPreview(
            booking.getReference(),
            bookings.isLate(booking, now),
            bookings.freeCancellationHours(),
            Math.max(hours, 0),
            booking.getPriceMinor() == null ? 0L : booking.getPriceMinor(),
            booking.getCurrency()
        );
    }

    @PostMapping("/{ref}/cancel")
    public BookingView cancel(@PathVariable String ref, @RequestBody(required = false) CancelRequest request) {
        Booking booking = mineOr404(ref);
        String reason = request == null ? null : request.reason();
        return mapper.toView(transition(booking, new BookingTransition.Cancel(CancelledBy.CUSTOMER, reason)));
    }

    /**
     * Marks this booking as reviewed. Called by the catalog service after it has accepted a review,
     * carrying the customer's own token — so booking enforces ownership itself rather than trusting
     * a caller's word about who is asking.
     *
     * <p>Idempotent by design: a second call returns 409 rather than silently succeeding, which is
     * what lets catalog treat "already reviewed" as a real answer instead of a lost update.
     */
    @PostMapping("/{ref}/reviewed")
    public BookingView markReviewed(@PathVariable String ref) {
        Booking booking = mineOr404(ref);
        if (Boolean.TRUE.equals(booking.getReviewed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "booking " + ref + " has already been reviewed");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only a COMPLETED booking can be reviewed; " + ref + " is " + booking.getStatus());
        }
        booking.setReviewed(true);
        return mapper.toView(repository.save(booking));
    }

    // ------------------------------------------------------------------- helpers --

    private Booking transition(Booking booking, BookingTransition move) {
        try {
            return bookings.apply(booking, move, currentLogin());
        } catch (IllegalStateException e) {
            // A refused transition is the caller asking for something the booking's current state
            // does not allow — 409, not 500. The message names both states.
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 404, never 403 — see the class comment. */
    private Booking mineOr404(String ref) {
        String login = currentLogin();
        return bookings
            .byReference(ref)
            .filter(b -> login.equals(b.getCustomerLogin()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such booking"));
    }

    private String currentLogin() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated customer"));
    }

    private static LocalTime safeTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (RuntimeException e) {
            return LocalTime.NOON;
        }
    }
}
