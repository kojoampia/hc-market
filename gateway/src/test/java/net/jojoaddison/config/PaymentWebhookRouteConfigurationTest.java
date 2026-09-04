package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * What the gateway lets through unauthenticated, and what it emphatically does not —
 * {@code decisions.md} D45.
 *
 * <p>This chain is the second of the two changes that expose booking's payment webhook, and it is
 * the one whose absence is silent: with the compose route and without this, every callback is
 * refused at the edge before it reaches booking, which reads as a broken provider integration. The
 * matcher is therefore asserted rather than described.
 *
 * <p>Built by hand rather than through a Spring context, so this stays a unit test: what is being
 * checked is which exchanges the chain claims, and {@link SecurityWebFilterChain#matches} answers
 * that without a running gateway.
 */
class PaymentWebhookRouteConfigurationTest {

    private final SecurityWebFilterChain chain = new PaymentWebhookRouteConfiguration().paymentWebhookRouteFilterChain(
        ServerHttpSecurity.http()
    );

    private boolean claims(HttpMethod method, String path) {
        return Boolean.TRUE.equals(
            chain.matches(MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build())).block()
        );
    }

    @Test
    @DisplayName("a provider's callback is claimed by this chain, so it is not asked for a token")
    void theCallbackPathIsPermitted() {
        assertThat(claims(HttpMethod.POST, "/services/healthconnectbooking/webhooks/payments/paystack")).isTrue();
        assertThat(claims(HttpMethod.POST, "/services/healthconnectbooking/webhooks/payments/momo")).isTrue();
    }

    /**
     * Everything one character wider is refused, and each of these is a real way to get it wrong.
     *
     * <p>A {@code GET} under the same prefix would be an unauthenticated read of what the platform
     * knows about somebody's payment. {@code /services/healthconnectbooking/**} — the shape somebody
     * reaches for when the webhook "still does not work" — would publish every booking, dispute and
     * erasure endpoint booking has to anonymous callers, which is D28's defect arriving through the
     * door D45 opened. And no other service has a webhook path at all.
     */
    @Test
    @DisplayName("nothing else under /services is claimed, and nothing but POST")
    void nothingElseIsPermitted() {
        assertThat(claims(HttpMethod.GET, "/services/healthconnectbooking/webhooks/payments/paystack")).isFalse();
        assertThat(claims(HttpMethod.DELETE, "/services/healthconnectbooking/webhooks/payments/paystack")).isFalse();
        assertThat(claims(HttpMethod.POST, "/services/healthconnectbooking/api/bookings")).isFalse();
        assertThat(claims(HttpMethod.POST, "/services/healthconnectcatalog/webhooks/payments/paystack")).isFalse();
        assertThat(claims(HttpMethod.POST, "/services/healthconnectcatalog/internal/professionals/p1/login")).isFalse();
    }

    /**
     * The prefix itself is claimed, because {@code /**} matches zero segments, and that is harmless
     * rather than a hole.
     *
     * <p>Asserted rather than left as a surprise: it looks like over-matching and is not. Booking
     * maps no handler at {@code /webhooks} or {@code /webhooks/payments}, so an unauthenticated POST
     * there is routed and answered 404 by booking — and everything that is not a POST is denied twice,
     * here and by booking's own {@code PaymentWebhookSecurityConfiguration}. Narrowing the pattern to
     * force a provider segment would buy nothing and would make the string stop matching the compose
     * predicate character for character, which CI compares.
     */
    @Test
    @DisplayName("the prefix itself is claimed too, which is what /** means and costs nothing")
    void thePrefixItselfIsClaimed() {
        assertThat(claims(HttpMethod.POST, "/services/healthconnectbooking/webhooks")).isTrue();
        assertThat(claims(HttpMethod.GET, "/services/healthconnectbooking/webhooks")).isFalse();
    }
}
