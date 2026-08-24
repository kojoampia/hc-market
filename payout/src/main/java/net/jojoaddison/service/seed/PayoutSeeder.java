package net.jojoaddison.service.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        long shiftDays = anchorDates ? 0 : ChronoUnit.DAYS.between(seed.meta().demoToday(), LocalDate.now());

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
            long commissionMinor = commissionOn(grossMinor, b.commissionRate());
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
     * Commission on one booking, in minor units.
     *
     * <p>Rounded <strong>per row</strong> and never on a total. With the seeded prices every result
     * is an exact integer, so the two agree today — but they stop agreeing the moment a price
     * appears that does not divide cleanly, and at that point a total-first calculation would
     * disagree with the sum of the receipts the customers were shown. The receipts are the truth.
     *
     * <p>HALF_UP, not HALF_EVEN: this is money owed to a person, and the rule that matches what a
     * receipt shows is the one to use.
     */
    static long commissionOn(long grossMinor, BigDecimal rate) {
        return BigDecimal.valueOf(grossMinor).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
