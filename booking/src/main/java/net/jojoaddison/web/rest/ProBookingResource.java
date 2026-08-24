package net.jojoaddison.web.rest;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.BookingMapper;
import net.jojoaddison.service.BookingWorkflow;
import net.jojoaddison.service.BookingTransition;
import net.jojoaddison.service.dto.BookingDtos.BookingView;
import net.jojoaddison.service.dto.BookingDtos.DeclineRequest;
import net.jojoaddison.service.dto.BookingDtos.ProposeRequest;
import net.jojoaddison.service.dto.BookingDtos.ScheduleDay;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * The professional workspace's bookings — spec §6, "Professional workspace".
 *
 * <h2>Ownership</h2>
 *
 * <p>These endpoints take <strong>no professional parameter</strong>, for the same reason
 * {@code /api/pro/earnings} takes none: the professional is resolved from the JWT and nowhere else,
 * so spec §9's "refuse any reference that is not the caller's" holds by construction rather than by
 * a check someone can forget to write.
 *
 * <h2>The professional reference</h2>
 *
 * <p>Bookings are keyed by {@code professionalRef} ("p1"), but a JWT carries a login. This service
 * does not own {@code Professional}, so it cannot resolve one from the other without asking the
 * catalog service — which would make the requests inbox fail whenever catalog is down. Instead the
 * mapping is carried on the booking itself: {@code professionalLogin} is written at seed and create
 * time, exactly as {@code Ledger.professionalLogin} is (decisions.md D12).
 */
@RestController
@RequestMapping("/api/pro")
public class ProBookingResource {

    private final BookingWorkflow bookings;
    private final BookingQueryRepository repository;
    private final BookingMapper mapper;

    public ProBookingResource(BookingWorkflow bookings, BookingQueryRepository repository, BookingMapper mapper) {
        this.bookings = bookings;
        this.repository = repository;
        this.mapper = mapper;
    }

    /** The requests inbox — everything still waiting on this professional. */
    @GetMapping("/requests")
    public List<BookingView> requests() {
        return mapper.toViews(repository.findByProfessionalLoginAndStatusOrderByScheduledDateAsc(currentLogin(), BookingStatus.REQUESTED));
    }

    @PostMapping("/requests/{ref}/accept")
    public BookingView accept(@PathVariable String ref) {
        return mapper.toView(transition(mineOr404(ref), new BookingTransition.Accept()));
    }

    @PostMapping("/requests/{ref}/decline")
    public BookingView decline(@PathVariable String ref, @RequestBody(required = false) DeclineRequest body) {
        return mapper.toView(transition(mineOr404(ref), new BookingTransition.Decline(body == null ? null : body.reason())));
    }

    @PostMapping("/requests/{ref}/propose")
    public BookingView propose(@PathVariable String ref, @RequestBody ProposeRequest body) {
        if (body == null || body.date() == null || body.time() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a proposed date and time are required");
        }
        return mapper.toView(transition(mineOr404(ref), new BookingTransition.ProposeReschedule(body.date(), body.time())));
    }

    /** The session happened. This is what puts a row in the ledger. */
    @PostMapping("/bookings/{ref}/complete")
    public BookingView complete(@PathVariable String ref) {
        return mapper.toView(transition(mineOr404(ref), new BookingTransition.Complete()));
    }

    @PostMapping("/bookings/{ref}/no-show")
    public BookingView noShow(@PathVariable String ref) {
        return mapper.toView(transition(mineOr404(ref), new BookingTransition.NoShow()));
    }

    @PostMapping("/bookings/{ref}/cancel")
    public BookingView cancel(@PathVariable String ref, @RequestBody(required = false) net.jojoaddison.service.dto.BookingDtos.CancelRequest body) {
        return mapper.toView(
            transition(mineOr404(ref), new BookingTransition.Cancel(CancelledBy.PROFESSIONAL, body == null ? null : body.reason()))
        );
    }

    /**
     * The schedule, grouped by day as the screen renders it.
     *
     * <p>Grouping happens here rather than in the client because the empty days matter: a day with
     * no appointments still appears in the prototype's schedule, and a client grouping a flat list
     * cannot invent days that the list never mentioned.
     */
    @GetMapping("/schedule")
    public List<ScheduleDay> schedule(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String q
    ) {
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(14) : to;

        List<Booking> confirmed = repository
            .findByProfessionalLoginAndStatusOrderByScheduledDateAsc(currentLogin(), BookingStatus.CONFIRMED)
            .stream()
            .filter(b -> !b.getScheduledDate().isBefore(start) && !b.getScheduledDate().isAfter(end))
            .filter(b -> mode == null || mode.isBlank() || (b.getDeliveryMode() != null && b.getDeliveryMode().name().equalsIgnoreCase(mode)))
            .filter(b -> q == null || q.isBlank() || matches(b, q.toLowerCase()))
            .toList();

        Map<LocalDate, List<BookingView>> byDay = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            byDay.put(d, new java.util.ArrayList<>());
        }
        confirmed.forEach(b -> byDay.computeIfAbsent(b.getScheduledDate(), d -> new java.util.ArrayList<>()).add(mapper.toView(b)));
        return byDay.entrySet().stream().map(e -> new ScheduleDay(e.getKey(), e.getValue())).toList();
    }

    // ------------------------------------------------------------------- helpers --

    private static boolean matches(Booking b, String needle) {
        return (b.getCustomerName() != null && b.getCustomerName().toLowerCase().contains(needle)) ||
               (b.getServiceName() != null && b.getServiceName().toLowerCase().contains(needle));
    }

    private Booking transition(Booking booking, BookingTransition move) {
        try {
            return bookings.apply(booking, move, currentLogin());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 404 rather than 403, for the same reason as on the customer side. */
    private Booking mineOr404(String ref) {
        String login = currentLogin();
        return bookings
            .byReference(ref)
            .filter(b -> login.equals(b.getProfessionalLogin()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such booking"));
    }

    private String currentLogin() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated professional"));
    }
}
