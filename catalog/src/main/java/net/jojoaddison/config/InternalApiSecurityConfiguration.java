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
 * The filter chain for {@code /internal/**} — the estate-facing surface described in
 * {@code decisions.md} D28.
 *
 * <h2>This chain is not what protects these endpoints</h2>
 *
 * <p>It permits the GETs. What keeps {@code /internal/**} off the internet is that the gateway's
 * four routes match {@code /services/<service>/api/**} and nothing else, so no request from outside
 * can be routed here in any environment. Say it plainly, because a reader who sees {@code permitAll}
 * and stops there will draw the wrong conclusion: <strong>widen those route predicates back to
 * {@code /services/<service>/**} and this is public.</strong>
 *
 * <p>It exists at all for two reasons. The generated {@code SecurityConfiguration} matches
 * {@code /api/**}, {@code /v3/api-docs/**} and {@code /management/**} — a path under
 * {@code /internal/} matches none of them, and what Spring Security does with an unmatched request
 * is a version-dependent detail nobody should have to look up to know whether this returns 200 or
 * 401. And a token cannot be required here anyway: booking calls this and holds no credential of
 * its own, because there is no service-to-service authentication in this estate.
 *
 * <p>Everything that is not a GET is denied, so the prefix cannot quietly acquire a write endpoint
 * without someone changing this file.
 *
 * <h2>A new file, on purpose</h2>
 *
 * <p>Exactly as {@link MarketplacePublicSecurityConfiguration} is. Regenerating the catalog from
 * JDL rewrites {@code SecurityConfiguration} and discards edits to it; the failure would be silent
 * in the direction that matters least here but the pattern is worth keeping uniform.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InternalApiSecurityConfiguration {

    /** Everything the estate may ask catalog directly. One entry, and it should stay short. */
    static final String INTERNAL_PATHS = "/internal/**";

    @Bean
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(INTERNAL_PATHS)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.requestMatchers(HttpMethod.GET, INTERNAL_PATHS).permitAll().anyRequest().denyAll());
        return http.build();
    }
}
