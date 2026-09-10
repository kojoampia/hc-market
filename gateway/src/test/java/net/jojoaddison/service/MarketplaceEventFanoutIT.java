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
 *
 * <h2>Every assertion is scoped to one aggregate reference, and that is the whole of D76</h2>
 *
 * <p>The sink is a <strong>broadcast of an estate's events</strong>, and one bean's, shared by every
 * subscriber in the JVM. It is not this test's private channel and never was, so an assertion of the
 * form "everything on the sink is mine" is wrong however quiet the estate happens to be — backlog
 * NEW-35. Each method used to subscribe, wait for {@code received.size() >= 2} and then assert
 * {@code containsExactlyInAnyOrder} over the <em>whole</em> collection, which fails the moment
 * anything else emits. It did — one full {@code clean verify} in four when D76 measured it, one in
 * three when the item was raised.
 *
 * <p>What actually arrived was not a neighbouring method's event, which is what the item assumed.
 * {@code recipientsComeFromThePayload} runs <strong>first</strong> in this class — measured, in every
 * one of twelve runs — so nothing later in the file can have reached it. The two surplus logins came from
 * {@code MarketplaceStreamFramingIT}, which runs earlier in the same JVM, publishes {@code b-onwire-1}
 * to {@code healthconnect.booking.accepted}, and shares this class's static broker. This class's
 * context is a different one, so its {@code @KafkaListener} joins a fresh {@code ${random.uuid}} group,
 * and {@link SseKafkaTestContainer} sets {@code auto-offset-reset: earliest} — so it <em>replays the
 * whole topic</em> on startup, into whichever method happens to hold a subscription at that instant.
 *
 * <p>Two consequences, and both shape what is written below rather than being commentary:
 *
 * <ul>
 *   <li><strong>Isolating the methods from each other would not have fixed it</strong>, and restarting
 *       the context per method would have made it worse — a new context is a new group, and a new
 *       group replays everything. So each method discriminates <em>its own</em> events instead, by an
 *       {@code aggregateRef} it mints and nothing else in this JVM uses. What has to be unique is the
 *       <strong>whole value</strong>, across the whole run rather than across this file; the shared
 *       {@code b-fanout-} prefix is a reading aid, and catalog's {@code ErasureFanoutLegIT} — another
 *       project, another JVM — mints {@code b-fanout-1} without that mattering to anything here.
 *   <li><strong>The wait is a predicate about the events, not a count.</strong> {@code size() >= 2} is
 *       what the flake exploited: it cannot tell whose two arrived. Each method publishes a
 *       {@link #publishBarrier barrier} to the same single-partition topic <em>after</em> the event under
 *       test, so the barrier's arrival proves every emission the event was ever going to produce has
 *       already been recorded. That is what makes {@code containsExactlyInAnyOrder} sound rather than
 *       merely usually right: without it, an assertion that "nothing else was addressed" races the
 *       extra emission it exists to catch.
 * </ul>
 *
 * <p>None of the logins below is shared with another method or with {@code MarketplaceStreamFramingIT},
 * so if anything ever does leak past the scoping, the failure names its own publisher. Being unable to
 * do that is what sent NEW-35 to the wrong cause.
 */
@IntegrationTest
// BOTH, explicitly: this annotation supersedes the one on @IntegrationTest rather than adding
// to it, so naming only the broker here silently drops Mongo. See SseKafkaTestContainer.
@ImportTestcontainers({ MongoDbTestContainer.class, SseKafkaTestContainer.class })
class MarketplaceEventFanoutIT {

    private static final Duration ARRIVAL = Duration.ofSeconds(30);

    private static final String REQUESTED = "healthconnect.booking.requested";
    private static final String ACCEPTED = "healthconnect.booking.accepted";
    private static final String CANCELLED = "healthconnect.booking.cancelled";
    private static final String COMPLETED = "healthconnect.booking.completed";

    /* One per publication, never shared between methods, and unique BY FULL VALUE against everything
       else that publishes into this JVM's sink. The prefix is a reading aid and not the guarantee:
       catalog's ErasureFanoutLegIT already mints b-fanout-1/2/3, in a different Maven project and so a
       different JVM, which costs nothing here and is exactly why the uniqueness that matters is the
       whole string. See the class comment. */
    private static final String BOTH_PARTIES = "b-fanout-both-parties";
    private static final String ADDRESSED = "b-fanout-addressed";
    private static final String SOMEBODY_ELSE = "b-fanout-somebody-else";
    private static final String FROM_PAYLOAD = "b-fanout-from-payload";

    @Autowired
    private MarketplaceEventFanout fanout;

    /**
     * An envelope in the estate's shape, addressed to a customer and a professional.
     *
     * <p>The {@code actor} is deliberately a login <strong>nobody addresses</strong> rather than the
     * professional it used to be. It is the one envelope field that names a person and is not a
     * recipient, so making it distinct turns the exactness assertions below into a live catch: an
     * addressing rule that started reading the envelope instead of the payload would put it on the
     * wire, and while it duplicated the professional nothing could have noticed.
     */
    private static String envelope(String type, String aggregateRef, String customerLogin, String professionalLogin) {
        return """
        {
          "eventId": "e-%s",
          "type": "%s",
          "occurredAt": "2026-08-31T09:00:00Z",
          "aggregateRef": "%s",
          "actor": "actor.%s",
          "payload": { "bookingRef": "%s", "customerLogin": "%s", "professionalLogin": "%s" }
        }
        """.formatted(aggregateRef, type, aggregateRef, aggregateRef, aggregateRef, customerLogin, professionalLogin);
    }

    private static void publish(String topic, String aggregateRef, String body) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SseKafkaTestContainer.bootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>(topic, aggregateRef, body));
            producer.flush();
        }
    }

    /**
     * A second event on the same topic, published after the one under test, whose only job is to be
     * seen — and the reason the exact assertions in this class are sound rather than lucky.
     *
     * <p>{@link SseKafkaTestContainer} creates every topic with <strong>one partition</strong>, and
     * the listener container is single-threaded, so records on a topic are consumed strictly in the
     * order they were produced. The barrier is produced after the event under test and its
     * {@code flush} has returned, so by the time its own emission reaches a subscriber, every
     * {@code tryEmitNext} the earlier record was ever going to make has already returned — on the same
     * thread, into the same list. That is what lets a method assert that nothing <em>else</em> was
     * addressed without racing the emission it is looking for.
     *
     * <p>It carries a blank professional deliberately, so it emits exactly one event: a blank login is
     * dropped by the fan-out's own {@code add}. Its reference is a {@code b-fanout-barrier-} one, so
     * it is excluded from every assertion by the same scoping as everything else.
     */
    private static void publishBarrier(String topic, String tag) {
        publish(topic, "b-fanout-barrier-" + tag, envelope(topic, "b-fanout-barrier-" + tag, barrierLogin(tag), ""));
    }

    private static String barrierLogin(String tag) {
        return "barrier." + tag;
    }

    /**
     * Publishes the barrier and waits for that one named event — a predicate about an event this
     * method minted, never a count of whatever turned up.
     */
    private static void awaitBarrier(List<UserEvent> received, String topic, String tag) {
        publishBarrier(topic, tag);
        Awaitility.await().atMost(ARRIVAL).until(() -> received.stream().anyMatch(e -> barrierLogin(tag).equals(e.recipientLogin())));
    }

    /** Only the events of one publication, discriminated by the reference this test minted for it. */
    private static List<UserEvent> eventsFor(List<UserEvent> received, String aggregateRef) {
        return received.stream().filter(event -> aggregateRef.equals(event.aggregateRef())).toList();
    }

    private static List<String> recipientsFor(List<UserEvent> received, String aggregateRef) {
        return eventsFor(received, aggregateRef).stream().map(UserEvent::recipientLogin).toList();
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
            publish(CANCELLED, BOTH_PARTIES, envelope(CANCELLED, BOTH_PARTIES, "abena.bothparties", "yaw.bothparties"));
            awaitBarrier(received, CANCELLED, "both-parties");

            assertThat(recipientsFor(received, BOTH_PARTIES)).containsExactlyInAnyOrder("abena.bothparties", "yaw.bothparties");
            // Not vacuous: the assertion above establishes that this list holds exactly two events.
            assertThat(eventsFor(received, BOTH_PARTIES)).allSatisfy(event -> {
                // The TYPE is the domain event, unprefixed — the estate prefix names a Kafka topic
                // and must never reach a client.
                assertThat(event.type()).isEqualTo(CANCELLED);
                assertThat(event.aggregateRef()).isEqualTo(BOTH_PARTIES);
                /* The payload is plain maps now, not a JsonNode — see MarketplaceEventFanout.plain.
                   A JsonNode here was serialised by its bean properties on the way out to clients,
                   so every SSE frame carried isArray/isBigDecimal/nodeType instead of the event. */
                assertThat(event.payload()).isInstanceOf(java.util.Map.class);
                assertThat(((java.util.Map<?, ?>) event.payload()).get("bookingRef")).isEqualTo(BOTH_PARTIES);
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
     *
     * <p><strong>The control is now about this method's own two events, and that is the point of it.</strong>
     * It used to assert that {@code everything} merely <em>contained</em> the foreign login, which a
     * neighbour's event satisfies just as well — so the run in which the flake fired had this method
     * pass on four events that were not its own, establishing nothing about {@code streamFor} at all.
     * A green that proves nothing is the harder half of NEW-35: the red one at least got looked at.
     */
    @Test
    @DisplayName("a filtered stream carries the addressed user's events and nobody else's")
    void aFilteredStreamCarriesOnlyItsOwnersEvents() {
        List<UserEvent> mine = new CopyOnWriteArrayList<>();
        List<UserEvent> everything = new CopyOnWriteArrayList<>();
        Disposable filtered = fanout.streamFor("esi.addressed").subscribe(mine::add);
        Disposable control = fanout.stream().subscribe(everything::add);
        try {
            publish(ACCEPTED, SOMEBODY_ELSE, envelope(ACCEPTED, SOMEBODY_ELSE, "ama.somebodyelse", "kwame.somebodyelse"));
            publish(COMPLETED, ADDRESSED, envelope(COMPLETED, ADDRESSED, "esi.addressed", "nana.addressed"));

            // One barrier per TOPIC, because the ordering a barrier rests on is a partition's and
            // these two events are on different ones. Two topics rather than one deliberately: the
            // listener names six and nothing else in the suite exercises `completed`.
            awaitBarrier(everything, ACCEPTED, "somebody-else");
            awaitBarrier(everything, COMPLETED, "addressed");

            // The control: both events really did reach the sink, so the emptiness asserted below
            // cannot be "the filter received nothing because nothing was published".
            assertThat(recipientsFor(everything, SOMEBODY_ELSE)).containsExactlyInAnyOrder("ama.somebodyelse", "kwame.somebodyelse");
            assertThat(recipientsFor(everything, ADDRESSED)).containsExactlyInAnyOrder("esi.addressed", "nana.addressed");

            // The boundary itself: of this method's own two events, the filtered stream carried the
            // owner's copy of the one addressed to her, and no part of the other.
            assertThat(recipientsFor(mine, SOMEBODY_ELSE)).isEmpty();
            assertThat(recipientsFor(mine, ADDRESSED)).containsExactly("esi.addressed");
        } finally {
            filtered.dispose();
            control.dispose();
        }
    }

    /**
     * A booking.requested concerns the professional and the customer both, but the addressing must
     * come from the payload rather than from the event type — a stream that guessed would eventually
     * guess wrong, and the filter in the resource trusts this value completely.
     *
     * <p>The envelope's {@code actor} is the decoy this now also catches: it names a person, it is not
     * a recipient, and the exact assertion below goes red if it is ever addressed. Watched failing.
     */
    @Test
    @DisplayName("the recipients come from the payload, not from the event type")
    void recipientsComeFromThePayload() {
        List<UserEvent> received = new CopyOnWriteArrayList<>();
        Disposable subscription = fanout.stream().subscribe(received::add);
        try {
            publish(REQUESTED, FROM_PAYLOAD, envelope(REQUESTED, FROM_PAYLOAD, "afia.frompayload", "kofi.frompayload"));
            awaitBarrier(received, REQUESTED, "from-payload");

            assertThat(recipientsFor(received, FROM_PAYLOAD)).containsExactlyInAnyOrder("afia.frompayload", "kofi.frompayload");
        } finally {
            subscription.dispose();
        }
    }
}
