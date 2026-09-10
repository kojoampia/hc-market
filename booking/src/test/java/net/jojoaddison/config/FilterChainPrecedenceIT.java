package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What orders booking's two filter chains — {@code decisions.md} D77.
 *
 * <h2>Why this file is in booking, which NEW-34 did not name</h2>
 *
 * <p>The backlog item said catalog's {@code InternalApiSecurityConfiguration} was "the one place" the
 * gateway's {@code @Order} correction had not reached. It was <strong>three</strong> places: catalog's
 * two chains and this service's {@link PaymentWebhookSecurityConfiguration}, which carried the same
 * annotation in the same position with the same false paragraph above it. A list trusted rather than
 * derived is this repository's recurring defect, and it recurred in the item describing it.
 *
 * <h2>The measurement, re-taken in this service</h2>
 *
 * <p>D52's rule is that a data answer of this kind is never transferable, so the annotation's
 * readability is asserted here rather than inherited from catalog's run. Measured on this container
 * with the annotation moved back onto the {@code @Configuration} class:
 * {@code findAnnotationOnBean("paymentWebhookFilterChain", Order.class)} answers {@code null}, and the
 * chain does not move — {@code P} sorting before {@code S} is what was holding it up.
 *
 * <h2>Order matters completely, because the generated chain claims every request</h2>
 *
 * <p>The generated chain declares <strong>no {@code securityMatcher}</strong>; its {@code /api/**},
 * {@code /v3/api-docs/**} and {@code /management/**} entries are {@code authorizeHttpRequests} rules,
 * which narrow authorization and not the chain. So it claims {@code /webhooks/**} too, and whichever
 * chain comes first is the only one that runs: with the generated one in front, every provider
 * callback is 401 at booking itself, which reads as a signature problem in a provider's dashboard.
 *
 * <h2>The floor underneath this file, which no test here can invoke</h2>
 *
 * <p>Servlet Spring Security refuses to build a {@code FilterChainProxy} in which an {@code any
 * request} chain precedes a narrower one — {@code WebSecurityFilterChainValidator} throws
 * {@code UnreachableFilterChainException} and the context does not start. Measured on catalog, quoted
 * in D77 §3, and deliberately <strong>not asserted</strong>: the validator is a package-private final
 * framework class and the proxy exposes no way to re-run it over a reordered list. A reader counting
 * green tests here should not conclude it is covered.
 */
@IntegrationTest
@AutoConfigureMockMvc
class FilterChainPrecedenceIT {

    /** A provider callback, as {@code PaymentWebhookResource} maps it — {@code decisions.md} D43. */
    private static final String WEBHOOK_PATH = "/webhooks/payments/paystack";

    /**
     * A path no chain in this context narrows to and no authorization rule names.
     *
     * <p>Deliberately not under {@code /api/}, where the generated chain's
     * {@code .requestMatchers("/api/**").authenticated()} would answer instead — that is a rule rather
     * than the absence of one, and the last test is about the absence.
     */
    private static final String UNRULED_PATH = "/nothing/at/all";

    /** The {@code @Bean} method's name, which is the bean's name. */
    private static final String WEBHOOK_CHAIN = "paymentWebhookFilterChain";

    /** JHipster's, in the generated {@code SecurityConfiguration}. */
    private static final String GENERATED_CHAIN = "filterChain";

    @Autowired
    private FilterChainProxy proxy;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Autowired
    private MockMvc mockMvc;

    /**
     * Red when the annotation moves back onto the {@code @Configuration} class, which is where it was
     * until D77 and where {@code findAnnotationOnBean} answers {@code null} — measured in this service.
     *
     * <p>Position alone cannot see that: the alphabet already puts this chain in front, so the runtime
     * order below stays green with no readable annotation anywhere in booking.
     */
    @Test
    @DisplayName("the webhook chain's precedence is declared where the container can read it")
    void theWebhookChainsPrecedenceIsDeclared() {
        assertThat(beanFactory.containsBean(WEBHOOK_CHAIN))
            .as("there is no %s bean — the @Configuration or the @Bean has gone, and with it the guard", WEBHOOK_CHAIN)
            .isTrue();

        Order declared = beanFactory.findAnnotationOnBean(WEBHOOK_CHAIN, Order.class);
        assertThat(declared)
            .as(
                "%s carries no @Order the container can read. On the @Configuration class it is invisible — " +
                "put it on the @Bean method. See decisions.md D77",
                WEBHOOK_CHAIN
            )
            .isNotNull();

        Order generated = beanFactory.findAnnotationOnBean(GENERATED_CHAIN, Order.class);
        int generatedOrder = generated == null ? Ordered.LOWEST_PRECEDENCE : generated.value();
        assertThat(declared.value())
            .as("the webhook chain must outrank the generated chain, not merely tie with it and win on class names")
            .isLessThan(generatedOrder);
    }

    /**
     * The premise that makes the ordering total rather than a matter of taste.
     *
     * <p>If a regeneration ever gives the generated chain a {@code securityMatcher}, these two may be
     * disjoint and D77's argument needs re-reading — so it goes red here rather than quietly expiring.
     */
    @Test
    @DisplayName("the generated chain claims every request, so nothing here is disjoint from it")
    void theGeneratedChainClaimsEveryRequest() {
        for (String path : List.of(WEBHOOK_PATH, UNRULED_PATH)) {
            assertThat(claims(generatedChain(), path))
                .as(
                    "the generated chain no longer claims %s. If it has acquired a securityMatcher, these chains may " +
                    "now be disjoint and D77's whole argument about precedence needs re-reading",
                    path
                )
                .isTrue();
        }
    }

    /**
     * The runtime's own ordering, read off {@code FilterChainProxy} rather than off an injected list.
     *
     * <p>Green today with no readable annotation at all, which is why it is not the guard for D77's
     * change — it is the statement of what that change protects.
     */
    @Test
    @DisplayName("the webhook chain claims the callback path before the generated chain does")
    void theWebhookChainClaimsTheCallbackPathFirst() {
        List<SecurityFilterChain> chains = proxy.getFilterChains();
        int generated = chains.indexOf(generatedChain());
        assertThat(generated).as("the generated chain is not in FilterChainProxy's list at all").isNotNegative();

        assertThat(firstChainClaiming(chains, WEBHOOK_PATH))
            .as(
                "a chain BEFORE index %d must claim %s, or the generated chain handles it and every provider " +
                "callback is 401 at booking itself. See decisions.md D77 and D43",
                generated,
                WEBHOOK_PATH
            )
            .isBetween(0, generated - 1);
    }

    /**
     * The framework floor nothing in this repository chose, and the answer to a question this service's
     * webhook chain declined to look up in the same words catalog's did.
     *
     * <p>On this stack it is a refusal: 401, from the generated chain's bearer-token entry point, for a
     * path its rules never name. So these chains <em>open</em> doors that were closed rather than
     * closing doors that were open — the correction D74 made in the reactive stack, holding here too.
     *
     * <p>Pinned so that a Spring Security upgrade flipping it to a fall-through goes red here, rather
     * than turning booking's entire unnamed surface into anonymous reads in silence.
     */
    @Test
    @DisplayName("a path no authorization rule names is refused, not passed through")
    void aPathNoRuleNamesIsRefused() throws Exception {
        assertThat(mockMvc.perform(get(UNRULED_PATH)).andReturn().getResponse().getStatus())
            .as(
                "%s matches no authorizeHttpRequests rule. If this is no longer a refusal, servlet Spring Security " +
                "has changed what it does with such a request and every unnamed path in booking has become readable. " +
                "See decisions.md D77",
                UNRULED_PATH
            )
            .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private SecurityFilterChain generatedChain() {
        return beanFactory.getBean(GENERATED_CHAIN, SecurityFilterChain.class);
    }

    /** Where in the runtime's own ordering the first chain claiming {@code path} sits, or -1. */
    private int firstChainClaiming(List<SecurityFilterChain> chains, String path) {
        for (int index = 0; index < chains.size(); index++) {
            if (claims(chains.get(index), path)) {
                return index;
            }
        }
        return -1;
    }

    private boolean claims(SecurityFilterChain chain, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return chain.matches(request);
    }
}
