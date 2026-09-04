package net.jojoaddison.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

/**
 * Lets a payment provider's callback through the gateway without a token — {@code decisions.md} D45.
 *
 * <h2>The second of two lines, and the one that is easy to forget</h2>
 *
 * <p>Exposing booking's payment webhook is <strong>two</strong> changes per environment, and D43 said
 * so in advance because getting it half right is silent. The first is a gateway route, in each of the
 * three compose files:
 *
 * <pre>
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_PREDICATES_0: Path=/services/healthconnectbooking/webhooks/**
 * </pre>
 *
 * <p>The second is this. The generated {@code SecurityConfiguration} ends its rules with
 * {@code .pathMatchers("/services/**").authenticated()}, and the gateway authenticates
 * <em>before</em> it routes — so with the route and without this, every callback is rejected at the
 * edge with a 401 that never reaches booking, never reaches an adapter, and never appears in
 * booking's logs. The provider sees an authentication failure from a URL it was given, retries for
 * as long as its policy allows, and files the payment as undelivered. That reads as a broken
 * integration rather than as a missing line in a security config, which is why both halves are
 * written down together in three places: here, in {@code PaymentWebhookResource}'s javadoc, and in
 * D45.
 *
 * <p>It is exactly the gap {@code MarketplacePublicRouteConfiguration} exists to close for the public
 * catalogue reads. The same shape is used, for the same reason, and this is a <strong>new file</strong>
 * on the same grounds: regenerating the gateway from JDL rewrites {@code SecurityConfiguration} and
 * discards edits to it.
 *
 * <h2>What is permitted, and what is emphatically not</h2>
 *
 * <p><strong>{@code POST} only, and one path.</strong> A {@code GET} under the same prefix would be an
 * unauthenticated read of what the platform knows about somebody's payment; booking's own
 * {@code PaymentWebhookSecurityConfiguration} denies everything that is not a POST as well, so the
 * two agree rather than one relying on the other.
 *
 * <p><strong>{@code permitAll} here is not the same as unprotected.</strong> A payment provider has
 * no token and cannot be given one, so authentication has to happen inside booking, from the
 * provider's own signature over the raw body — {@code PaymentProvider.readCallback}. Say it plainly,
 * because a reader who sees {@code permitAll} and stops there will draw the wrong conclusion: the
 * security of this path is a signature check in application code. Today every caller gets 401 from
 * it, because no adapter in the estate can verify anything (D45).
 *
 * <p><strong>The pattern must stay narrow.</strong> Widening it to
 * {@code /services/healthconnectbooking/**} would publish booking's entire API to anonymous callers —
 * every booking, every dispute, every erasure endpoint — which is D28's defect with the gateway's own
 * security instead of its routes. This is the one path in the estate deliberately routed outside
 * {@code /api/**}, and CI asserts the shape of the route that reaches it in all three compose files.
 */
@Configuration
public class PaymentWebhookRouteConfiguration {

    /**
     * Booking's webhook prefix as the gateway sees it, before {@code StripPrefix=2}.
     *
     * <p>Written out rather than composed from the service name: it has to match the compose files'
     * route predicate character for character, and a constant assembled from parts is harder to
     * compare with one than a literal is.
     */
    static final String WEBHOOK_PATH = "/services/healthconnectbooking/webhooks/**";

    /**
     * Ahead of the generated chain — and the annotation has to be <strong>here</strong>, on the
     * method.
     *
     * <p>It sat on the {@code @Configuration} class until a mutation test went green without it, and
     * there it ordered nothing at all. Spring hands the comparator the factory <em>method</em> and the
     * bean <em>type</em> as order sources, never the declaring configuration class, so
     * {@code findAnnotationOnBean(name, Order.class)} answered {@code null} for all three of this
     * gateway's chains. What was actually ordering them was component-scan order:
     * {@code MarketplacePublic…}, {@code PaymentWebhook…}, {@code SecurityConfiguration},
     * alphabetically.
     *
     * <p>It kept working because a tie is resolved by registration order. The generated
     * {@code SecurityConfiguration.springSecurityFilterChain} carries no order either, so both chains
     * sat at {@code LOWEST_PRECEDENCE} and the stable sort left this one in front — the letter P
     * coming before the letter S was the whole of what held the webhook open. That is not an academic
     * point: the generated chain's {@code securityMatcher} is a <em>negated</em> matcher claiming
     * everything except {@code /app}, {@code /i18n}, {@code /content} and {@code /swagger-ui}, so it
     * claims this path too. Renaming this class to anything sorting after {@code SecurityConfiguration}
     * would have made every provider callback a 401 at the edge, with nothing failing anywhere.
     *
     * <p>Read from the method the value counts, and {@code HIGHEST_PRECEDENCE + 11} is then strictly
     * ahead of an unordered chain rather than tied with it. {@code PaymentWebhookRoutePermitIT}
     * asserts the strictness, so deleting this line is red rather than merely lucky.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 11)
    public SecurityWebFilterChain paymentWebhookRouteFilterChain(ServerHttpSecurity http) {
        http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher(WEBHOOK_PATH, HttpMethod.POST))
            // No token means no session and no CSRF token either; nothing here is reached from a
            // browser, and a provider cannot fetch one.
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(authz -> authz.anyExchange().permitAll());
        return http.build();
    }
}
