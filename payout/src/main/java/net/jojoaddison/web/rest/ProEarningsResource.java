package net.jojoaddison.web.rest;

import java.time.LocalDate;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.EarningsService;
import net.jojoaddison.service.dto.EarningsDtos.Earnings;
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

    public ProEarningsResource(EarningsService earnings) {
        this.earnings = earnings;
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
}
