package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The webhook permit as the running gateway assembles it — {@code decisions.md} D45.
 *
 * <h2>Why this exists beside {@link PaymentWebhookRouteConfigurationTest}</h2>
 *
 * <p>That test builds the chain with {@code new PaymentWebhookRouteConfiguration()} and CI greps two
 * literals out of the source file. Both check what the chain <em>says</em>; neither checks that the
 * container ever hears it. Removing {@code @Configuration}, {@code @Bean} or {@code @Order} leaves
 * every one of those checks green while every provider callback is refused with a 401 at the edge —
 * measured, all three, by mutation. This file is the half that asks the context.
 *
 * <p><strong>{@code @Order} needs two assertions, not one, and measuring said so.</strong> The
 * generated {@code SecurityConfiguration.springSecurityFilterChain} carries no order at all and its
 * {@code securityMatcher} is a <em>negated</em> matcher claiming everything except {@code /app},
 * {@code /i18n}, {@code /content} and {@code /swagger-ui} — so it claims the webhook path too, and the
 * two matchers look disjoint from the outside while they are not. But deleting the annotation does
 * <em>not</em> reorder the list: two unordered beans tie at {@code LOWEST_PRECEDENCE} and a stable
 * sort leaves them in registration order, which for these two is component-scan order —
 * {@code PaymentWebhook…} before {@code SecurityConfiguration}, alphabetically. Measured, both ways:
 *
 * <pre>
 *   as written      -&gt; [PUBLIC, WEBHOOK, GENERATED]
 *   &#64;Order removed  -&gt; [PUBLIC, WEBHOOK, GENERATED]   — unchanged, and lucky
 * </pre>
 *
 * <p>So position alone cannot see the annotation go. What it can see is
 * {@code @Configuration}/{@code @Bean} going, because then no chain claims the callback before the
 * generated one does. The annotation is caught by the second test, which asks whether the precedence
 * is <em>declared</em> — strictly ahead of the generated chain rather than tied with it and sorted by
 * the alphabet.
 *
 * <p>Spring sorts an injected {@code List} of beans exactly as it sorts the chains behind
 * {@code WebFilterChainProxy}, so the list below is the runtime's own order rather than a restatement
 * of it.
 *
 * <p>The <em>widening</em> direction — a matcher broadened to {@code /services/healthconnectbooking/**}
 * — is already covered by {@code PaymentWebhookRouteConfigurationTest.nothingElseIsPermitted}, and is
 * not repeated here. What was uncovered is the <em>disabling</em> direction, and that is all this
 * file is for.
 */
@IntegrationTest
@AutoConfigureWebTestClient
class PaymentWebhookRoutePermitIT {

    /** A callback as a provider would send it, with the prefix the compose route strips. */
    private static final String CALLBACK_PATH = "/services/healthconnectbooking/webhooks/payments/paystack";

    /**
     * Any ordinary booking path. It stands in for "the chain that authenticates {@code /services/**}"
     * — the generated one — because that is what claiming this path means at the gateway edge.
     */
    private static final String BOOKING_API_PATH = "/services/healthconnectbooking/api/bookings";

    /** The {@code @Bean} method's name, which is the bean's name. */
    private static final String WEBHOOK_CHAIN_BEAN = "paymentWebhookRouteFilterChain";

    /** JHipster's, in the generated {@code SecurityConfiguration}. */
    private static final String GENERATED_CHAIN_BEAN = "springSecurityFilterChain";

    @Autowired
    private List<SecurityWebFilterChain> chains;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Autowired
    private WebTestClient client;

    /** Where in the runtime's own ordering the first chain claiming {@code path} sits, or -1. */
    private int firstChainClaiming(String path) {
        for (int index = 0; index < chains.size(); index++) {
            Boolean claimed = chains.get(index).matches(MockServerWebExchange.from(MockServerHttpRequest.post(path).build())).block();
            if (Boolean.TRUE.equals(claimed)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Red without {@code @Configuration} and red without {@code @Bean}.
     *
     * <p>In either case there is no webhook chain in the context at all, so the first chain claiming
     * the callback <em>is</em> the generated one and the two indexes are equal rather than ordered.
     * That is the 401-at-the-edge failure, seen from the runtime's own list.
     */
    @Test
    @DisplayName("the container puts a chain claiming the callback in front of the one authenticating /services/**")
    void theWebhookChainOutranksTheGeneratedChain() {
        int generated = firstChainClaiming(BOOKING_API_PATH);
        assertThat(generated)
            .as("no chain in this context claims %s, so there is nothing to be in front of", BOOKING_API_PATH)
            .isNotNegative();

        assertThat(firstChainClaiming(CALLBACK_PATH))
            .as(
                "a chain BEFORE index %d must claim %s, or the generated chain authenticates the callback first " +
                "and every provider gets a 401 at the edge — see decisions.md D45",
                generated,
                CALLBACK_PATH
            )
            .isBetween(0, generated - 1);
    }

    /**
     * Red when {@code @Order} is deleted, which position alone cannot be.
     *
     * <p>{@code findAnnotationOnBean} reads what the comparator reads, and it is the reason the
     * annotation moved from the {@code @Configuration} class to the {@code @Bean} method during this
     * review: on the class it answered {@code null} for all three chains, so the precedence everyone
     * believed was declared was never read by anything. Asserting it here is asserting that the
     * webhook chain wins on a written-down number rather than on {@code P} sorting before {@code S}.
     *
     * <p>The generated chain is expected to have <em>no</em> order — that is the generator's business
     * and not something to change — so what is asserted about it is only that this one is strictly
     * ahead. Tied would be enough today and is exactly what must not be relied on.
     */
    @Test
    @DisplayName("the webhook chain's precedence is declared, not inherited from the alphabet")
    void theWebhookChainsPrecedenceIsDeclared() {
        // Asked first so that a chain removed altogether fails here with a sentence rather than with
        // findAnnotationOnBean's NoSuchBeanDefinitionException, which is the right answer given badly.
        assertThat(beanFactory.containsBean(WEBHOOK_CHAIN_BEAN))
            .as("there is no %s bean — the @Configuration or the @Bean has gone, and with it the permit", WEBHOOK_CHAIN_BEAN)
            .isTrue();

        Order webhook = beanFactory.findAnnotationOnBean(WEBHOOK_CHAIN_BEAN, Order.class);
        assertThat(webhook)
            .as(
                "%s carries no @Order the container can read. On the @Configuration class it is invisible — " +
                "put it on the @Bean method. Without it this chain only precedes the generated one because " +
                "two unordered beans tie and the class names sort that way. See decisions.md D45",
                WEBHOOK_CHAIN_BEAN
            )
            .isNotNull();

        Order generated = beanFactory.findAnnotationOnBean(GENERATED_CHAIN_BEAN, Order.class);
        int generatedOrder = generated == null ? Ordered.LOWEST_PRECEDENCE : generated.value();
        assertThat(webhook.value())
            .as("the webhook chain must outrank the chain that authenticates /services/**, not merely tie with it")
            .isLessThan(generatedOrder);
    }

    /**
     * The same property through HTTP, because the ordering above is a means and this is the end.
     *
     * <p>Asserted as "not 401" rather than as an exact status: nothing routes {@code /services/**} in
     * a test context, so what a permitted callback actually gets here is the gateway's own 404. That
     * is the right shape to assert anyway — what D45 protects is that the request is not turned away
     * before booking is reached, and the status booking would answer with is booking's business and is
     * covered by {@code PaymentWebhookResourceIT} over there.
     */
    @Test
    @DisplayName("an anonymous callback is not refused by the edge, while an anonymous booking read still is")
    void anAnonymousCallbackIsNotRefusedAtTheEdge() {
        client
            .post()
            .uri(CALLBACK_PATH)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .value(status ->
                assertThat(status)
                    .as("the gateway asked a payment provider for a token; it has none and cannot be given one")
                    .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
            );

        // The control. Without it "not 401" would also pass on a gateway that authenticates nothing,
        // which is the opposite mistake and a far worse one.
        client.post().uri(BOOKING_API_PATH).bodyValue("{}").exchange().expectStatus().isUnauthorized();
    }
}
