package net.jojoaddison.web.rest;

import java.time.LocalDate;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.EarningsService;
import java.util.List;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.repository.PayoutQueryRepository;
import net.jojoaddison.service.BookingScheduleClient;
import net.jojoaddison.service.dto.EarningsDtos.Earnings;
import net.jojoaddison.service.dto.EarningsDtos.NextUp;
import net.jojoaddison.service.dto.EarningsDtos.Overview;
import net.jojoaddison.service.dto.EarningsDtos.PayoutRow;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * The professional workspace's earnings screen — spec §6, {@code GET /api/pro/earnings}.
 *
 * <h2>Ownership</h2>
 *
 * <p>Spec §9: "Ownership is enforced in the service layer, not by hiding buttons: {@code /api/pro/**}
 * resolves the professional from the token and refuses any reference that is not the caller's."
 *
 * <p>So this endpoint takes <strong>no professional parameter at all</strong>. The login comes from
 * the JWT subject and nowhere else, which makes "refuse any reference that is not the caller's"
 * true by construction rather than by a check someone can forget to write. There is deliberately no
 * {@code ?professionalRef=} override, not even an admin one — that would reintroduce exactly the
 * parameter this design removes.
 */
@RestController
@RequestMapping("/api/pro")
public class ProEarningsResource {

    private final EarningsService earnings;
    private final EarningsRepository ledger;
    private final PayoutQueryRepository payouts;
    private final BookingScheduleClient schedule;

    public ProEarningsResource(
        EarningsService earnings,
        EarningsRepository ledger,
        PayoutQueryRepository payouts,
        BookingScheduleClient schedule
    ) {
        this.earnings = earnings;
        this.ledger = ledger;
        this.payouts = payouts;
        this.schedule = schedule;
    }

    /**
     * @param months how many trailing months of rows to return. The prototype's chart shows 7.
     */
    @GetMapping("/earnings")
    public ResponseEntity<Earnings> earnings(@RequestParam(defaultValue = "7") int months) {
        String login = SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated professional"));
        if (months < 1 || months > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "months must be between 1 and 60");
        }
        return ResponseEntity.ok(earnings.forProfessional(login, months, LocalDate.now()));
    }

    /**
     * The Overview screen — spec §6's four stat tiles, both charts and "next up".
     *
     * <p>Everything except the last comes from this service's own aggregates over the ledger. "Next
     * up" comes from the booking service, and its absence is reported rather than faked:
     * {@code nextUpAvailable} distinguishes "booking says nothing is booked" from "booking could not
     * be reached", which are very different things to show a professional.
     */
    @GetMapping("/overview")
    public Overview overview(
        @RequestParam(defaultValue = "7") int months,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        String login = currentLogin();
        Earnings e = earnings.forProfessional(login, months, LocalDate.now());

        var day = schedule.nextConfirmedDay(authorization);
        NextUp nextUp = day
            .map(d -> d.bookings().get(0))
            .map(b -> new NextUp(b.reference(), b.customerName(), b.serviceName(), b.scheduledDate(), b.scheduledTime(), b.deliveryMode()))
            .orElse(null);

        return new Overview(
            login,
            e.lifetime(),
            e.monthToDate(),
            e.averageSessionValueMinor(),
            e.months(),
            e.byDeliveryMode(),
            e.byService(),
            nextUp,
            day.isPresent()
        );
    }

    /**
     * The payout table.
     *
     * <p>Payouts are keyed by professional reference while the token carries a login, so the
     * reference is resolved from this professional's own ledger rows — see
     * {@code EarningsRepository.professionalRefsFor}. Nobody with no ledger entries has payouts, so
     * the empty case is correct rather than merely tolerated.
     */
    @GetMapping("/payouts")
    public List<PayoutRow> payouts() {
        List<String> refs = ledger.professionalRefsFor(currentLogin());
        if (refs.isEmpty()) {
            return List.of();
        }
        return payouts
            .findByProfessionalRefInOrderByPeriodStartDesc(refs)
            .stream()
            .map(p ->
                new PayoutRow(
                    p.getReference(),
                    p.getPeriodStart(),
                    p.getPeriodEnd(),
                    p.getGrossMinor() == null ? 0L : p.getGrossMinor(),
                    p.getCommissionMinor() == null ? 0L : p.getCommissionMinor(),
                    p.getNetMinor() == null ? 0L : p.getNetMinor(),
                    p.getCurrency(),
                    p.getStatus() == null ? null : p.getStatus().name(),
                    p.getSettledOn(),
                    p.getBankReference()
                )
            )
            .toList();
    }

    private String currentLogin() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated professional"));
    }
}
