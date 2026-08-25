package net.jojoaddison.web.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.service.Commission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The brokerage split for an amount, on a date. Used by the booking service to build a customer's
 * receipt.
 *
 * <h2>Why a pure function, and not "the split for booking X"</h2>
 *
 * <p>The obvious design is an endpoint taking a booking reference and returning that booking's
 * ledger row. It was written that way first and then removed: booking would have to call it with the
 * customer's own token, and the endpoint has no way to tell whose booking a reference is — so any
 * authenticated customer could read the price and commission of any booking whose reference they
 * could guess, and the seeded references are {@code b1}, {@code h1}, {@code q1}.
 *
 * <p>This takes an amount the caller already knows and a date, and returns arithmetic. It discloses
 * nothing: the commission rate is public — the prototype prints "12% platform fee" on the listing —
 * and the amount came from the caller. Ownership stays entirely in the booking service, which is the
 * only one that knows whose booking it is.
 *
 * <h2>Why booking does not just multiply by 0.12 itself</h2>
 *
 * <p>Because the rate is versioned by {@code effectiveFrom} and the rounding rule is a decision
 * ({@code HALF_UP}, per row, never on a total). A second implementation in booking would be a second
 * place for both to drift, and the symptom would be a receipt disagreeing with the ledger by a
 * pesewa — for which nobody would think to look.
 */
@RestController
public class BrokerageResource {

    private final BrokerageConfigRepository configs;

    public BrokerageResource(BrokerageConfigRepository configs) {
        this.configs = configs;
    }

    public record Split(
        long grossMinor,
        long commissionMinor,
        long netMinor,
        String commissionRate,
        String currency,
        int freeCancellationHours,
        String lateCancellationPct
    ) {}

    /**
     * @param amountMinor the price the caller is asking about, in minor units
     * @param on          the date the split should be struck at — defaults to today. A receipt for a
     *                    session completed last year must use last year's rate, which is why this is
     *                    a parameter rather than "now".
     */
    @GetMapping("/api/internal/brokerage/split")
    public Split split(@RequestParam long amountMinor, @RequestParam(required = false) LocalDate on) {
        if (amountMinor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountMinor cannot be negative");
        }
        BrokerageConfig config = inForce(on == null ? Instant.now() : on.atStartOfDay().toInstant(ZoneOffset.UTC));
        long commission = Commission.on(amountMinor, config.getCommissionRate());
        return new Split(
            amountMinor,
            commission,
            amountMinor - commission,
            config.getCommissionRate().toPlainString(),
            config.getCurrency(),
            config.getFreeCancellationHours(),
            config.getLateCancellationPct().toPlainString()
        );
    }

    /** The latest config that had already taken effect — a rate scheduled for next month prices nothing today. */
    private BrokerageConfig inForce(Instant at) {
        List<BrokerageConfig> all = configs.findAll();
        return all
            .stream()
            .filter(c -> c.getEffectiveFrom() != null && !c.getEffectiveFrom().isAfter(at))
            .max(Comparator.comparing(BrokerageConfig::getEffectiveFrom))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no brokerage configuration in force"));
    }
}
