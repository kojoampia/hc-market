package net.jojoaddison.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The brokerage fee calculation.
 *
 * <p>Its own class because two callers need it and they must agree: the seeder, writing 256
 * historic ledger rows, and the Kafka consumer, writing new ones as bookings complete. Had this
 * stayed a helper on the seeder, the consumer would have grown its own copy and the two would have
 * drifted the first time the rounding rule was touched — with the symptom being historic and live
 * earnings that disagree by a few pesewas and no obvious reason why.
 */
public final class Commission {

    private Commission() {}

    /**
     * Commission on one booking, in minor units.
     *
     * <p>Rounded <strong>per booking</strong> and never on a total. With the seeded prices every
     * result is an exact integer so the two agree today, but they diverge the moment a price appears
     * that does not divide cleanly — and a total-first calculation would then disagree with the sum
     * of the receipts the customers were actually shown. The receipts are the truth.
     *
     * <p>HALF_UP rather than HALF_EVEN: this is money owed to a person, and the rule that matches
     * what a receipt shows is the one to use. Banker's rounding is for statistics, not invoices.
     */
    public static long on(long grossMinor, BigDecimal rate) {
        return BigDecimal.valueOf(grossMinor).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** The fee a late cancellation earns the professional, before commission. */
    public static long lateCancellationFee(long priceMinor, BigDecimal pct) {
        return BigDecimal.valueOf(priceMinor).multiply(pct).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
