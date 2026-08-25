package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.service.SlotTime;
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
import net.jojoaddison.service.BrokerageClient;
import net.jojoaddison.service.dto.BookingDtos.Receipt;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final BrokerageClient brokerage;

    public CustomerBookingResource(
        BookingWorkflow bookings,
        BookingQueryRepository repository,
        BookingHistoryRepository history,
        BookingMapper mapper,
        BookingCreator creator,
        BrokerageClient brokerage
    ) {
        this.bookings = bookings;
        this.repository = repository;
        this.history = history;
        this.mapper = mapper;
        this.creator = creator;
        this.brokerage = brokerage;
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
            .scheduledTime(SlotTime.parse(request.scheduledTime()))
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
        Instant scheduled = booking.getScheduledDate().atTime(booking.getScheduledTime()).toInstant(ZoneOffset.UTC);
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

    /**
     * The receipt — spec §6's "gross, commission, total".
     *
     * <p>The split is struck at the date the session happened, not today: a receipt reprinted after
     * the brokerage changes its terms must still say what the customer was told at the time.
     */
    @GetMapping("/{ref}/receipt")
    public Receipt receipt(@PathVariable String ref, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        Booking b = mineOr404(ref);
        LocalDate struckAt = b.getCompletedAt() != null
            ? LocalDate.ofInstant(b.getCompletedAt(), java.time.ZoneOffset.UTC)
            : b.getScheduledDate();
        long price = b.getPriceMinor() == null ? 0L : b.getPriceMinor();

        BrokerageClient.Split split;
        try {
            split = brokerage.splitFor(price, struckAt, authorization);
        } catch (BrokerageClient.PayoutUnavailable e) {
            // Deliberately no fallback. Guessing 12% here would produce a receipt that looks
            // authoritative and might not match the ledger.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }

        return new Receipt(
            b.getReference(),
            b.getServiceName(),
            b.getProfessionalRef(),
            b.getScheduledDate(),
            SlotTime.format(b.getScheduledTime()),
            b.getStatus() == null ? null : b.getStatus().name(),
            split.grossMinor(),
            split.commissionMinor(),
            split.netMinor(),
            // The fee is inside the price, so the customer's total IS the gross.
            split.grossMinor(),
            split.commissionRate(),
            split.currency() == null ? b.getCurrency() : split.currency()
        );
    }

    /**
     * The customer's half of a reschedule.
     *
     * <p>The professional proposes a new time and the booking waits in RESCHEDULE_PROPOSED. Until
     * now only the professional could move it on, which meant a customer could be offered a time and
     * have no way to answer — the state existed with no exit the customer controlled.
     */
    @PostMapping("/{ref}/reschedule/accept")
    public BookingView acceptReschedule(@PathVariable String ref) {
        return mapper.toView(transition(mineOr404(ref), new BookingTransition.Accept()));
    }

    @PostMapping("/{ref}/reschedule/decline")
    public BookingView declineReschedule(@PathVariable String ref, @RequestBody(required = false) CancelRequest body) {
        // Declining a proposed time cancels the booking: the original slot is gone (the professional
        // proposed a change because they could not keep it) and there is nothing to fall back to.
        return mapper.toView(
            transition(mineOr404(ref), new BookingTransition.Cancel(CancelledBy.CUSTOMER, body == null ? "reschedule declined" : body.reason()))
        );
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
}
