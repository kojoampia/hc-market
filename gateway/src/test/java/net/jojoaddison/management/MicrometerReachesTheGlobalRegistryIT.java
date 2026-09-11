package net.jojoaddison.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The half of NEW-44 that is about this application — {@code decisions.md} D85.
 *
 * <p>The agent's Micrometer bridge works by adding an OpenTelemetry-backed registry to
 * {@link Metrics#globalRegistry}, so a meter is carried <strong>if and only if it reaches that
 * registry</strong>. {@code deploy/verify-micrometer-otlp-bridge.sh} measures the agent's side —
 * measured, the bridge works and is opt-in, off without
 * {@code OTEL_INSTRUMENTATION_MICROMETER_ENABLED=true}. This asserts <em>our</em> side: that
 * {@code gateway.identity.*} is registered somewhere the agent would find it.
 *
 * <p><strong>Both must hold, and each is invisible from the other.</strong> A working bridge over a
 * registry nothing publishes to carries nothing; meters on a standalone registry are not carried however
 * well the bridge works. The failure is the same in both cases — panels of "No data" — and neither the
 * script nor this test can see the other's half.
 *
 * <p><strong>Why this is asserted rather than read off a property.</strong> Boot's
 * {@code management.metrics.use-global-registry} defaults to true and is set nowhere here, so the
 * property file says nothing; and "the default is true" is a claim about a framework version, which is
 * exactly the class of claim D63 was created by. The registry the container actually built is the only
 * honest subject.
 */
@IntegrationTest
class MicrometerReachesTheGlobalRegistryIT {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private GatewayIdentityMeters meters;

    @Test
    @DisplayName("the container's MeterRegistry is attached to Metrics.globalRegistry, which is what the agent bridges")
    void theApplicationRegistryIsTheGlobalOne() {
        assertThat(Metrics.globalRegistry.getRegistries())
            .as(
                "the OpenTelemetry agent's micrometer instrumentation adds its registry to Metrics.globalRegistry, so a " +
                "meter only reaches OTLP if the application's registry is attached there. If this is empty, every " +
                "gateway_identity_* panel reads 'No data' no matter what the transport does — see decisions.md D85."
            )
            .isNotEmpty();
    }

    /**
     * BOTH DIRECTIONS, because a fix for one is the same defect facing the other way.
     *
     * <p>The meters are registered on {@code Metrics.globalRegistry} (D85), so they must be findable
     * <strong>through the composite</strong> — which is what lets the agent's OTel-backed child receive
     * them — <strong>and</strong> on the application's own {@code PrometheusMeterRegistry}, which is what
     * serves {@code /management/prometheus} and is where the dashboard's series names come from.
     *
     * <p>Measured, and this is the whole of NEW-44's application half: a meter registered on a CHILD
     * registry is invisible to the composite and to its sibling children, so D84's original wiring —
     * constructing the meters with the injected bean — published to the endpoint and to nothing else.
     * Every panel would have read "No data" however the transport was configured.
     */
    @Test
    @DisplayName("the identity meters are on the composite AND reach the application's own registry")
    void theMetersAreOnTheCompositeAndReachTheChild() {
        meters.recordLogin(GatewayIdentityMeters.Outcome.SUCCESS);

        assertThat(Metrics.globalRegistry.find(GatewayIdentityMeters.LOGINS_METER).counters())
            .as(
                "registered THROUGH the global composite, which is the only way the agent's OTel-backed child registry " +
                "receives them — a composite does not index what its children register on themselves (decisions.md D85)"
            )
            .isNotEmpty();

        assertThat(registry.find(GatewayIdentityMeters.LOGINS_METER).counters())
            .as(
                "and still present on the application's own registry, which serves /management/prometheus and is where " +
                "GatewayIdentityMetersNamingUnitTest's series names come from. A fix that reached the agent and lost " +
                "this would be the same defect facing the other way."
            )
            .isNotEmpty();

        assertThat(Metrics.globalRegistry.getRegistries())
            .as("and the application's registry must be one of the composite's children, or the forwarding above reaches nothing")
            .contains(registry);
    }
}
