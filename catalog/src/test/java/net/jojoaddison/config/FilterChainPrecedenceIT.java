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
 * What orders catalog's three filter chains — {@code decisions.md} D77.
 *
 * <h2>The two measurements this file exists to keep true</h2>
 *
 * <p>catalog declares three {@link SecurityFilterChain} beans: this package's
 * {@link InternalApiSecurityConfiguration} and {@link MarketplacePublicSecurityConfiguration}, and the
 * generated {@code SecurityConfiguration}. Both hand-written classes carried
 * {@code @Order(Ordered.HIGHEST_PRECEDENCE + n)} on the <strong>{@code @Configuration} class</strong>
 * until D77, which is where Spring cannot read it — WP-13's finding on the gateway, re-measured here
 * because D52's rule is that a data answer of this kind is never transferable and the reactive stack
 * selects chains through a different proxy.
 *
 * <p>Measured on this container, and both halves are quoted in D77 §2:
 *
 * <pre>
 *   annotation on the CLASS   -&gt;  findAnnotationOnBean(name, Order.class) == null, for both chains,
 *                                 and inverting it to LOWEST_PRECEDENCE moved nothing at all
 *   annotation on the @Bean   -&gt;  findAnnotationOnBean answers the value, and the chain moves
 * </pre>
 *
 * <p>So the annotation was unread and the only thing ordering these three was <em>component-scan
 * order</em>: {@code I} sorts before {@code M} sorts before {@code S}, which happens to be the order
 * the estate needs. A class rename would have reordered them.
 *
 * <h2>Order matters completely, because the generated chain claims every request</h2>
 *
 * <p>The generated chain declares <strong>no {@code securityMatcher}</strong> — its rules are
 * {@code authorizeHttpRequests} entries, which narrow authorization and not the chain — so it matches
 * {@code any request} and therefore claims {@code /internal/**} and every public read as well. The
 * three chains are not disjoint, and whichever of them comes first is the only one that runs.
 * {@link #theGeneratedChainClaimsEveryRequest()} pins that premise: if a regeneration ever gives it a
 * matcher, D77's reasoning goes red here rather than quietly expiring.
 *
 * <h2>The floor underneath this file, which no test here can invoke</h2>
 *
 * <p>Servlet Spring Security does not let a mis-ordering through. {@code FilterChainProxy
 * .afterPropertiesSet} runs {@code WebSecurityFilterChainValidator}, which throws
 * {@code UnreachableFilterChainException} when an {@code any request} chain is published before a
 * narrower one — so the context <em>refuses to start</em> rather than silently serving the wrong chain.
 * Measured by putting {@code @Order(HIGHEST_PRECEDENCE)} on the generated chain's {@code @Bean}: 109
 * tests failed to load a context, with the exception naming both chains. That is quoted in D77 §3.
 *
 * <p><strong>It is deliberately not asserted.</strong> The validator is a package-private final class
 * in {@code org.springframework.security.config.annotation.web.builders} and the proxy exposes no way
 * to re-run it over a reordered list, so pinning it would mean reflecting into framework internals or
 * declaring a test class inside a Spring package. Recorded as a measurement instead of faked as a test
 * — a reader counting green tests here should not conclude the validator is covered.
 */
@IntegrationTest
@AutoConfigureMockMvc
class FilterChainPrecedenceIT {

    /** The estate-facing lookup booking calls — {@code decisions.md} D28. */
    private static final String INTERNAL_PATH = "/internal/professionals/p1/login";

    /** A public marketplace read, which spec §6 requires to work without a token. */
    private static final String PUBLIC_PATH = "/api/professionals";

    /**
     * A path no chain in this context narrows to and no authorization rule names.
     *
     * <p>Under {@code /api/} it would be caught by the generated chain's {@code .requestMatchers
     * ("/api/**").authenticated()}, which is a rule rather than the absence of one — so the last test
     * would be about that rule instead of about what the framework does with a request no rule reaches.
     */
    private static final String UNRULED_PATH = "/nothing/at/all";

    /** The two {@code @Bean} method names, which are the bean names. */
    private static final List<String> HAND_WRITTEN_CHAINS = List.of("internalApiFilterChain", "publicMarketplaceFilterChain");

    /** JHipster's, in the generated {@code SecurityConfiguration}. */
    private static final String GENERATED_CHAIN = "filterChain";

    @Autowired
    private FilterChainProxy proxy;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Autowired
    private MockMvc mockMvc;

    /**
     * Red when either annotation moves back onto its {@code @Configuration} class, which is where both
     * of them were until D77 and where {@code findAnnotationOnBean} answers {@code null} — measured.
     *
     * <p>Position alone cannot see that: the alphabet already puts these two in front, so the runtime
     * order below stays green with no readable annotation anywhere in the service.
     */
    @Test
    @DisplayName("each hand-written chain's precedence is declared where the container can read it")
    void eachHandWrittenChainsPrecedenceIsDeclared() {
        Order generated = beanFactory.findAnnotationOnBean(GENERATED_CHAIN, Order.class);
        int generatedOrder = generated == null ? Ordered.LOWEST_PRECEDENCE : generated.value();

        for (String bean : HAND_WRITTEN_CHAINS) {
            assertThat(beanFactory.containsBean(bean))
                .as("there is no %s bean — the @Configuration or the @Bean has gone, and with it the guard", bean)
                .isTrue();

            Order declared = beanFactory.findAnnotationOnBean(bean, Order.class);
            assertThat(declared)
                .as(
                    "%s carries no @Order the container can read. On the @Configuration class it is invisible — " +
                    "put it on the @Bean method. See decisions.md D77",
                    bean
                )
                .isNotNull();

            assertThat(declared.value())
                .as("%s must outrank the generated chain, not merely tie with it and win on class names", bean)
                .isLessThan(generatedOrder);
        }
    }

    /**
     * The premise that makes the ordering total rather than a matter of taste.
     *
     * <p>The generated chain names {@code /api/**}, {@code /v3/api-docs/**} and {@code /management/**}
     * in its rules and <em>nothing</em> in a {@code securityMatcher}, so it claims every request —
     * including both paths above. Two comments in this estate said it claimed only those three prefixes
     * and were corrected by D77.
     */
    @Test
    @DisplayName("the generated chain claims every request, so nothing here is disjoint from it")
    void theGeneratedChainClaimsEveryRequest() {
        for (String path : List.of(INTERNAL_PATH, PUBLIC_PATH, UNRULED_PATH)) {
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
     * change — it is the statement of what that change protects. It goes red only if something puts the
     * generated chain first, and servlet Spring Security refuses to start such a context at all, so in
     * practice this assertion is reached only while it passes.
     */
    @Test
    @DisplayName("a narrower chain claims each path before the generated chain does")
    void aNarrowerChainClaimsEachPathFirst() {
        List<SecurityFilterChain> chains = proxy.getFilterChains();
        int generated = chains.indexOf(generatedChain());
        assertThat(generated).as("the generated chain is not in FilterChainProxy's list at all").isNotNegative();

        for (String path : List.of(INTERNAL_PATH, PUBLIC_PATH)) {
            assertThat(firstChainClaiming(chains, path))
                .as(
                    "a chain BEFORE index %d must claim %s, or the generated chain handles it: %s becomes 401 for " +
                    "booking's lookup and for every anonymous marketplace read. See decisions.md D77",
                    generated,
                    path,
                    path
                )
                .isBetween(0, generated - 1);
        }
    }

    /**
     * The framework floor nothing in this repository chose, and the answer to a question two comments
     * here declined to look up.
     *
     * <p>Both {@link InternalApiSecurityConfiguration} and booking's webhook chain said that what
     * Spring Security does with an unmatched request is "a version-dependent detail nobody should have
     * to look up". On this stack it is a refusal: 401, from the generated chain's bearer-token entry
     * point, for a path its rules never name. So these chains <em>open</em> doors that were closed
     * rather than closing doors that were open — the same correction D74 made in the reactive stack,
     * re-measured here.
     *
     * <p>Pinned so that a Spring Security upgrade flipping it to a fall-through goes red here, rather
     * than turning catalog's entire unnamed surface into anonymous reads in silence.
     */
    @Test
    @DisplayName("a path no authorization rule names is refused, not passed through")
    void aPathNoRuleNamesIsRefused() throws Exception {
        assertThat(mockMvc.perform(get(UNRULED_PATH)).andReturn().getResponse().getStatus())
            .as(
                "%s matches no authorizeHttpRequests rule. If this is no longer a refusal, servlet Spring Security " +
                "has changed what it does with such a request and every unnamed path in catalog has become readable. " +
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
