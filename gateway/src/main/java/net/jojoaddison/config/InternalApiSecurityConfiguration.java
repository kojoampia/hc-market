package net.jojoaddison.config;

import static org.springframework.security.config.Customizer.withDefaults;

import net.jojoaddison.security.ContactLookupToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

/**
 * The filter chain for the gateway's own {@code /internal/**} — {@code decisions.md} D74.
 *
 * <h2>What protects this endpoint, and why catalog's argument does not transfer</h2>
 *
 * <p>catalog has a file with this name and it opens by saying it is <em>not</em> what protects its
 * endpoints: what keeps catalog's {@code /internal/professionals/{ref}/login} off the internet is D28's
 * route narrowing — the gateway's routes match {@code /services/<service>/api/**} and nothing else, so
 * no request from outside can be routed to a path under {@code /internal/}. <strong>That argument does
 * not transfer one inch to this file.</strong> This endpoint is on the gateway itself: there is no
 * route in front of it, both nginx vhosts end in a {@code location /} that proxies everything to the
 * gateway, and the dev and quality compose files publish the gateway's port on every interface.
 * <strong>Every estate can reach {@code /internal/**} here from outside.</strong>
 *
 * <p>So the protection is stated rather than inherited, and it is three things — listed in the order of
 * how much weight each actually bears, which is not the order they were written in:
 *
 * <ol>
 *   <li><strong>A credential only a holder of the estate signing key can produce.</strong> This is the
 *       load-bearing one. {@link ContactLookupToken#AUTHORITY} is granted by no login: the gateway is
 *       the only issuer of user tokens in this estate and it never puts that authority in one, so the
 *       only way to satisfy the rule below is to sign a token with {@code JWT_BASE64_SECRET}.
 *   <li><strong>The narrowings, checked in the resource.</strong> The authority alone is not enough —
 *       {@code POST /api/admin/authorities} can create it by name and {@code UserResource} can grant
 *       it, so an administrator could hand a real person a twenty-four-hour token carrying it.
 *       {@code ContactLookupToken.mayRead} refuses that: the subject must be
 *       {@code system:contact-lookup}, which matches no user in any store, the token must name the
 *       login being asked about, and it must live thirty seconds.
 *   <li><strong>The generated chain's default-deny, underneath both.</strong> See below — it is real,
 *       it is why this endpoint fails closed rather than open when this file is deleted, and it is the
 *       one of the three that nothing in this repository chose.
 * </ol>
 *
 * <p>Say it plainly, because a reader who sees a chain and stops there will draw the wrong conclusion:
 * <strong>this path is reachable from the internet and refuses everybody who cannot sign with the
 * estate key.</strong> An edge block would be defence in depth and is deliberately not here — D74
 * argues why, and the short version is that it would be a control on production and no control at all
 * on the two estates that publish the gateway's port directly, which is worse than none.
 *
 * <h2>Why a chain at all — and it is NOT the reason this file was first written</h2>
 *
 * <p>This section said the generated chain <em>permits</em> an unmatched path, and that it had been
 * measured. It had not been, and it is false. <strong>Measured, on this container, both ways</strong>
 * (D74 §4.3): with this chain removed, {@code GET /internal/customers/{login}/email} answers
 * <strong>401 anonymously and 403 to a correct estate-signed contact-lookup token</strong>, and so does
 * every other path the generated chain claims and has no rule for — {@code /internalx/…} and
 * {@code /nothing/at/all} give the identical pair. Reactive Spring Security's
 * {@code DelegatingReactiveAuthorizationManager} <em>denies</em> an exchange no rule matched; it does
 * not fall through. {@code InternalApiPermitIT} pins it, so an upgrade that flips it goes red here
 * rather than quietly.
 *
 * <p>This paragraph used to endorse catalog's file for calling that "a version-dependent detail nobody
 * should have to look up". <strong>D77 deleted that sentence from catalog</strong>, because it was
 * wrong twice over: the detail <em>was</em> looked up — in the servlet stack it is <strong>401</strong>,
 * the same refusal measured here — and the claim it rested on, that the generated chain matches only
 * {@code /api/**}, {@code /v3/api-docs/**} and {@code /management/**}, confused
 * {@code authorizeHttpRequests} rules with a {@code securityMatcher}. Do not cite it; catalog now says
 * the opposite, and both services' chains open doors rather than closing them.
 *
 * <p>So what this chain does is <strong>open</strong> a door that was closed — it is the reason booking
 * can reach the endpoint at all, and every refusal above is a refusal it introduces on the way. That is
 * a materially different claim from the one this comment used to make, and it changes what a reader
 * should be afraid of: <strong>deleting this file breaks payments, it does not leak an address.</strong>
 *
 * <h2>Two lines here that no test can see, and are guarded by a grep instead</h2>
 *
 * <p>Measured under mutation, both green: widening {@code hasAuthority(AUTHORITY)} to
 * {@code authenticated()}, and deleting {@code anyExchange().denyAll()}. Neither discloses anything
 * today, and that is exactly why nothing goes red — {@code ContactLookupToken.mayRead} in the resource
 * refuses everything the authority rule would have (an ordinary login token's subject is a person), and
 * the framework's default-deny refuses everything {@code denyAll} would have. Both are therefore
 * over-determined, and a test asserting either would be asserting a coincidence.
 *
 * <p>They stay because over-determined is not the same as unnecessary: the authority rule is what makes
 * this endpoint's requirement legible at the edge rather than only inside a resource, and
 * {@code denyAll} is what stops the prefix quietly acquiring a write endpoint the day the framework's
 * default changes. What guards them is
 * {@code "The gateway's contact lookup must stay behind the estate's own credential"} in
 * {@code build.yml}, which greps this file and the resource — the same division of labour the payment
 * webhook's permit has, and for the same reason.
 *
 * <p>It also has to authenticate for itself. A chain scoped by {@code securityMatcher} carries its own
 * {@code oauth2ResourceServer}: without it there is no JWT converter on this path, every caller is
 * anonymous, and the endpoint is 401 for booking as well — a fail-closed outage rather than a leak,
 * and still an outage.
 *
 * <h2>A new file, and the {@code @Order} is on the METHOD</h2>
 *
 * <p>New because regenerating the gateway from JDL rewrites {@code SecurityConfiguration} and discards
 * every edit to it — the same reasoning as {@link MarketplacePublicRouteConfiguration} and
 * {@link PaymentWebhookRouteConfiguration}.
 *
 * <p>On the method because Spring never reads it anywhere else. The comparator is handed the factory
 * method and the bean type as order sources, never the declaring {@code @Configuration} class, so
 * {@code findAnnotationOnBean} answers {@code null} for a class-level annotation — which is exactly
 * what WP-13's review found on the webhook chain, where the only thing putting it in front of the
 * generated chain was the letter P sorting before the letter S. Read
 * {@link PaymentWebhookRouteConfiguration}'s note on the measurement before moving this line.
 *
 * <p><strong>catalog's file of this name had its {@code @Order} on the class until D77</strong>
 * (backlog {@code NEW-34}), and so did catalog's public-reads chain and booking's webhook chain — three
 * files, where that item and CLAUDE.md both said one. D77 re-measured the mechanism in the servlet
 * stack rather than porting this one, per D52's rule, and found it holds there identically; it also
 * found that the servlet generated chain declares no {@code securityMatcher} at all, so the ordering is
 * total rather than merely tidy. Do not read the reactive measurement here as covering that one.
 */
@Configuration
public class InternalApiSecurityConfiguration {

    /** Everything the estate may ask this gateway directly. One entry, and it should stay short. */
    static final String INTERNAL_PATHS = "/internal/**";

    /**
     * Ahead of the generated chain, and of the two chains beside this one.
     *
     * <p>{@code HIGHEST_PRECEDENCE + 12} sits after the public-reads chain ({@code +10}) and the
     * payment webhook ({@code +11}) and strictly ahead of the generated chain, which carries no order
     * at all and therefore sits at {@code LOWEST_PRECEDENCE}. The three are disjoint by path, so the
     * relative order among them decides nothing today; being strictly ahead of the generated one is
     * what matters, and {@code InternalApiPermitIT} asserts the strictness rather than the position.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 12)
    public SecurityWebFilterChain internalApiFilterChain(ServerHttpSecurity http) {
        http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher(INTERNAL_PATHS))
            // No cookie, no session, no browser. A service presents a bearer token per call.
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            // Everything that is not a GET is denied, so the prefix cannot quietly acquire a write
            // endpoint without somebody changing this file — catalog's rule, kept.
            .authorizeExchange(authz ->
                authz.pathMatchers(HttpMethod.GET, INTERNAL_PATHS).hasAuthority(ContactLookupToken.AUTHORITY).anyExchange().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
