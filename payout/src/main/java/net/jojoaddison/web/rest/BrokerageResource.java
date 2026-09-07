package net.jojoaddison.web.rest;

import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.service.BrokerageTerms;
import net.jojoaddison.service.Commission;
import net.jojoaddison.service.MarketCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The brokerage split for an amount, at a moment. Used by the booking service to build a customer's
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
 * <p>This takes an amount the caller already knows and a moment, and returns arithmetic. It discloses
 * nothing: the commission rate is public — the prototype prints "12% platform fee" on the listing —
 * and the amount came from the caller. Ownership stays entirely in the booking service, which is the
 * only one that knows whose booking it is.
 *
 * <p><strong>It is gateway-routed and therefore reachable by any token this estate accepts.</strong>
 * {@code Path=/services/healthconnectpayout/api/**} covers {@code /api/internal/...}, unlike
 * catalog's {@code /internal/**}, which D28 keeps private precisely by not matching. That is by
 * design and the paragraph above is why; it is written down here because "internal" in the path
 * reads as a claim about reachability that this one does not make.
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

    private static final Logger LOG = LoggerFactory.getLogger(BrokerageResource.class);

    private final BrokerageTerms terms;

    public BrokerageResource(BrokerageTerms terms) {
        this.terms = terms;
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
     * @param at          the <strong>moment</strong> the split should be struck at, and the answer
     *                    whenever the caller has one — {@code decisions.md} D56, backlog NEW-16.
     *                    <p>This parameter did not exist until D56 and {@code on} was the whole of
     *                    the question. D53 had moved the ledger onto the completion instant and left
     *                    the receipt on a day, so a rate taking effect at <em>noon</em> on the day a
     *                    booking completed at 14:00 priced the ledger under the new terms and the
     *                    receipt under the old — NEW-13's shape one granularity down, biting only
     *                    when {@code effectiveFrom} is not midnight in Accra, which the one row in
     *                    either estate is.
     * @param on          the <em>day</em> the split should be struck on, kept beside {@code at} and
     *                    used only when there is no {@code at}. It is not deprecated and must not be
     *                    dropped, for two independent reasons.
     *                    <p><strong>Compatibility.</strong> An older booking service knows nothing of
     *                    {@code at} and sends only this; a newer one sends both, so an older
     *                    <em>payout</em> that ignores an unknown parameter still gets a day rather
     *                    than nothing. Dropping {@code on} would have left that deployment falling
     *                    through to the clock, which is NEW-13 rebuilt in the other service.
     *                    <p><strong>And some callers genuinely have no moment.</strong> The receipt
     *                    for a booking that has not completed is struck on its scheduled day, and a
     *                    day is the only truth there is about it. That is why that branch does not
     *                    warn: a warning that fires on a correct state is one people learn to ignore,
     *                    and payout cannot tell the two callers apart anyway.
     *                    <p>A date is not a moment, so turning one into an instant needs a calendar,
     *                    and it is the marketplace's — {@code decisions.md} D51. This read
     *                    {@code ZoneOffset.UTC}, which was never the NEW-10 defect: a named zone is a
     *                    decision somebody can disagree with, and the whole of that defect was
     *                    calendars that were never named. It is {@link MarketCalendar#MARKET_ZONE}
     *                    now because "which of the brokerage's terms were in force on this day" is a
     *                    <em>marketplace</em> day, and because one service answering the same
     *                    question two ways is noise a reader has to rule out.
     */
    @GetMapping("/api/internal/brokerage/split")
    public Split split(
        @RequestParam long amountMinor,
        @RequestParam(required = false) Instant at,
        @RequestParam(required = false) LocalDate on
    ) {
        if (amountMinor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountMinor cannot be negative");
        }
        BrokerageConfig config = inForce(struckAt(at, on));
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

    /**
     * The moment these terms are asked about — four cases, and the fourth one refuses.
     *
     * <ol>
     *   <li><strong>{@code at} alone</strong>: it is the answer. Nothing else is consulted.
     *   <li><strong>{@code at} and {@code on}</strong>: {@code at} wins. They are not redundant by
     *       accident — booking sends both so that whichever version of this service answers has
     *       something to work with — and {@code on} is a coarsening of {@code at} by construction, so
     *       "they differ" is the normal case rather than an error. What is not normal is {@code on}
     *       naming a different <em>day</em> from {@code at}, which only a caller holding two ideas of
     *       when can produce. That is a WARN and not a refusal: the answer is {@code at} either way,
     *       so refusing would cost a customer their receipt and buy nothing, and the line names both
     *       so the caller can be fixed.
     *   <li><strong>{@code on} alone</strong>: the start of that day in the marketplace's calendar.
     *       See the parameter's javadoc for why that is an answer rather than a fallback.
     *   <li><strong>Neither: refused, 400.</strong> Never {@code Instant.now()}, which is what this
     *       did until D56 and is the shape D53 removed from the consumer. A request that failed to
     *       say when is not a request about now — answering it prices a receipt at today's terms and
     *       returns something indistinguishable from a correct answer. It is unreachable from any
     *       version of booking that has ever run, since {@code Booking.scheduledDate} is required and
     *       has always been sent, which is precisely when a closed door is free.
     * </ol>
     */
    private static Instant struckAt(Instant at, LocalDate on) {
        if (at == null && on == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "a split must be struck at a stated moment or on a stated day; neither 'at' nor 'on' was given"
            );
        }
        if (at == null) {
            return on.atStartOfDay(MarketCalendar.MARKET_ZONE).toInstant();
        }
        if (on != null && !on.equals(LocalDate.ofInstant(at, MarketCalendar.MARKET_ZONE))) {
            LOG.warn(
                "split asked for at={} and on={}, which is not that instant's day in {} — pricing at the instant and ignoring the day (decisions.md D56)",
                at,
                on,
                MarketCalendar.MARKET_ZONE
            );
        }
        return at;
    }

    /**
     * The terms in force then. One selector, shared with {@code BookingEventConsumer} so that a
     * receipt and the ledger row behind the same booking cannot resolve two different rows — see
     * {@link BrokerageTerms}, {@code decisions.md} D56.
     *
     * <p>The refusal stays here rather than in the selector because the two callers refuse
     * differently: 503 is right for a customer reading a receipt, and the consumer needs a throw the
     * container will retry.
     */
    private BrokerageConfig inForce(Instant at) {
        return terms
            .inForceAt(at)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no brokerage configuration in force"));
    }
}
