package net.jojoaddison.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import java.util.Arrays;

/**
 * Lets the public marketplace reads through the gateway without a token.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>Spec §6 says "Public reads need no token; everything else is role-scoped at the gateway."
 * The catalog service honours that — but the generated gateway
 * {@code SecurityConfiguration} ends its rules with
 * {@code .pathMatchers("/services/**").authenticated()}, which rejects Discover, Browse and every
 * professional profile with a 401 <em>before routing</em>. The catalog's own permissive rules never
 * get a chance to apply, so "public" was only true if you bypassed the gateway — which no real
 * client does.
 *
 * <h2>Why a separate chain</h2>
 *
 * <p>{@code SecurityConfiguration} is generated, and regenerating the gateway from JDL discards
 * edits to generated files. A lost {@code permitAll} line here would present as anonymous visitors
 * suddenly getting 401s from Browse — a symptom that reads like a routing or JWT fault and sends
 * you looking in the wrong place entirely. A new file with a higher-precedence chain survives
 * regeneration.
 *
 * <h2>Scope</h2>
 *
 * <p>GET only, catalog only, and the paths are listed individually rather than as a wildcard over
 * the service. {@code POST /services/healthconnectcatalog/api/reviews} must stay authenticated —
 * spec §9 requires it to prove a COMPLETED booking backs the review — so
 * {@code /api/reviews/count} is named explicitly instead of matching {@code /api/reviews/**}.
 */
@Configuration
public class MarketplacePublicRouteConfiguration {

    /** Mirrors catalog's own public surface, prefixed with the gateway's service route. */
    static final String[] PUBLIC_GET_PATHS = {
        "/services/healthconnectcatalog/api/categories",
        "/services/healthconnectcatalog/api/professionals",
        "/services/healthconnectcatalog/api/professionals/count",
        "/services/healthconnectcatalog/api/professionals/facets",
        "/services/healthconnectcatalog/api/professionals/*",
        "/services/healthconnectcatalog/api/professionals/*/availability",
        "/services/healthconnectcatalog/api/professionals/*/reviews",
        "/services/healthconnectcatalog/api/reviews/count",
    };

    /**
     * On the method, not on the class — see {@link PaymentWebhookRouteConfiguration} for the measurement.
     *
     * <p>A class-level {@code @Order} on a {@code @Bean} factory orders nothing: the comparator is
     * offered the factory method and the bean type, never the declaring configuration class. This
     * chain was running ahead of the generated one on component-scan order alone, which is a property
     * of the class's name rather than of anything written down.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityWebFilterChain publicMarketplaceRouteFilterChain(ServerHttpSecurity http) {
        ServerWebExchangeMatcher publicGets = new OrServerWebExchangeMatcher(
            Arrays.stream(PUBLIC_GET_PATHS)
                .map(pattern -> (ServerWebExchangeMatcher) new PathPatternParserServerWebExchangeMatcher(pattern, HttpMethod.GET))
                .toList()
        );
        http
            .securityMatcher(publicGets)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(authz -> authz.anyExchange().permitAll());
        return http.build();
    }
}
