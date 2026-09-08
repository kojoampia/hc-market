package net.jojoaddison.service.seed;

import java.math.BigDecimal;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.repository.LedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the payout service's slice of the seed: one ledger row per completed session.
 *
 * <p>What is <em>not</em> loaded: any total. There is no lifetime gross, no monthly earnings and no
 * per-professional summary anywhere in this schema. Those are SQL aggregates over the rows written
 * here, computed at read time — see {@code EarningsService}.
 *
 * <p><strong>And, since {@code decisions.md} D57, no {@code BrokerageConfig}.</strong> This class wrote
 * one and {@link #clear()} deleted one, which made the estate's commission rate demo data — loadable
 * only under the {@code test & dev} profile pair with {@code healthconnect.seed.enabled} true, and
 * therefore absent from production, where {@code BookingEventConsumer} then retried every
 * {@code booking.completed} for ever and every receipt was a 503 (backlog NEW-18). The founding terms
 * are commercial configuration rather than demo data and belong to
 * {@code net.jojoaddison.service.BrokerageBootstrap}, which writes them on every environment. Two
 * consequences worth stating in place:
 *
 * <ul>
 *   <li>a reseed no longer empties {@code brokerage_config}. It used to, between two transactions, on a
 *       live dev estate — a window in which this service could price nothing;
 *   <li>the rate the seeded ledger rows are computed at still comes from the seed file, because those
 *       amounts are the prototype's figures and {@code extract-seed.mjs} asserts their totals. It is not
 *       read back from the config, and {@link #warnIfTheSeedDisagreesWithTheEstate} is what stops the
 *       two drifting in silence.
 * </ul>
 */
@Service
public class PayoutSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutSeeder.class);

    private final BrokerageConfigRepository brokerageConfigRepository;
    private final LedgerRepository ledgerRepository;

    public PayoutSeeder(BrokerageConfigRepository brokerageConfigRepository, LedgerRepository ledgerRepository) {
        this.brokerageConfigRepository = brokerageConfigRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public boolean alreadySeeded() {
        return ledgerRepository.count() > 0;
    }

    /**
     * Truncates what the seed owns, which since D57 is the ledger and nothing else.
     *
     * <p>{@code brokerageConfigRepository.deleteAllInBatch()} used to be the second line here, and
     * removing it is the point rather than tidying: the founding terms are not seed data, and a reseed
     * that empties {@code brokerage_config} is {@code deploy-dev.sh reseed} recreating NEW-18 on a
     * running estate. {@code BrokerageBootstrap} only writes at startup, so nothing would have put the
     * row back until the next restart.
     */
    @Transactional
    public void clear() {
        ledgerRepository.deleteAllInBatch();
    }

    @Transactional
    public void load(SeedFile seed, boolean anchorDates) {
        // decisions.md D48. Not LocalDate.now(): the calendar is the estate's, and the four seeded
        // services have to arrive at the same number or their dates stop lining up with each other.
        // ledger.earned_on is read against booking's completed_at, which the lifetime-earnings
        // aggregate sums over, so a day of disagreement here is a day of earnings in the wrong month.
        long shiftDays = SeedCalendar.shiftDays(seed.meta().demoToday(), anchorDates);
        if (shiftDays != 0) {
            // The day is DERIVED from the shift rather than read from the clock a second time: two
            // reads either side of Accra midnight would print a date the seed was not loaded against,
            // in the one log line whose job is to explain a disagreement between services.
            LOG.info(
                "shifting every seed date by {} days: {} -> {} in {}",
                shiftDays,
                seed.meta().demoToday(),
                seed.meta().demoToday().plusDays(shiftDays),
                SeedCalendar.SEED_ZONE
            );
        } else {
            // A run that shifts nothing used to log nothing at all, and "no line" is ambiguous between
            // the two ways of getting here. The quality box is always the first of them, so the one
            // estate anybody audits said nothing whatever about its own calendar. One line, naming
            // which case and the zone, is enough to read it off the log.
            LOG.info(
                "seed dates unshifted ({}): loading them as written against {} in {}",
                anchorDates ? "anchored" : "today IS the demo day",
                seed.meta().demoToday(),
                SeedCalendar.SEED_ZONE
            );
        }

        SeedFile.Brokerage b = seed.brokerage();
        warnIfTheSeedDisagreesWithTheEstate(b.commissionRate());

        long gross = 0;
        long commission = 0;
        for (SeedFile.SeedSession s : seed.sessions()) {
            long grossMinor = s.grossMinor();
            long commissionMinor = net.jojoaddison.service.Commission.on(grossMinor, b.commissionRate());
            gross += grossMinor;
            commission += commissionMinor;
            ledgerRepository.save(
                new Ledger()
                    .bookingReference(s.ref())
                    .professionalRef(s.professionalRef())
                    .professionalLogin(s.professionalLogin())
                    .grossMinor(grossMinor)
                    .commissionMinor(commissionMinor)
                    .netMinor(grossMinor - commissionMinor)
                    .currency(s.currency())
                    .deliveryMode(DeliveryMode.valueOf(s.deliveryMode()))
                    .serviceRef(s.serviceRef())
                    .serviceName(s.serviceName())
                    .earnedOn(s.completedDate().plusDays(shiftDays))
            );
        }
        LOG.info("seeded {} ledger entries — gross {} commission {} net {}", seed.sessions().size(), gross, commission, gross - commission);
    }

    /**
     * Says so when the rate the seeded ledger was computed at is not a rate this estate holds.
     *
     * <p>Two numbers that must agree, in two places, with nothing comparing them — the shape this
     * repository keeps rediscovering. Before D57 they could not disagree, because this class wrote both;
     * now the founding rate is a deployment input ({@code HC_BROKERAGE_COMMISSION_RATE}) and the seed's
     * is the prototype's, so an estate started with a different founding rate gets 256 seeded ledger
     * rows priced at 12% and every new booking priced at something else. Both sets of rows are
     * internally consistent and no count moves, which is exactly why it needs a line in the log.
     *
     * <p>A warning and not a refusal: it is a dev and quality condition by construction (production
     * never seeds), the seeded amounts are the prototype's acceptance figures and must not move, and
     * nothing about it is unsafe — only confusing.
     */
    private void warnIfTheSeedDisagreesWithTheEstate(BigDecimal seedRate) {
        List<BrokerageConfig> held = brokerageConfigRepository.findAll();
        if (held.stream().anyMatch(c -> c.getCommissionRate() != null && c.getCommissionRate().compareTo(seedRate) == 0)) {
            return;
        }
        LOG.warn(
            "the seed prices every ledger row at a commission of {}, which no BrokerageConfig in this " +
            "estate carries ({}) — the seeded history and every new booking will be priced differently. " +
            "The founding rate is HC_BROKERAGE_COMMISSION_RATE; the seed's is the prototype's and cannot " +
            "move (decisions.md D57)",
            seedRate.toPlainString(),
            held.stream().map(c -> String.valueOf(c.getCommissionRate())).toList()
        );
    }
}
