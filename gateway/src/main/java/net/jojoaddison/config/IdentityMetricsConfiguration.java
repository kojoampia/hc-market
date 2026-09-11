package net.jojoaddison.config;

import io.micrometer.core.instrument.Metrics;
import net.jojoaddison.management.GatewayIdentityMeters;
import net.jojoaddison.security.CountingReactiveAuthenticationManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.ReactiveAuthenticationManager;

/**
 * Puts {@link CountingReactiveAuthenticationManager} in front of the generated one —
 * {@code decisions.md} D84, backlog NEW-43.
 *
 * <p><strong>A new file, on purpose.</strong> The manager it decorates is a {@code @Bean} in the
 * generated {@code SecurityConfiguration}, which {@code jhipster jdl --force} rewrites wholesale, so
 * the decoration cannot live there. This is the same move as
 * {@code MarketplacePublicSecurityConfiguration} and {@code InternalApiSecurityConfiguration}: leave
 * the generated file alone and add a bean beside it.
 *
 * <p><strong>{@code @Primary} rather than a replacement</strong>, because the generated bean must keep
 * existing: it is the delegate. Two beans of type {@link ReactiveAuthenticationManager} are then in the
 * context and {@code AuthenticateController}, which injects by type, gets this one.
 *
 * <p><strong>The delegate is injected by NAME and that is load-bearing.</strong> Without the
 * {@code @Qualifier} this method's own return type makes it a candidate for its own parameter, and
 * {@code @Primary} would resolve the parameter to the bean being defined — a self-referential
 * definition. The name {@code reactiveAuthenticationManager} is the generated method's, so a JHipster
 * version that renames it fails the context loudly at startup rather than quietly counting nothing.
 *
 * <p><strong>There is deliberately no switch.</strong> Four counters and two gauges cost nothing to
 * keep, and a flag would only add a state in which the dashboard is blank for a reason nobody can see
 * from the dashboard. The estate-wide switch that does exist — whether anything <em>collects</em> these
 * metrics — is {@code HC_OTEL_JAVA_OPTS} and the collector, and it is one layer up (D73 §3).
 */
@Configuration
public class IdentityMetricsConfiguration {

    /**
     * The meters, on {@code Metrics.globalRegistry} and deliberately not on the injected bean —
     * {@code decisions.md} D85, backlog NEW-44.
     *
     * <p>The injected {@code MeterRegistry} is the {@code PrometheusMeterRegistry}, which Boot also
     * attaches as a <em>child</em> of the global composite. A composite forwards only what is registered
     * through it, so a meter created on that child is visible to the exposition endpoint and to nothing
     * else — including the OTel-backed registry the agent adds as a sibling child. Measured both ways;
     * see {@link GatewayIdentityMeters}'s javadoc for the table.
     *
     * <p>Registering on the composite reaches <strong>every</strong> child, so the exposition still
     * serves these series AND the agent's bridge can carry them. {@code MicrometerReachesTheGlobalRegistryIT}
     * asserts both halves, because a fix that reached the agent and lost the endpoint would be the same
     * defect facing the other way.
     */
    @Bean
    public GatewayIdentityMeters gatewayIdentityMeters() {
        return new GatewayIdentityMeters(Metrics.globalRegistry);
    }

    @Bean
    @Primary
    public ReactiveAuthenticationManager countingReactiveAuthenticationManager(
        @Qualifier("reactiveAuthenticationManager") ReactiveAuthenticationManager delegate,
        GatewayIdentityMeters meters
    ) {
        return new CountingReactiveAuthenticationManager(delegate, meters);
    }
}
