package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single fact {@code deploy-prod.sh}'s smoke test reads — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>Why the deploy reads this and not the aggregate health</h2>
 *
 * <p>Because the aggregate answers about everything. A real {@code prod} boot against an empty
 * throwaway database reported {@code DOWN} on an estate whose founding row was present and correct,
 * because {@code binders.kafka} was down with no broker on the machine — so a smoke test reading it
 * would have failed the deploy of a healthy stack over a broker blip, and a failing smoke test rolls
 * back (D49). {@code termsInForce} is about one thing.
 *
 * <p>{@code deploy-prod.sh} greps for {@code "termsInForce": true} with whitespace tolerated, and CI
 * asserts that it still does, that this class still exists, and that payout still exposes {@code info}.
 * The key name is therefore load-bearing across two repositories' worth of distance: <strong>renaming
 * it breaks the deploy, not this test</strong>, which is why the literal is asserted here rather than
 * only the value.
 */
class BrokerageTermsInfoContributorUnitTest {

    private final BrokerageConfigRepository configs = mock(BrokerageConfigRepository.class);

    private final BrokerageTermsInfoContributor contributor = new BrokerageTermsInfoContributor(new BrokerageTerms(configs));

    @Test
    @DisplayName("a bootstrapped estate says so, and says what it charges")
    void aBootstrappedEstateSaysSo() {
        when(configs.findAll()).thenReturn(List.of(founding()));

        assertThat(contributor.brokerage())
            .containsEntry("termsInForce", true)
            .containsEntry("commissionRate", "0.12")
            .containsEntry("currency", "GHS")
            .containsEntry("effectiveFrom", "1970-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("an empty brokerage_config says so too, and volunteers no rate it does not have")
    void anEmptyTableSaysSo() {
        when(configs.findAll()).thenReturn(List.of());

        assertThat(contributor.brokerage()).containsEntry("termsInForce", false).containsOnlyKeys("termsInForce");
    }

    @Test
    @DisplayName("a table holding only FUTURE terms says false, which a count could not")
    void aFutureDatedRowSaysFalse() {
        // Mutating only the dating: the row exists, so a count reports a configured estate, while the
        // consumer and the receipt both find nothing in force and fail exactly as on an empty table.
        when(configs.findAll()).thenReturn(List.of(config(Instant.parse("2999-01-01T00:00:00Z"))));

        assertThat(contributor.brokerage()).containsEntry("termsInForce", false);
    }

    private static BrokerageConfig founding() {
        return config(FoundingTerms.EFFECTIVE_FROM);
    }

    private static BrokerageConfig config(Instant effectiveFrom) {
        return new BrokerageConfig()
            .commissionRate(new BigDecimal("0.12"))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(effectiveFrom);
    }
}
