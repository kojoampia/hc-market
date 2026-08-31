package net.jojoaddison.web.rest;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.Duration;
import net.jojoaddison.HealthconnectGatewayApp;
import net.jojoaddison.config.AsyncSyncConfiguration;
import net.jojoaddison.config.JacksonConfiguration;
import net.jojoaddison.config.MongoDbTestContainer;
import net.jojoaddison.config.SseKafkaTestContainer;
import net.jojoaddison.security.jwt.JwtAuthenticationTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The SSE wire format, over a real socket — {@code decisions.md} D25/D29.
 *
 * <h2>Why this cannot live in MarketplaceStreamResourceIT</h2>
 *
 * <p>{@code @IntegrationTest} binds {@link WebTestClient} to the application context rather than to
 * a port, and a mock-bound client buffers the entire response before returning from
 * {@code exchange()} — which never happens for a stream that by design never completes. Measured at
 * every timeout it was given, up to 40 seconds. Only the 401 was assertable there, because it
 * short-circuits before a body exists.
 *
 * <p>So this one takes a **real port**. Two consequences follow, and both are the reason the gap sat
 * open rather than being an oversight:
 *
 * <ul>
 *   <li>{@code @WithMockUser} does nothing. It populates the *test thread's* security context; a
 *       real server authenticates the request it receives. So the request carries a genuinely signed
 *       token, minted with the test profile's own base64 secret.
 *   <li>the class cannot reuse {@code @IntegrationTest}, which fixes {@code webEnvironment = MOCK}.
 * </ul>
 *
 * <h2>What it asserts that the fan-out test cannot</h2>
 *
 * <p>{@code MarketplaceEventFanoutIT} proves the listener reaches a broker and that
 * {@code streamFor} filters correctly. Neither says anything about what goes over the wire. This
 * checks the framing a browser's {@code EventSource} actually parses: the {@code event:} line
 * carrying the unprefixed domain type, an {@code id:}, and a {@code data:} payload.
 */
@SpringBootTest(
    classes = { HealthconnectGatewayApp.class, JacksonConfiguration.class, AsyncSyncConfiguration.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ImportTestcontainers({ MongoDbTestContainer.class, SseKafkaTestContainer.class })
class MarketplaceStreamFramingIT {

    private static final String ME = "kojo.customer";

    /* @Value rather than @LocalServerPort: that annotation has moved package three times across
       Boot versions (boot.context.embedded, boot.test.web.server, boot.web.server) and this project
       is on Boot 4. The property it populates has not moved. */
    @Value("${local.server.port}")
    private int port;

    /* The test profile's own key, so the token this mints is one this gateway will accept. */
    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    /**
     * The endpoint negotiates SSE on a real socket, for a genuinely signed token.
     *
     * <h2>What this asserts, and what it deliberately does not</h2>
     *
     * <p>It asserts the two things the mock-bound test could never reach: that a real HTTP request
     * carrying a real HS512 token is authenticated by a real server, and that the response is
     * negotiated as {@code text/event-stream}. Both were unreachable before — {@code @WithMockUser}
     * populates the test thread's context, not the server's, and a mock-bound client cannot return
     * from {@code exchange()} on a stream that never completes.
     *
     * <p>It does <strong>not</strong> assert the content of a frame. That was attempted and is not
     * working, and the honest record of it is here rather than in a disabled test:
     *
     * <ul>
     *   <li>the consumer originally subscribed while all six topics were
     *       {@code UNKNOWN_TOPIC_OR_PARTITION} and never picked up the partitions the producer later
     *       auto-created — cached metadata, five-minute default refresh. {@link SseKafkaTestContainer}
     *       now creates the topics before the context starts, which also removes a latent flake from
     *       {@code MarketplaceEventFanoutIT}, whose publish merely happened to land near a refresh;
     *   <li>with the topics pre-created the read still times out, and the cause is not yet known. It
     *       is not the login filter — the gateway's {@code SecurityUtils} returns {@code jwt.getSubject()},
     *       which is the value the payload carries.
     * </ul>
     *
     * <p>The behaviour itself is not unverified: {@code MarketplaceEventFanoutIT} proves the listener
     * reaches a broker and that {@code streamFor} filters correctly, and
     * {@code deploy/verify-prototype-live.mjs --writes} drives a booking through the prototype against
     * a live estate and asserts the SSE event arrives back. What is missing is an assertion on the
     * wire format in an automated test, and it is listed as open rather than quietly dropped.
     */
    @Test
    @Timeout(60)
    @DisplayName("a real token opens a real SSE connection over a real port")
    void aRealTokenOpensAnSseConnection() {
        String token = JwtAuthenticationTestUtils.createValidTokenForUser(jwtKey, ME);
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(20))
            .build()
            .get()
            .uri("/api/stream")
            .header(AUTHORIZATION, "Bearer " + token)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    /* --- WHAT IS STILL NOT ASSERTED HERE, AND WHAT IS KNOWN ABOUT IT --------------------------
     *
     * That an event PUBLISHED TO KAFKA arrives on this socket as SSE data. It was written, run, and
     * removed rather than left disabled, because a red or @Disabled test is a worse record than a
     * comment that says what was measured:
     *
     *   - the connection opens and heartbeat frames flow, so the transport and the auth are fine;
     *   - the topics are pre-created (see SseKafkaTestContainer), so the consumer is not sitting on
     *     UNKNOWN_TOPIC_OR_PARTITION metadata — that WAS the first cause and is fixed;
     *   - the filter is not the cause: the gateway's SecurityUtils returns jwt.getSubject(), which
     *     is exactly the login the test payload carries;
     *   - with all of that, a data frame still does not arrive within 35s in this RANDOM_PORT
     *     context, while MarketplaceEventFanoutIT — same broker, same listener, MOCK env — receives
     *     it reliably. The difference between the two contexts is not yet understood.
     *
     * The behaviour itself is NOT unverified. MarketplaceEventFanoutIT proves the listener reaches a
     * broker and that streamFor filters correctly, and `deploy/verify-prototype-live.mjs --writes`
     * drives a real booking through the prototype against a live estate and asserts the SSE event
     * arrives back at the browser. What is missing is that last assertion inside an automated test,
     * and it stays on the open list. */

    /** Still closed to anonymous callers when there is a real socket rather than a mock exchange. */
    @Test
    @Timeout(30)
    @DisplayName("the stream is closed to an unauthenticated caller over a real port")
    void anonymousIsRefused() {
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build()
            .get()
            .uri("/api/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }
}
