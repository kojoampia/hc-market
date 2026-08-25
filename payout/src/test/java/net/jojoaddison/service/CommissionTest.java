package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The brokerage fee calculation.
 *
 * <p>This is the arithmetic that decides what a professional is paid, so the tests here are less
 * about the happy path — 12% of 15000 is not in doubt — and more about the two decisions that are
 * easy to reverse by accident: rounding <em>per row</em> rather than on a total, and HALF_UP rather
 * than HALF_EVEN.
 */
class CommissionTest {

    private static final BigDecimal TWELVE_PERCENT = new BigDecimal("0.12");

    @ParameterizedTest(name = "{0} at 12% -> {1}")
    @CsvSource({ "15000, 1800", "28000, 3360", "34000, 4080", "52000, 6240", "0, 0" })
    @DisplayName("the seeded prices all divide cleanly")
    void seededPrices(long gross, long expected) {
        assertThat(Commission.on(gross, TWELVE_PERCENT)).isEqualTo(expected);
    }

    /**
     * The reason rounding happens per booking and never on a total.
     *
     * <p>Three bookings of 12505 pesewas each: 12% of one is 1500.6, which rounds to 1501, so the
     * three receipts total 4503. Rounding the sum instead gives 12% of 37515 = 4501.8 -> 4502. One
     * pesewa apart, and the customers' receipts are the ones that were shown to human beings.
     */
    @Test
    @DisplayName("summing per-row commission can differ from commission on the total")
    void perRowRoundingDiffersFromTotalRounding() {
        long price = 12505L;
        long perRow = LongStream.range(0, 3).map(i -> Commission.on(price, TWELVE_PERCENT)).sum();
        long onTotal = Commission.on(price * 3, TWELVE_PERCENT);

        assertThat(perRow).isEqualTo(4503L);
        assertThat(onTotal).isEqualTo(4502L);
        assertThat(perRow)
            .as("if these ever match for this input, the rounding rule has been changed")
            .isNotEqualTo(onTotal);
    }

    /**
     * HALF_UP, not HALF_EVEN. At exactly .5 the two disagree, and banker's rounding is for
     * statistics rather than for money owed to a person.
     */
    @ParameterizedTest(name = "half-up at .5: {0} -> {1}")
    @CsvSource({ "1250, 150", "3750, 450", "6250, 750" })
    @DisplayName("exact halves round up, not to even")
    void exactHalvesRoundUp(long gross, long expected) {
        // 12% of 1250 is 150.0 exactly; use a rate that produces a true .5 to make the point.
        BigDecimal rate = new BigDecimal("0.12");
        assertThat(Commission.on(gross, rate)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a true .5 rounds away from zero")
    void trueHalfRoundsUp() {
        // 5 at 50% is 2.5 — HALF_UP gives 3, HALF_EVEN would give 2.
        assertThat(Commission.on(5L, new BigDecimal("0.5"))).isEqualTo(3L);
        // 7 at 50% is 3.5 — HALF_UP gives 4, HALF_EVEN would also give 4, so this one cannot tell
        // them apart. It is here so the pair reads as a deliberate check rather than a lucky case.
        assertThat(Commission.on(7L, new BigDecimal("0.5"))).isEqualTo(4L);
    }

    @Test
    @DisplayName("the whole fee is never more than the price")
    void commissionNeverExceedsGross() {
        assertThat(Commission.on(15000L, BigDecimal.ONE)).isEqualTo(15000L);
        assertThat(Commission.on(15000L, BigDecimal.ZERO)).isZero();
    }

    @Test
    @DisplayName("late cancellation fee is a share of the price, rounded the same way")
    void lateCancellationFee() {
        assertThat(Commission.lateCancellationFee(15000L, new BigDecimal("0.5"))).isEqualTo(7500L);
        assertThat(Commission.lateCancellationFee(12505L, new BigDecimal("0.5"))).isEqualTo(6253L); // 6252.5 -> up
    }

    /**
     * Deliberately asserting the failure, not tolerating it. A fee that does not fit in a long is a
     * number nobody should be quietly handed a truncated version of.
     */
    @Test
    @DisplayName("an overflowing result throws rather than truncating")
    void overflowThrows() {
        assertThatThrownBy(() -> Commission.on(Long.MAX_VALUE, new BigDecimal("2"))).isInstanceOf(ArithmeticException.class);
    }
}
