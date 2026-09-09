package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * JHipster's Kafka sample must not answer anybody — backlog NEW-17, {@code decisions.md} D59.
 *
 * <p>This is the gateway's copy, and it is the one that needed a decision rather than a deletion. The
 * four microservices' samples carry {@code /publish}, {@code /register} and {@code /unregister}; the
 * gateway's carried {@code /publish} and <strong>{@code GET /consume}</strong>, which is the endpoint
 * CLAUDE.md and D25/D29 have described for months as the thing
 * {@link net.jojoaddison.web.rest.MarketplaceStreamResource} is <em>not</em>: a {@code unicast()} sink
 * so the second connected client errors, no {@code text/event-stream} content type, and no per-user
 * filtering, so every subscriber would see every event and customers would read each other's
 * bookings.
 *
 * <p><strong>The argument for deleting it rather than leaving it beside the real one is now a
 * measurement.</strong> {@code /consume} drains {@code broker.KafkaConsumer}, which binds to
 * {@code sse-topic}; that topic's end offset on the shared broker is <strong>0</strong>, so in the
 * estate's whole life nothing has ever been published to it. The endpoint has never carried a byte,
 * and the only thing it could ever carry is somebody else's event.
 *
 * <p>{@code POST /publish} is the half this shares with the other four: an arbitrary caller-supplied
 * string onto the broker four products borrow (D27), on a topic created on demand. D54 asserted that
 * and then withdrew it on the grounds that the {@code kafka} profile is active nowhere; <strong>both
 * readings were wrong and the first was wrong in the right direction</strong>. {@code application.yml}
 * lists {@code kafka} in {@code spring.profiles.group.dev} <em>and</em>
 * {@code spring.profiles.group.prod}, so the profile is active in every environment this estate has —
 * read off the running quality container, which reports
 * {@code activeProfiles: [secret-samples, kafka, api-docs, dev, test]}.
 *
 * <p>{@code broker.KafkaConsumer} <strong>stays</strong>, with no reader. It is named by
 * {@code spring.cloud.function.definition} in a generated file, and with the resource gone it maps no
 * URL — D54's rule for an orphaned generated class, and the alternative is editing a generated config
 * file to buy nothing. Its sink buffers without a subscriber, which is exactly as true today as it was
 * yesterday, because {@code /consume} never had a caller either; that, and the supplier beside it, are
 * backlog NEW-21.
 *
 * <p>This is a <strong>new file</strong>, so {@code jhipster jdl ../jdl/gateway.jdl --force} leaves it
 * in place while it puts the resource back. That is the point of it.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_TIMEOUT)
@WithMockUser(username = "a.stranger")
class KafkaSampleIsNotAnApiIT {

    private static final String SAMPLE = "/api/healthconnect-gateway-kafka";

    @Autowired
    private WebTestClient client;

    /**
     * A positive control, because every other test here is a negative one — D54's review, finding 3.
     *
     * <p>The refusal set below is {@code 401/403/404/405} rather than a bare 404 on purpose: deletion
     * gives 404 today and a future gate would give 403. The price of that latitude is that a 404 for
     * the wrong reason reads exactly like a 404 for the right one, so one endpoint that must answer
     * establishes that the gateway is up, mapped and serving in the same run. {@code /api/authenticate}
     * answers <strong>204</strong> for an authenticated caller and 401 for nobody, and only the
     * handler can produce the 204 — a refusal from the security chain could not.
     */
    @Test
    @DisplayName("the gateway is actually serving — otherwise every refusal below is meaningless")
    void theSurvivingApiStillAnswers() {
        client.get().uri("/api/authenticate").exchange().expectStatus().isNoContent();
    }

    /**
     * {@code message} is supplied deliberately. Without it a restored resource would answer 400 on a
     * missing required request parameter, and this guard would pass against a live publish endpoint.
     */
    @Test
    @DisplayName("nobody may publish an arbitrary string onto the shared broker")
    void nobodyMayPublish() {
        String path = SAMPLE + "/publish";
        assertThat(statusOf(path, () -> client.post().uri(builder -> builder.path(path).queryParam("message", "a-stranger-was-here").build())))
            .as("POST %s must not answer — it is JHipster's Kafka sample, restored. See backlog NEW-17 and decisions.md D59.", path)
            .isIn(401, 403, 404, 405);
    }

    @Test
    @DisplayName("nor may anybody drain the unfiltered sample stream")
    void nobodyMayConsume() {
        String path = SAMPLE + "/consume";
        assertThat(statusOf(path, () -> client.get().uri(path)))
            .as("GET %s must not answer — it is JHipster's Kafka sample, restored. See backlog NEW-17 and decisions.md D59.", path)
            .isIn(401, 403, 404, 405);
    }

    /**
     * The status, or an {@link AssertionError} naming the path.
     *
     * <p>A restored {@code /consume} returns a {@code Flux} from a sink that never completes, and this
     * client is bound to the application context rather than to a port — so the blocking read behind
     * {@code exchange()} would <strong>time out</strong> rather than return a status, and the guard
     * would report a harness fault instead of the door it was asked about. Any failure here means the
     * same thing as a 200 does: something answered on that path.
     */
    private int statusOf(String path, java.util.function.Supplier<WebTestClient.RequestHeadersSpec<?>> request) {
        try {
            return request.get().exchange().returnResult(String.class).getStatus().value();
        } catch (RuntimeException answered) {
            throw new AssertionError(
                path + " neither refused nor returned a status — something is mapped there and it held the connection open. " +
                "That is JHipster's Kafka sample, restored. See backlog NEW-17 and decisions.md D59.",
                answered
            );
        }
    }
}
