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
import org.springframework.boot.health.contributor.Status;

/**
 * Payout says out loud when it cannot price anything — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>Why there is anything to say</h2>
 *
 * <p>NEW-18's whole character is that the estate stayed green while it was broken: booking was happy,
 * the consumer retried in its own log, the catalogue smoke test passed, every container reported ready.
 * This indicator is the one thing that would have made a deploy able to notice, and
 * {@code deploy-prod.sh}'s smoke test is what asks it.
 *
 * <h2>Three states, and the third is the one a count would miss</h2>
 *
 * <p>The question is "is anything in force", not "is the table non-empty". A table holding only a
 * future-dated row prices nothing either, and the failure is indistinguishable from an empty one at the
 * consumer and at the receipt — D53 and D56 both select the latest config whose {@code effectiveFrom}
 * has already passed. Each of the three is asserted on its own.
 */
class BrokerageTermsHealthIndicatorUnitTest {

    private final BrokerageConfigRepository configs = mock(BrokerageConfigRepository.class);

    private final BrokerageTermsHealthIndicator indicator = new BrokerageTermsHealthIndicator(new BrokerageTerms(configs));

    private static BrokerageConfig config(Instant effectiveFrom) {
        return new BrokerageConfig()
            .commissionRate(new BigDecimal("0.12"))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(effectiveFrom);
    }

    @Test
    @DisplayName("a bootstrapped estate is UP, and says what it is charging")
    void theFoundingRowIsUp() {
        when(configs.findAll()).thenReturn(List.of(config(FoundingTerms.EFFECTIVE_FROM)));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("commissionRate", "0.12").containsEntry("currency", "GHS");
    }

    @Test
    @DisplayName("an empty brokerage_config is DOWN — the whole of NEW-18")
    void anEmptyTableIsDown() {
        when(configs.findAll()).thenReturn(List.of());

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason").toString()).contains("no BrokerageConfig is in force");
    }

    @Test
    @DisplayName("a table holding only FUTURE terms is DOWN too, which a count cannot see")
    void aFutureDatedRowIsDown() {
        // Mutating only the dating. The row exists, so `count() > 0` reports a configured estate; the
        // consumer and the receipt both find nothing in force and fail exactly as they do on an empty
        // table. Reachable by hand, and by a founding row dated to anything but the beginning — which
        // is why FoundingTerms.EFFECTIVE_FROM is the epoch and is not a deployment input.
        when(configs.findAll()).thenReturn(List.of(config(Instant.parse("2999-01-01T00:00:00Z"))));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
