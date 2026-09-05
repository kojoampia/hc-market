package net.jojoaddison.service.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
 * Loads the payout service's slice of the seed: the brokerage configuration, and one ledger row per
 * completed session.
 *
 * <p>What is <em>not</em> loaded: any total. There is no lifetime gross, no monthly earnings and no
 * per-professional summary anywhere in this schema. Those are SQL aggregates over the rows written
 * here, computed at read time — see {@code EarningsService}.
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

    @Transactional
    public void clear() {
        ledgerRepository.deleteAllInBatch();
        brokerageConfigRepository.deleteAllInBatch();
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
        BrokerageConfig config = new BrokerageConfig()
            .commissionRate(b.commissionRate())
            .payoutLagDays(b.payoutLagDays())
            .freeCancellationHours(b.freeCancellationHours())
            .lateCancellationPct(b.lateCancellationPct())
            .currency(b.currency())
            // Backdated so that every seeded session completed *after* the config took effect —
            // the whole point of versioning by effectiveFrom is that a booking prices against the
            // config in force when it completed, and a config that starts today would price none.
            .effectiveFrom(Instant.parse("2020-01-01T00:00:00Z"));
        brokerageConfigRepository.save(config);

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

}
