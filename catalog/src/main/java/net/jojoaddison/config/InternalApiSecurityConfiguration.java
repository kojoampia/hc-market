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
 * <p>It exists at all because this chain <strong>opens</strong> a door the generated one closes, and
 * that is the opposite of what this paragraph said until {@code decisions.md} D77. The claim here was
 * that the generated {@code SecurityConfiguration} "matches {@code /api/**}, {@code /v3/api-docs/**}
 * and {@code /management/**}" so that a path under {@code /internal/} matches none of them, and that
 * what Spring Security then does is "a version-dependent detail nobody should have to look up".
 * <strong>Both halves were wrong.</strong> Those three are {@code authorizeHttpRequests} rules, which
 * narrow authorization and not the chain; the generated chain declares no {@code securityMatcher} at
 * all, so it matches <em>any request</em> and claims this prefix too. And the detail was looked up, on
 * this stack, by measurement: a path its rules never name is <strong>401</strong>, not 200 — so
 * without this file booking's lookup is refused rather than merely unmatched.
 * {@code FilterChainPrecedenceIT} pins both facts.
 *
 * <p>A token cannot be required here anyway: booking calls this and holds no credential of its own
 * for it, because this estate has no service-to-service authentication on this path (D38 gave booking
 * two credentials, and neither is presented here).
 *
 * <p>Everything that is not a GET is denied, so the prefix cannot quietly acquire a write endpoint
 * without someone changing this file.
 *
 * <h2>A new file, and the {@code @Order} is on the METHOD</h2>
 *
 * <p>New exactly as {@link MarketplacePublicSecurityConfiguration} is. Regenerating the catalog from
 * JDL rewrites {@code SecurityConfiguration} and discards edits to it; the failure would be silent
 * in the direction that matters least here but the pattern is worth keeping uniform.
 *
 * <p>The {@code @Order} sat on this class until D77, where <strong>Spring cannot read it</strong>:
 * the comparator is handed the factory method and the bean type and never the declaring class, so
 * {@code findAnnotationOnBean} answered {@code null} and the only thing putting this chain in front
 * of the generated one was component-scan order — {@code I} before {@code M} before {@code S}. That
 * was WP-13's finding on the gateway and it was <em>re-measured</em> here rather than ported, because
 * D52's rule is that a data answer of this kind does not transfer between stacks. It held: inverting
 * the class annotation to {@code LOWEST_PRECEDENCE} moved nothing at all.
 */
@Configuration
public class InternalApiSecurityConfiguration {

    /** Everything the estate may ask catalog directly. One entry, and it should stay short. */
    static final String INTERNAL_PATHS = "/internal/**";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(INTERNAL_PATHS)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.requestMatchers(HttpMethod.GET, INTERNAL_PATHS).permitAll().anyRequest().denyAll());
        return http.build();
    }
}
