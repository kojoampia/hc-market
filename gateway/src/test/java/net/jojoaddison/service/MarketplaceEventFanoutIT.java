package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.config.MongoDbTestContainer;
import net.jojoaddison.config.SseKafkaTestContainer;
import net.jojoaddison.service.MarketplaceEventFanout.UserEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import reactor.core.Disposable;

/**
 * Proof that the SSE fan-out is actually connected to a broker — {@code decisions.md} D25/D29.
 *
 * <h2>Why this test is the important one</h2>
 *
 * <p>{@code @KafkaListener} that is never wired does not fail. The context starts, the bean exists,
 * the endpoint answers, the stream opens, and nothing ever arrives on it. Every other check in this
 * repository would stay green — the gateway is healthy, the routes work, the SSE connection is
 * established — and the only symptom would be a live channel that is silent.
 *
 * <p>That risk is real here rather than theoretical: the gateway carries
 * {@code spring-cloud-starter-stream-kafka} and no {@code spring-boot-starter-kafka}, and everything
 * JHipster generates for this application goes through Spring Cloud Stream bindings instead. So this
 * publishes a real envelope to a real broker and waits for it to come out of the sink.
 *
 * <p>It is deliberately about the wiring and the addressing, not about HTTP. The SSE framing is
 * {@code MarketplaceStreamResource}'s and is covered separately.
 */
@IntegrationTest
// BOTH, explicitly: this annotation supersedes the one on @IntegrationTest rather than adding
// to it, so naming only the broker here silently drops Mongo. See SseKafkaTestContainer.
@ImportTestcontainers({ MongoDbTestContainer.class, SseKafkaTestContainer.class })
class MarketplaceEventFanoutIT {

    private static final Duration ARRIVAL = Duration.ofSeconds(30);

    @Autowired
    private MarketplaceEventFanout fanout;

    private static String envelope(String type, String customerLogin, String professionalLogin) {
        return """
        {
          "eventId": "e-%s",
          "type": "%s",
          "occurredAt": "2026-08-31T09:00:00Z",
          "aggregateRef": "b-sse-1",
          "actor": "%s",
          "payload": { "bookingRef": "b-sse-1", "customerLogin": "%s", "professionalLogin": "%s" }
        }
        """.formatted(type.hashCode(), type, professionalLogin, customerLogin, professionalLogin);
    }

    private static void publish(String topic, String body) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SseKafkaTestContainer.bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>(topic, "b-sse-1", body));
            producer.flush();
        }
    }

    /**
     * The whole point: an event published to the estate's topic reaches the in-memory broadcast,
     * addressed to <em>both</em> sides of the booking.
     */
    @Test
    @DisplayName("an event published to Kafka reaches the fan-out, addressed to both parties")
    void anEventPublishedToKafkaReachesTheFanout() {
        List<UserEvent> received = new CopyOnWriteArrayList<>();
        Disposable subscription = fanout.stream().subscribe(received::add);
        try {
            publish("healthconnect.booking.cancelled", envelope("healthconnect.booking.cancelled", "kojo.customer", "akosua.mensah"));

            Awaitility.await().atMost(ARRIVAL).until(() -> received.size() >= 2);

            assertThat(received).extracting(UserEvent::recipientLogin).containsExactlyInAnyOrder("kojo.customer", "akosua.mensah");
            assertThat(received).allSatisfy(event -> {
                // The TYPE is the domain event, unprefixed — the estate prefix names a Kafka topic
                // and must never reach a client.
                assertThat(event.type()).isEqualTo("healthconnect.booking.cancelled");
                assertThat(event.aggregateRef()).isEqualTo("b-sse-1");
                /* The payload is plain maps now, not a JsonNode — see MarketplaceEventFanout.plain.
                   A JsonNode here was serialised by its bean properties on the way out to clients,
                   so every SSE frame carried isArray/isBigDecimal/nodeType instead of the event. */
                assertThat(event.payload()).isInstanceOf(java.util.Map.class);
                assertThat(((java.util.Map<?, ?>) event.payload()).get("bookingRef")).isEqualTo("b-sse-1");
            });
        } finally {
            subscription.dispose();
        }
    }

    /**
     * <strong>The disclosure boundary.</strong> {@code streamFor} is the only thing standing between
     * one customer and everybody else's bookings, so it is asserted here — against a real broker,
     * with no HTTP client in the way — rather than through the endpoint.
     *
     * <p>Both events are published; only the addressed one may appear on the filtered stream, and the
     * unfiltered stream is used as the control so a test that simply received nothing cannot pass.
     */
    @Test
    @DisplayName("a filtered stream carries the addressed user's events and nobody else's")
    void aFilteredStreamCarriesOnlyItsOwnersEvents() {
        List<UserEvent> mine = new CopyOnWriteArrayList<>();
        List<UserEvent> everything = new CopyOnWriteArrayList<>();
        Disposable filtered = fanout.streamFor("kojo.customer").subscribe(mine::add);
        Disposable control = fanout.stream().subscribe(everything::add);
        try {
            publish("healthconnect.booking.accepted", envelope("healthconnect.booking.accepted", "ama.other", "akosua.mensah"));
            publish("healthconnect.booking.completed", envelope("healthconnect.booking.completed", "kojo.customer", "akosua.mensah"));

            // Four emissions in total: two events, each addressed to two parties.
            Awaitility.await().atMost(ARRIVAL).until(() -> everything.size() >= 4);

            assertThat(mine).extracting(UserEvent::recipientLogin).containsOnly("kojo.customer");
            assertThat(mine).hasSize(1);
            assertThat(everything).extracting(UserEvent::recipientLogin).contains("ama.other");
        } finally {
            filtered.dispose();
            control.dispose();
        }
    }

    /**
     * A booking.requested concerns the professional and the customer both, but the addressing must
     * come from the payload rather than from the event type — a stream that guessed would eventually
     * guess wrong, and the filter in the resource trusts this value completely.
     */
    @Test
    @DisplayName("the recipients come from the payload, not from the event type")
    void recipientsComeFromThePayload() {
        List<UserEvent> received = new CopyOnWriteArrayList<>();
        Disposable subscription = fanout.stream().subscribe(received::add);
        try {
            publish("healthconnect.booking.requested", envelope("healthconnect.booking.requested", "ama.other", "kwame.trainer"));

            Awaitility.await().atMost(ARRIVAL).until(() -> received.size() >= 2);
            assertThat(received).extracting(UserEvent::recipientLogin).containsExactlyInAnyOrder("ama.other", "kwame.trainer");
        } finally {
            subscription.dispose();
        }
    }
}
