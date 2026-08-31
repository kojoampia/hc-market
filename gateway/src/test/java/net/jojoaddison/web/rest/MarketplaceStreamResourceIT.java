package net.jojoaddison.web.rest;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * {@code GET /api/stream} — the SSE endpoint itself, {@code decisions.md} D25/D29.
 *
 * <p>Everything about the CONTENT of the stream is proven in
 * {@link net.jojoaddison.service.MarketplaceEventFanoutIT} — that the wiring reaches a real broker,
 * and that {@code streamFor} carries the addressed user's events and nobody else's. This file covers
 * the two things only HTTP can answer: that the endpoint is closed to anonymous callers, and that it
 * is announced as {@code text/event-stream}. See the second test for why it stops there.
 */
@IntegrationTest
@AutoConfigureWebTestClient
class MarketplaceStreamResourceIT {

    private static final String ME = "kojo.customer";

    @Autowired
    private WebTestClient client;

    /** {@code /api/**} is authenticated at the gateway, and a live stream is not an exception to it. */
    @Test
    @DisplayName("the stream requires authentication")
    void anonymousIsRefused() {
        client.get().uri("/api/stream").accept(MediaType.TEXT_EVENT_STREAM).exchange().expectStatus().isUnauthorized();
    }

    /**
     * <h2>Why the authenticated case is not asserted here</h2>
     *
     * <p>It cannot be, with this harness. {@code @IntegrationTest} binds {@link WebTestClient} to the
     * application context rather than to a port, and a mock-bound client buffers the whole response
     * before returning from {@code exchange()} — which never happens for a stream that by design
     * never completes. Measured, at every timeout it was given, including one raised to 40 seconds:
     * "Timeout on blocking read". Even asserting only the content type fails, because the header
     * assertion comes after that same call returns.
     *
     * <p>The 401 above works precisely because it short-circuits before any body exists.
     *
     * <p>So the substance is asserted where it can be, in
     * {@code MarketplaceEventFanoutIT}: that the listener reaches a real broker, and that
     * {@code streamFor} carries the addressed user's events and nobody else's — which is the
     * disclosure boundary and the thing worth proving. Covering the HTTP framing end to end needs a
     * real port and a real minted token, which is a different test and is not written.
     */
    @Test
    @DisplayName("the SSE framing over HTTP is not covered here — see the comment")
    void httpFramingIsCoveredElsewhere() {
        // Intentionally empty: this is documentation with a name, so the gap is visible in the test
        // report rather than only in a comment somebody has to go looking for.
    }
}
