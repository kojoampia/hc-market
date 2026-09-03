package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.service.CatalogClient;
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
import net.jojoaddison.service.payment.BookingPayments;
import net.jojoaddison.service.payment.PaymentState;
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
import net.jojoaddison.service.dto.BookingDtos.PaymentAction;
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

    /** See {@link #zoneOf}. Ghana is UTC+0 all year, which is what makes this a safe fallback. */
    private static final String DEFAULT_ZONE_ID = "Africa/Accra";

    private final BookingWorkflow bookings;
    private final BookingQueryRepository repository;
    private final BookingHistoryRepository history;
    private final BookingMapper mapper;
    private final BookingCreator creator;
    private final BrokerageClient brokerage;
    private final CatalogClient catalog;
    private final BookingPayments payments;

    public CustomerBookingResource(
        BookingWorkflow bookings,
        BookingQueryRepository repository,
        BookingHistoryRepository history,
        BookingMapper mapper,
        BookingCreator creator,
        BrokerageClient brokerage,
        CatalogClient catalog,
        BookingPayments payments
    ) {
        this.bookings = bookings;
        this.repository = repository;
        this.history = history;
        this.mapper = mapper;
        this.creator = creator;
        this.brokerage = brokerage;
        this.catalog = catalog;
        this.payments = payments;
    }

    /**
     * Wizard step 4 — creates a booking in {@code REQUESTED}.
     *
     * <p><strong>Price and currency come from the catalogue, never from the request.</strong> They
     * used to be stored as sent, which made the price of a booking whatever the caller said it was
     * — and since {@code Ledger} derives gross, commission and net from the completed booking, that
     * was a direct route to crediting a professional an arbitrary amount. See
     * {@link net.jojoaddison.service.CatalogClient}.
     *
     * <p>A request whose figures disagree with the catalogue is rejected with <strong>409</strong>
     * rather than quietly booked at the catalogue's number. The realistic cause is that the price
     * changed while the customer was in the wizard, and completing the booking at a price they were
     * never shown is not a fix — it charges them something they did not agree to. The client
     * re-reads the profile and asks again.
     *
     * <p><strong>{@code professionalLogin} comes from the catalogue too</strong>, as of D28. That
     * one was not a wrong number but a wrong <em>recipient</em>: a truthful {@code professionalRef}
     * sent with somebody else's login put a real booking into an inbox it did not belong to, and
     * nothing anywhere disagreed. Same 409 on a mismatch, same 503 when catalog cannot be asked.
     *
     * <p><strong>A payment that is not decided yet still yields a booking</strong>, in
     * {@code PENDING_PAYMENT} — {@code decisions.md} D43. All three providers D37 chose confirm
     * asynchronously, so the alternative was to hold the customer's request in a second table that is
     * a booking in everything but name, and to reserve nothing while they tap their phone. The row is
     * written, no {@code booking.requested} is published, and the professional learns of it when the
     * webhook confirms the money. The response carries {@code payment} — a URL to visit or "a prompt
     * has been sent" — and it is the only response in the estate that does.
     */
    @PostMapping
    public ResponseEntity<BookingView> create(@Valid @RequestBody CreateBooking request) {
        String login = currentLogin();
        CatalogClient.Offering offering = priceFromCatalogue(request);
        String professionalLogin = loginFromCatalogue(request);
        Booking booking = new Booking()
            // Short, unique, and not guessable in sequence — a booking reference ends up in URLs
            // and emails, and b1/b2/b3 would let anyone walk the estate's bookings by hand.
            .reference("b-" + UUID.randomUUID().toString().substring(0, 8))
            .customerLogin(login)
            .customerName(request.customerName() == null || request.customerName().isBlank() ? login : request.customerName())
            .professionalRef(request.professionalRef())
            // Carried on the booking so the professional's inbox never has to ask catalog who this
            // ref belongs to (D12) — but taken from the CATALOGUE, not from the request. See
            // loginFromCatalogue below.
            .professionalLogin(professionalLogin)
            .serviceRef(request.serviceRef())
            // All three from the catalogue's answer, not the request body.
            .serviceName(offering.service().name())
            .priceMinor(offering.service().priceMinor())
            .currency(offering.service().currency())
            // The professional's zone, from the same answer that priced the booking (D21). Stored
            // rather than resolved at read time: a booking already made must not move on the clock
            // because the professional later relocated.
            .zoneId(zoneOf(offering))
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
        BookingPayments.Taken taken = authorizePayment(booking);
        // The provider has not decided yet — Paystack's redirect, Hubtel's and MoMo's phone prompt.
        // D43: the booking is written, in a state the professional never sees, and announced only
        // when the money is confirmed. Everything else about the request is unchanged.
        boolean awaitingPayment = taken.outcome().state() == PaymentState.PENDING;
        if (awaitingPayment) {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        }
        try {
            // Saved through BookingCreator so the row and its booking.requested event share one
            // transaction — the same guarantee every transition gets. The pending path writes the
            // same row and publishes nothing; the event follows from the webhook.
            Booking saved = awaitingPayment ? creator.createAwaitingPayment(booking, login) : creator.create(booking, login);
            return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toView(saved, nextActionFor(taken)));
        } catch (RuntimeException e) {
            // The money was committed a moment ago and the booking does not exist. Give it back
            // before the exception leaves this method — nothing further along knows a payment was
            // taken, and after the response has been written there is nobody left to ask. See
            // BookingPayments.release: a release that itself fails marks the row for a person rather
            // than retrying into a provider that has just failed.
            payments.release(taken, "booking " + booking.getReference() + " could not be created");
            throw e;
        }
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
     *
     * <p><strong>A booking that cannot be cancelled has no preview</strong>, and the refusal is the
     * same 409 {@code /cancel} itself gives — asked of the same transition, so the two cannot drift
     * apart. Without that check the endpoint answered for any status, and the case that made it matter
     * is {@code PENDING_PAYMENT} (D43): a booking whose appointment is inside the free-cancellation
     * window came back as {@code lateCancellation: true} carrying the full price, which is a fee
     * quoted to a customer who has paid nothing, for an action the endpoint next door would refuse.
     */
    @GetMapping("/{ref}/cancellation-preview")
    public CancellationPreview cancellationPreview(@PathVariable String ref) {
        Booking booking = mineOr404(ref);
        BookingTransition.Cancel cancel = new BookingTransition.Cancel(CancelledBy.CUSTOMER, null);
        if (!cancel.legalFrom(booking.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "booking " + ref + " cannot be cancelled from " + booking.getStatus() + ", so there is nothing to preview"
            );
        }
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

    /**
     * Establishes what this offering costs, and refuses to proceed on any disagreement.
     *
     * <p>The currency check is the one D22 asks for: {@code currency} is carried on every money
     * field in the estate and only GHS is ever used, so a mismatch cannot arise from ordinary
     * traffic. That is exactly why it is worth asserting — a column that can silently disagree
     * across a join is worse than no column, and the failure would otherwise first appear as a
     * ledger row denominated in something the brokerage config does not price.
     */
    private CatalogClient.Offering priceFromCatalogue(CreateBooking request) {
        CatalogClient.Offering offering;
        try {
            offering = catalog.priceOf(request.professionalRef(), request.serviceRef());
        } catch (CatalogClient.UnknownOffering unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, unknown.getMessage());
        } catch (CatalogClient.CatalogUnavailable down) {
            // 503, not 500: nothing is broken, the price simply cannot be established right now,
            // and retrying is the correct response.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, down.getMessage());
        }
        if (request.priceMinor() != null && request.priceMinor() != offering.service().priceMinor()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "this service now costs %d, not %d — reload the profile and try again".formatted(
                        offering.service().priceMinor(),
                        request.priceMinor()
                    )
            );
        }
        if (request.currency() != null && !request.currency().equals(offering.service().currency())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "this service is priced in %s, not %s".formatted(offering.service().currency(), request.currency())
            );
        }
        return offering;
    }

    /**
     * Asks the payment seam whether this booking's money is in hand — {@code decisions.md} D15/D31.
     *
     * <p><strong>Today this always passes</strong>, because the only provider bean in the estate
     * reports {@code OFF_PLATFORM}: the customer pays the professional directly, which is what has
     * always happened and is now stated rather than assumed. So this call changes no behaviour and
     * is not meant to.
     *
     * <p>What it is, is the one place a real provider gets consulted. When one is configured, a
     * booking whose payment is declined is refused here instead of being created and reconciled
     * later — because a booking that exists without its money is a professional's diary blocked for
     * a session nobody paid for, and unpicking that costs two people a phone call each.
     *
     * <p>Called <em>before</em> {@code creator.create}, deliberately. Authorizing after the row is
     * written would put an outbound call to a third party inside the transaction that publishes
     * {@code booking.requested}, and a provider timing out would then roll back a booking that the
     * customer's screen had every reason to believe was made.
     *
     * <p><strong>402 for a decline, 502 for a provider that fell over.</strong> The client's next
     * move differs: one means try another instrument, the other means try again. Collapsing them into
     * one status would make the customer re-enter details that were never the problem.
     *
     * <p><strong>The handle comes back, and it is kept</strong> — {@code decisions.md} D41. This used
     * to read the outcome's state and drop the rest of it, including the provider's own reference,
     * which is the only thing {@code capture}, {@code refund}, {@code voidAuthorization} and
     * {@code status} can be called with. {@link BookingPayments#take} writes it down before returning,
     * and what it returns is what {@link BookingPayments#release} needs if the booking then fails.
     * The status codes stay here because they are answers to a client; the money's lifecycle does
     * not, because it is not a detail of one endpoint.
     */
    private BookingPayments.Taken authorizePayment(Booking booking) {
        BookingPayments.Taken taken = payments.take(booking);
        if (taken.outcome().state().permitsBooking()) {
            return taken;
        }
        HttpStatus status = taken.outcome().state() == PaymentState.DECLINED ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.BAD_GATEWAY;
        throw new ResponseStatusException(status, taken.outcome().reason() == null ? "payment could not be taken" : taken.outcome().reason());
    }

    /**
     * What the customer still has to do about the money, if anything — {@code decisions.md} D43.
     *
     * <p>Null unless the payment is pending, which is what keeps a payment link out of every other
     * booking view in the estate. The state name travels beside the action so a client can tell
     * "waiting on you" from "nothing to pay" without inferring it from the booking's status, and the
     * URL is the provider's own, relayed rather than rewritten.
     */
    private static PaymentAction nextActionFor(BookingPayments.Taken taken) {
        if (taken.outcome().state() != PaymentState.PENDING) {
            return null;
        }
        return new PaymentAction(
            taken.outcome().state().name(),
            taken.outcome().nextAction().kind().name(),
            taken.outcome().nextAction().url()
        );
    }

    /**
     * The zone the booking's wall clock belongs to — {@code decisions.md} D21.
     *
     * <p>Falls back to Africa/Accra rather than refusing, and that is the one place in this resource
     * where a default is right. Ghana is UTC+0 all year, so an absent zone cannot make the time
     * wrong today; a catalogue one release behind would otherwise make every booking in the estate
     * fail on a field that changes nothing. The price and the owner get no such latitude because
     * guessing either produces a figure that is actually incorrect.
     */
    private static String zoneOf(CatalogClient.Offering offering) {
        return offering.zoneId() == null || offering.zoneId().isBlank() ? DEFAULT_ZONE_ID : offering.zoneId();
    }

    /**
     * Establishes whose booking this actually is — {@code decisions.md} D28.
     *
     * <p>{@code professionalLogin} used to be stored exactly as sent. The field is on the booking
     * for a good reason (D12: the professional's inbox must not have to ask catalog on every read),
     * but denormalised was being used as if it meant unverified, and the cost was not a wrong number
     * — it was a real booking landing in <strong>someone else's inbox</strong>, with every derived
     * figure downstream perfectly consistent with the login that was stored.
     *
     * <p>The catalogue's answer is now the authority. A request that omits the field gets it; a
     * request that disagrees is <strong>409</strong>, matching how a stale price is handled and for
     * the same reason — the realistic cause is a profile read before an ownership change, and
     * silently correcting the caller teaches it nothing. A caller that meant to lie learns only
     * that it was refused.
     *
     * <p>The message names neither login. Confirming "no, it is actually <em>this</em> person"
     * would turn a refusal into the disclosure the endpoint exists to avoid.
     */
    private String loginFromCatalogue(CreateBooking request) {
        String actual;
        try {
            actual = catalog.loginOf(request.professionalRef());
        } catch (CatalogClient.UnknownOffering unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, unknown.getMessage());
        } catch (CatalogClient.CatalogUnavailable down) {
            // 503 for the same reason the price call uses it: nothing is broken, the fact simply
            // cannot be established right now, and a guessed owner is worse than a retry.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, down.getMessage());
        }
        if (request.professionalLogin() != null && !request.professionalLogin().equals(actual)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "professional %s is not held by that login — reload the profile and try again".formatted(request.professionalRef())
            );
        }
        return actual;
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
