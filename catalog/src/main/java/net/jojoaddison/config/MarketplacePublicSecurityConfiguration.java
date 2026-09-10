package net.jojoaddison.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the public marketplace reads, which spec §6 requires to work without a token: "Public reads
 * need no token; everything else is role-scoped at the gateway."
 *
 * <h2>Why a separate filter chain rather than an edit to SecurityConfiguration</h2>
 *
 * <p>The generated {@code SecurityConfiguration} closes {@code /api/**} to anonymous callers. The
 * obvious fix is to add {@code permitAll()} rules to it — but it is a generated file, and
 * regenerating the catalog from JDL rewrites generated files and discards every edit to them. That
 * failure would be quiet in the worst way: Browse and Discover would start returning 401 to
 * anonymous visitors, which reads as a gateway routing fault rather than a lost config line.
 *
 * <p>A new file with its own higher-precedence chain survives regeneration. This chain claims only
 * the handful of public GET paths; everything else falls through to the generated chain unchanged.
 *
 * <h2>The {@code @Order} is on the {@code @Bean} method — {@code decisions.md} D77</h2>
 *
 * <p>It sat on this class until then, where <strong>Spring cannot read it</strong>: the comparator is
 * offered the factory method and the bean type and never the declaring class, so
 * {@code findAnnotationOnBean} answered {@code null} and what actually put this chain in front of the
 * generated one was component-scan order — {@code M} sorting before {@code S}. Measured in this
 * service rather than ported from the gateway's equivalent (D52's rule), and the reason it matters is
 * that the generated chain declares <strong>no {@code securityMatcher}</strong> and therefore claims
 * every one of the paths below as well. Whichever chain comes first is the only one that runs, so a
 * class rename here is Browse and Discover answering 401 to anonymous visitors — the exact silent
 * failure the section above exists to avoid. {@code FilterChainPrecedenceIT} is the guard.
 *
 * <h2>Scope</h2>
 *
 * <p>Read-only, and narrow on purpose. {@code /api/reviews/count} is listed explicitly rather than
 * as {@code /api/reviews/**} so that {@code POST /api/reviews} — which spec §9 requires to prove a
 * COMPLETED booking backs it — stays authenticated.
 */
@Configuration
public class MarketplacePublicSecurityConfiguration {

    /** The public read surface of the catalog, taken from spec §6's "Public / customer" table. */
    static final String[] PUBLIC_GET_PATHS = {
        "/api/categories",
        "/api/professionals",
        "/api/professionals/count",
        "/api/professionals/facets",
        "/api/professionals/*",
        "/api/professionals/*/availability",
        "/api/professionals/*/reviews",
        "/api/reviews/count",
    };

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain publicMarketplaceFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(PUBLIC_GET_PATHS)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz ->
                authz
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                    .permitAll()
                    // Anything else that happens to match these paths — a POST or a DELETE — is
                    // not part of the public contract and must not slip through with it.
                    .anyRequest()
                    .denyAll()
            );
        return http.build();
    }
}
