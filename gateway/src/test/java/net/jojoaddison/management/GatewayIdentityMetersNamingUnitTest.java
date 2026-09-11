package net.jojoaddison.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import net.jojoaddison.management.GatewayIdentityMeters.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The series names {@code deploy/observability/} queries — {@code decisions.md} D84, backlog NEW-43.
 *
 * <p>Micrometer renames a meter on its way out: dots become underscores, the base unit is appended, and
 * a counter gains {@code _total}. So the PromQL in the dashboard is a claim about a <strong>transformation
 * of these constants</strong>, not about the constants themselves — and a dashboard whose queries are
 * one underscore wrong is a set of empty panels with nothing anywhere disagreeing.
 *
 * <p>Derived on paper, {@code gateway.identity.logins} with base unit {@code attempts} reads as
 * {@code gateway_identity_logins_attempts_total}. That happens to be right and it was one plausible
 * guess away from {@code gateway_identity_logins_total}. This asserts the exact strings against a real
 * {@link PrometheusMeterRegistry}, so a Micrometer upgrade that changes the convention breaks here
 * rather than silently in Grafana.
 *
 * <p><strong>This is not a claim about transport.</strong> `/management/prometheus` is 404'd at quality's
 * edge and unscraped in production, deliberately (D63/D73). The naming is the registry's and is the same
 * whichever transport carries the metric; where it goes is the open half recorded in NEW-44.
 */
class GatewayIdentityMetersNamingUnitTest {

    @Test
    @DisplayName("the exposition names are exactly what the dashboard's PromQL asks for")
    void expositionNamesMatchTheDashboard() {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        var meters = new GatewayIdentityMeters(registry);

        // every bucket, so no series is absent when a panel asks for it
        for (Outcome outcome : Outcome.values()) {
            meters.recordLogin(outcome);
        }
        meters.setAccounts(7, 3);

        String scrape = registry.scrape();

        assertThat(scrape)
            .as("the logins counter, as the dashboard queries it")
            .contains("gateway_identity_logins_attempts_total{outcome=\"success\"")
            .contains("gateway_identity_logins_attempts_total{outcome=\"bad-credentials\"")
            .contains("gateway_identity_logins_attempts_total{outcome=\"not-activated\"")
            .contains("gateway_identity_logins_attempts_total{outcome=\"error\"");

        assertThat(scrape)
            .as("the account gauges, as the dashboard queries them")
            .contains("gateway_identity_accounts{state=\"activated\"")
            .contains("gateway_identity_accounts{state=\"not-activated\"");

        // THE VALUES, not just the names: a scrape naming the right series with the wrong number is the
        // failure a name-only assertion cannot see.
        assertThat(scrape).contains("gateway_identity_accounts{state=\"activated\"} 7.0");
        assertThat(scrape).contains("gateway_identity_accounts{state=\"not-activated\"} 3.0");
    }
}
