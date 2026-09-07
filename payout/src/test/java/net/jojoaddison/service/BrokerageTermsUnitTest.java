package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * One selector, and a tie-break that does not depend on the order rows came back in —
 * {@code decisions.md} D56, backlog NEW-16.
 *
 * <h2>Why the repository is mocked here and real in the IT beside it</h2>
 *
 * <p>Because the defect being closed <em>is</em> a dependence on row order.
 * {@code Stream.max(comparing(effectiveFrom))} keeps the first maximal element it sees, so two
 * configs sharing an {@code effectiveFrom} resolved to whichever the database happened to return
 * first. A test against a real table can only assert the answer for the one order that table
 * produced — which is exactly the evidence that made the old code look correct. Controlling the list
 * lets both orders be asserted in one run, and it is the pair of them that says anything.
 *
 * <p>Every instant is in 2019–2022 for {@code TheRateIsStruckWhenTheBookingHappenedTest}'s reason:
 * an asserted moment that a real clock could equal is an assertion that holds on some days.
 */
@ExtendWith(MockitoExtension.class)
class BrokerageTermsUnitTest {

    private static final Instant OLD_FROM = Instant.parse("2019-01-01T00:00:00Z");
    private static final Instant NEW_FROM = Instant.parse("2021-01-01T00:00:00Z");
    private static final Instant FUTURE_FROM = Instant.parse("2022-01-01T00:00:00Z");

    /** Between the two, and years before any clock this will ever run under. */
    private static final Instant A_BOOKING_IN_2020 = Instant.parse("2020-06-01T09:30:00Z");

    @Mock
    private BrokerageConfigRepository configs;

    private static BrokerageConfig config(long id, String rate, Instant effectiveFrom) {
        BrokerageConfig config = new BrokerageConfig()
            .commissionRate(new BigDecimal(rate))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(effectiveFrom);
        config.setId(id);
        return config;
    }

    private BrokerageTerms over(List<BrokerageConfig> rows) {
        when(configs.findAll()).thenReturn(rows);
        return new BrokerageTerms(configs);
    }

    @Test
    @DisplayName("the latest terms that had already taken effect, not simply the newest row")
    void effectiveDatingIsRespected() {
        BrokerageTerms terms = over(List.of(config(1, "0.12", OLD_FROM), config(2, "0.30", NEW_FROM), config(3, "0.44", FUTURE_FROM)));

        assertThat(terms.inForceAt(A_BOOKING_IN_2020)).get().extracting(BrokerageConfig::getCommissionRate).isEqualTo(new BigDecimal("0.12"));
        assertThat(terms.inForceAt(Instant.parse("2021-06-01T00:00:00Z")))
            .get()
            .extracting(BrokerageConfig::getCommissionRate)
            .isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    @DisplayName("terms that had not yet taken effect price nothing")
    void nothingInForceYetIsEmpty() {
        BrokerageTerms terms = over(List.of(config(1, "0.12", OLD_FROM)));

        assertThat(terms.inForceAt(Instant.parse("2018-01-01T00:00:00Z"))).isEmpty();
    }

    @Test
    @DisplayName("an empty table is empty rather than an arbitrary answer")
    void noTermsAtAllIsEmpty() {
        assertThat(over(List.of()).inForceAt(A_BOOKING_IN_2020)).isEmpty();
    }

    @Test
    @DisplayName("a row with no effectiveFrom cannot be in force")
    void anUndatedRowIsIgnored() {
        BrokerageConfig undated = config(9, "0.99", OLD_FROM).effectiveFrom(null);

        assertThat(over(List.of(undated, config(1, "0.12", OLD_FROM))).inForceAt(A_BOOKING_IN_2020))
            .get()
            .extracting(BrokerageConfig::getCommissionRate)
            .isEqualTo(new BigDecimal("0.12"));
    }

    @Test
    @DisplayName("two configs sharing an effectiveFrom: the newest row wins, whichever order they arrive in")
    void theTieBreakDoesNotDependOnRowOrder() {
        BrokerageConfig older = config(10, "0.30", NEW_FROM);
        BrokerageConfig newer = config(11, "0.55", NEW_FROM);
        Instant afterBoth = Instant.parse("2021-06-01T00:00:00Z");

        // Ascending id, which is what a plain `select * from brokerage_config` tends to give and is
        // therefore the order the old code was written against. Stream.max keeps the FIRST maximal
        // element, so this order used to answer 0.30.
        assertThat(over(List.of(older, newer)).inForceAt(afterBoth))
            .get()
            .extracting(BrokerageConfig::getCommissionRate)
            .isEqualTo(new BigDecimal("0.55"));

        // And descending, which the old code also answered — with the other row. Two orders, two
        // answers, one JVM: that is the receipt and the ledger disagreeing with no rate change
        // between them, and it is the whole reason there is one selector now.
        assertThat(over(List.of(newer, older)).inForceAt(afterBoth))
            .get()
            .extracting(BrokerageConfig::getCommissionRate)
            .isEqualTo(new BigDecimal("0.55"));
    }

    @Test
    @DisplayName("an unsaved config never outranks a persisted one on a tie")
    void aNullIdSortsFirst() {
        BrokerageConfig unsaved = new BrokerageConfig()
            .commissionRate(new BigDecimal("0.99"))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(NEW_FROM);
        BrokerageConfig persisted = config(10, "0.30", NEW_FROM);
        Instant afterBoth = Instant.parse("2021-06-01T00:00:00Z");

        // Both orders again, because the point is that the comparator decides and not the list.
        // Comparator.naturalOrder() alone would throw a NullPointerException here rather than choose.
        assertThat(over(List.of(unsaved, persisted)).inForceAt(afterBoth))
            .get()
            .extracting(BrokerageConfig::getCommissionRate)
            .isEqualTo(new BigDecimal("0.30"));
        assertThat(over(List.of(persisted, unsaved)).inForceAt(afterBoth))
            .get()
            .extracting(BrokerageConfig::getCommissionRate)
            .isEqualTo(new BigDecimal("0.30"));
    }
}
