package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ContactLookupToken;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * What actually keeps the gateway's {@code /internal/**} off the internet — {@code decisions.md} D74.
 *
 * <h2>The claim, and why it needed its own file</h2>
 *
 * <p>catalog's {@code /internal/**} is private because <strong>no gateway route matches it</strong>
 * (D28), and that argument does not survive being moved one service along: this endpoint is on the
 * gateway itself, so there is no route in front of it, both nginx vhosts end in a {@code location /}
 * that proxies everything here, and the dev and quality compose files publish the gateway's port on
 * every interface. What refuses a stranger is the credential, and that is what is asserted here rather
 * than assumed.
 *
 * <h2>What was assumed, measured, and turned out to be backwards</h2>
 *
 * <p>This file first asserted that an unmatched path on this gateway is <em>anonymously reachable</em>,
 * on the reasoning that the generated chain claims {@code /internal/**} through a negated
 * {@code securityMatcher} while naming no {@code authorizeExchange} rule for it, and that a reactive
 * authorization manager matching nothing completes empty and lets the request through. <strong>It went
 * red on its own control</strong>, which is why the control was written first.
 *
 * <p>Measured on this container, with {@code @Configuration} removed and restored:
 *
 * <pre>
 *   chain present  -&gt;  /internal/…  anonymous 401, contact-lookup token 404 (no such account)
 *   chain removed   -&gt;  /internal/…  anonymous 401, contact-lookup token 403
 *   either way      -&gt;  /internalx/… and /nothing/at/all: anonymous 401, that token 403
 * </pre>
 *
 * <p>So reactive Spring Security <strong>denies</strong> an exchange no rule matched, and this chain's
 * job is the opposite of what the comment claimed: it <em>opens</em> a door that was closed. Deleting
 * it breaks payments; it does not disclose an address. The tests below are shaped around that: the
 * first asserts the estate's own token gets through while nothing else does, and the last pins the
 * default-deny itself, so a Spring Security upgrade that flipped it would go red here rather than
 * turning three neighbouring paths into anonymous reads in silence.
 *
 * <h2>Two things in this chain no test here can see, stated rather than implied</h2>
 *
 * <p>Measured, both green under mutation: widening {@code hasAuthority(AUTHORITY)} to
 * {@code authenticated()}, and deleting {@code anyExchange().denyAll()}. The first is covered by
 * {@code mayRead} in the resource and the second by the framework's own default-deny — so both are
 * over-determined today and neither can be asserted from here without asserting a coincidence. They
 * are guarded by a CI grep instead, and the limit is written down because a reader counting green tests
 * would otherwise conclude this chain is fully pinned.
 *
 * <p>Beside {@code PaymentWebhookRoutePermitIT}, and for its reasons: a CI grep and a hand-built chain
 * test both stay green when {@code @Configuration}, {@code @Bean} or {@code @Order} is removed. This
 * asks the container.
 */
@IntegrationTest
@AutoConfigureWebTestClient
class InternalApiPermitIT {

    /** The one path under the prefix, as booking calls it. */
    private static final String INTERNAL_PATH = "/internal/customers/wp13.payer/email";

    /**
     * A path claimed by the generated chain and named by none of its rules.
     *
     * <p>Deliberately one character away from the prefix above, so the pair differs in nothing else.
     * It must NOT be under {@code /internal/}, or the chain under test would claim it and the last
     * assertion would be about this package's own work rather than about the generated chain's.
     */
    private static final String UNMATCHED_PATH = "/internalx/customers/wp13.payer/email";

    /** Any ordinary API path — the generated chain's {@code .pathMatchers("/api/**").authenticated()}. */
    private static final String API_PATH = "/api/account";

    /** The {@code @Bean} method's name, which is the bean's name. */
    private static final String INTERNAL_CHAIN_BEAN = "internalApiFilterChain";

    /** JHipster's, in the generated {@code SecurityConfiguration}. */
    private static final String GENERATED_CHAIN_BEAN = "springSecurityFilterChain";

    @Autowired
    private List<SecurityWebFilterChain> chains;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private WebTestClient client;

    /**
     * The estate's own credential gets past the edge, and nothing else does.
     *
     * <p>Red with {@code @Configuration} or {@code @Bean} removed — measured: the estate-signed token
     * then gets 403 from the generated chain's default-deny instead of reaching the resource, and three
     * of {@code InternalCustomerContactResourceIT}'s cases go with it.
     *
     * <p><strong>NOT red when {@code hasAuthority} is widened to {@code authenticated()}</strong>, and
     * that was measured rather than assumed — this comment claimed the opposite until it was run. The
     * ordinary login token is still 403, because {@code ContactLookupToken.mayRead} in the resource
     * refuses everything the authority check would have: the subject is a person. So the chain's
     * authority rule is defence in depth <em>given</em> {@code mayRead}, and no test in this estate can
     * see it go. A CI grep can, and does — {@code "The gateway's contact lookup must stay behind the
     * estate's own credential"} — which is the same division of labour the webhook permit has.
     *
     * <p><strong>"Not 401 and not 403" rather than an exact status</strong>, because no account is
     * seeded in this class and the correct answer for the estate's token is therefore the resource's own
     * 404. What is being asserted is that the request was <em>not turned away before the resource</em>,
     * which is this chain's whole job; what the resource then answers is
     * {@code InternalCustomerContactResourceIT}'s subject.
     */
    @Test
    @DisplayName("an estate-signed contact-lookup token reaches the endpoint; anonymous and ordinary tokens do not")
    void onlyTheEstatesOwnCredentialGetsPastTheEdge() {
        client
            .get()
            .uri(INTERNAL_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactLookupToken())
            .exchange()
            .expectStatus()
            .value(status ->
                assertThat(status)
                    .as(
                        "the estate's own contact-lookup token must reach the resource — 401 or 403 here means the " +
                        "chain is gone and booking cannot take a payment. See decisions.md D74"
                    )
                    .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value())
            );

        // The two controls, and without them the assertion above passes on a gateway that authenticates
        // nothing at all — the opposite mistake and a far worse one.
        client.get().uri(INTERNAL_PATH).exchange().expectStatus().isUnauthorized();
        client
            .get()
            .uri(INTERNAL_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ordinaryLoginToken())
            .exchange()
            .expectStatus()
            .isForbidden();

        // And the ordinary API is still authenticated: a chain claiming too much would refuse this too.
        client.get().uri(API_PATH).exchange().expectStatus().isUnauthorized();
    }

    /**
     * The floor underneath this package, pinned because nothing here chose it.
     *
     * <p>Reactive Spring Security denies an exchange that matched no {@code authorizeExchange} rule.
     * That is what makes the gateway's whole unmatched surface fail closed — {@code /internalx/…},
     * {@code /nothing/at/all}, and {@code /internal/**} itself if this package's chain were deleted —
     * and it is a framework detail rather than a decision written down anywhere in this repository.
     *
     * <p>So it is asserted, with an estate-signed token so that the answer cannot be confused with the
     * anonymous 401 an entry point would give. If a Spring Security upgrade flips it to a fall-through,
     * this goes red rather than three neighbouring paths silently becoming anonymous reads.
     */
    @Test
    @DisplayName("a path the generated chain claims and has no rule for is denied, not passed through")
    void anUnmatchedPathIsDeniedRatherThanPermitted() {
        client
            .get()
            .uri(UNMATCHED_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactLookupToken())
            .exchange()
            .expectStatus()
            .value(status ->
                assertThat(status)
                    .as(
                        "%s matches no authorizeExchange rule. If this is no longer a refusal, reactive Spring " +
                        "Security has changed what it does with an unmatched exchange, and every unmatched path " +
                        "on this gateway has become readable. See decisions.md D74",
                        UNMATCHED_PATH
                    )
                    .isEqualTo(HttpStatus.FORBIDDEN.value())
            );

        client.get().uri(UNMATCHED_PATH).exchange().expectStatus().isUnauthorized();
    }

    /**
     * Red without {@code @Configuration} and red without {@code @Bean}.
     *
     * <p>In either case no chain in the context claims {@code /internal/**} before the generated one,
     * so the two indexes are equal rather than ordered — and the generated chain then denies the
     * lookup, seen from the runtime's own list. Spring sorts an injected {@code List} of beans exactly
     * as it sorts the chains behind {@code WebFilterChainProxy}, so this list is the runtime's order
     * rather than a restatement of it.
     */
    @Test
    @DisplayName("the container puts a chain claiming /internal/** in front of the generated one")
    void theInternalChainOutranksTheGeneratedChain() {
        int generated = firstChainClaiming(API_PATH);
        assertThat(generated).as("no chain in this context claims %s, so there is nothing to be in front of", API_PATH).isNotNegative();

        assertThat(firstChainClaiming(INTERNAL_PATH))
            .as(
                "a chain BEFORE index %d must claim %s, or the generated chain handles it — and it has no rule " +
                "for that path, so it denies it and booking cannot take a payment. See decisions.md D74",
                generated,
                INTERNAL_PATH
            )
            .isBetween(0, generated - 1);
    }

    /**
     * Red when {@code @Order} is deleted, which position alone cannot be.
     *
     * <p>{@code findAnnotationOnBean} reads what the comparator reads, and on a {@code @Configuration}
     * class it answers {@code null} — the finding WP-13's review made on the webhook chain, where the
     * only thing putting it in front of the generated one was the letter P sorting before the letter S.
     * {@code InternalApiSecurityConfiguration} carries the annotation on the {@code @Bean} method for
     * that reason, and this is what would go red if it moved. catalog's file of the same name still has
     * it on the class — backlog {@code NEW-34}, deliberately not fixed here.
     */
    @Test
    @DisplayName("the internal chain's precedence is declared, not inherited from the alphabet")
    void theInternalChainsPrecedenceIsDeclared() {
        assertThat(beanFactory.containsBean(INTERNAL_CHAIN_BEAN))
            .as("there is no %s bean — the @Configuration or the @Bean has gone, and with it the guard", INTERNAL_CHAIN_BEAN)
            .isTrue();

        Order internal = beanFactory.findAnnotationOnBean(INTERNAL_CHAIN_BEAN, Order.class);
        assertThat(internal)
            .as(
                "%s carries no @Order the container can read. On the @Configuration class it is invisible — " +
                "put it on the @Bean method. See decisions.md D74 and D45",
                INTERNAL_CHAIN_BEAN
            )
            .isNotNull();

        Order generated = beanFactory.findAnnotationOnBean(GENERATED_CHAIN_BEAN, Order.class);
        int generatedOrder = generated == null ? Ordered.LOWEST_PRECEDENCE : generated.value();
        assertThat(internal.value())
            .as("the internal chain must outrank the generated chain, not merely tie with it and win on class names")
            .isLessThan(generatedOrder);
    }

    /** What {@code FanoutTokenMinter.forContactLookupOf("wp13.payer")} mints, from the contract itself. */
    private String contactLookupToken() {
        return token(ContactLookupToken.SUBJECT, ContactLookupToken.AUTHORITY, ContactLookupToken.LIFETIME);
    }

    /** A perfectly ordinary login token, which is the control for "the authority is the thing". */
    private String ordinaryLoginToken() {
        return token("wp13.payer", AuthoritiesConstants.USER, Duration.ofHours(24));
    }

    private String token(String subject, String authority, Duration lifetime) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("hc-market-booking")
            .issuedAt(now)
            .expiresAt(now.plus(lifetime))
            .subject(subject)
            .claim(SecurityUtils.AUTHORITIES_CLAIM, authority)
            .claim(ContactLookupToken.SUBJECT_CLAIM, "wp13.payer")
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Where in the runtime's own ordering the first chain claiming {@code path} sits, or -1. */
    private int firstChainClaiming(String path) {
        for (int index = 0; index < chains.size(); index++) {
            Boolean claimed = chains.get(index).matches(MockServerWebExchange.from(MockServerHttpRequest.get(path).build())).block();
            if (Boolean.TRUE.equals(claimed)) {
                return index;
            }
        }
        return -1;
    }
}
