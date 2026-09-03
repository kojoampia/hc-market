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
 * The filter chain for {@code /webhooks/**} — the provider-facing surface D43 adds.
 *
 * <h2>This chain is not what authenticates these requests</h2>
 *
 * <p>It permits the POST, and it has to: a payment provider has no token and cannot be given one, so
 * a chain that demanded a JWT would reject every genuine callback and accept nothing else. What
 * authenticates a callback is the provider's own signature over the raw body, checked by
 * {@code PaymentProvider.readCallback}, and an unverified caller gets a flat 401 from the resource.
 * Say it plainly, because a reader who sees {@code permitAll} and stops there will draw the wrong
 * conclusion: <strong>the security of this path is a signature check in application code, not a rule
 * in this file.</strong>
 *
 * <p>Two further things narrow it, and they are worth knowing before anybody widens the matcher.
 * Nothing routes here <em>through the gateway</em> today — the gateway's four route predicates match
 * {@code /services/<service>/api/**}, and this path is not under {@code /api}, which is the same
 * property that keeps catalog's {@code /internal/**} private (D28). That is a statement about the
 * gateway and not about the host: {@code docker-compose.dev.yml} publishes booking's own port on every
 * interface, so on a development machine this path is reachable directly, exactly as catalog's
 * {@code /internal/**} always has been. And everything that is not a POST
 * is denied, so the prefix cannot quietly acquire a readable endpoint: a {@code GET
 * /webhooks/payments/...} that answered anything at all would be an unauthenticated read of what the
 * platform knows about somebody's payment.
 *
 * <p>It exists at all for the same reason {@code InternalApiSecurityConfiguration} does in catalog:
 * the generated {@code SecurityConfiguration} matches {@code /api/**}, {@code /v3/api-docs/**} and
 * {@code /management/**}, a path under {@code /webhooks/} matches none of them, and what Spring
 * Security does with an unmatched request is a version-dependent detail nobody should have to look up
 * to know whether this returns 200 or 401.
 *
 * <h2>A new file, on purpose</h2>
 *
 * <p>Regenerating booking from JDL rewrites {@code SecurityConfiguration} and discards edits to it. A
 * lost rule here would present as every provider callback returning 401 while the signature is
 * perfectly good — a symptom that sends you to the provider's dashboard rather than to this
 * repository.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PaymentWebhookSecurityConfiguration {

    /** Everything a payment provider may reach. One entry, and it should stay one. */
    static final String WEBHOOK_PATHS = "/webhooks/**";

    @Bean
    public SecurityFilterChain paymentWebhookFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(WEBHOOK_PATHS)
            // No token, so no CSRF token either, and nothing here is reached from a browser session.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.requestMatchers(HttpMethod.POST, WEBHOOK_PATHS).permitAll().anyRequest().denyAll());
        return http.build();
    }
}
