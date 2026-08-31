package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Turns the estate's Kafka events into a live in-memory broadcast, so the gateway can push them to
 * whichever customers and professionals happen to be connected — {@code decisions.md} D25/D29.
 *
 * <h2>Why the gateway, and not messaging</h2>
 *
 * <p>D25 settled this: the gateway is the only reactive application in the estate and the only one
 * already holding a connection to every client, so a long-lived per-user stream costs nothing
 * structurally here and would cost a thread per subscriber in an imperative service. {@code messaging}
 * keeps doing what it does — writing durable notification rows — and this is the live channel beside
 * it, not a replacement for it. A client that was disconnected reads {@code /api/notifications} and
 * misses nothing.
 *
 * <h2>What is wrong with the generated scaffold this replaces</h2>
 *
 * <p>JHipster generates {@code broker.KafkaConsumer} and {@code /api/healthconnect-gateway-kafka/consume},
 * and they look like this feature. They are a demo, and every difference matters:
 *
 * <ul>
 *   <li>the sink is {@code unicast()} — the <strong>second</strong> connected client gets an error,
 *       so it cannot serve two users at once;
 *   <li>there is no {@code text/event-stream} content type, so it is not SSE;
 *   <li>there is <strong>no per-user filtering</strong>, so every subscriber sees every event —
 *       customers would read each other's bookings;
 *   <li>it binds to {@code sse-topic}, which nothing in hc-market publishes to.
 * </ul>
 *
 * <p>Both are left in place because they are generated and regeneration would put them back anyway;
 * this is a new file beside them. Anything reading {@code /consume} should be treated as reading a
 * sample, not a feature.
 *
 * <h2>directBestEffort, deliberately</h2>
 *
 * <p>{@code multicast().directBestEffort()} broadcasts to whoever is attached right now and
 * <strong>drops</strong> for a subscriber too slow to keep up, rather than buffering or failing the
 * emitter. That is the right trade for a live channel and the wrong one for anything durable: a
 * dropped toast is invisible, a dropped notification row is a bug. The durable copy is messaging's,
 * which is exactly why this one is allowed to be lossy.
 *
 * <p>It also means a Kafka listener thread can never block on a stalled HTTP client, which is the
 * failure that would otherwise take consumption down for everybody.
 *
 * <h2>The consumer group is unique per instance, which inverts the estate's usual rule</h2>
 *
 * <p>Everywhere else in this estate a shared, explicit group is correct: work is divided, each event
 * is handled once, and an anonymous group loses committed offsets across restarts (see the note in
 * {@code quality/compose.yml}). Here the opposite holds. Every gateway instance must see
 * <strong>every</strong> event, because the user it needs to reach may be connected to any of them —
 * with a shared group two instances would each get half the partitions and half the connected users
 * would silently never be told anything.
 *
 * <p>So the group carries {@code ${random.uuid}} and the offsets are deliberately disposable. Events
 * published while an instance was down are worthless to a live stream by definition; the durable
 * record is messaging's job.
 */
@Service
public class MarketplaceEventFanout {

    private static final Logger LOG = LoggerFactory.getLogger(MarketplaceEventFanout.class);

    private final ObjectMapper mapper;
    private final Sinks.Many<UserEvent> sink = Sinks.many().multicast().directBestEffort();

    public MarketplaceEventFanout(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Everything currently being broadcast, addressed but unfiltered. */
    public Flux<UserEvent> stream() {
        return sink.asFlux();
    }

    /**
     * Everything addressed to one login, and <strong>nothing else</strong>.
     *
     * <p>This lives here rather than in the resource because addressing is this class's concern from
     * end to end — it decides who an event is for in {@link #recipientsOf}, so it should also be what
     * decides who may see it. Keeping the two together means the disclosure boundary is one file and
     * one line, and can be tested against a real broker without an HTTP client in the way.
     */
    public Flux<UserEvent> streamFor(String login) {
        return sink.asFlux().filter(event -> login.equals(event.recipientLogin()));
    }

    /**
     * The topics carry their canonical names as inline defaults for the reason D29 records: the test
     * configuration shadows the main one, and a bare placeholder fails the context with "Could not
     * resolve placeholder", which reads as a typo rather than as a config file that was never loaded.
     *
     * <p>{@code healthconnect.topics.prefix} applies here too. A gateway left on the unprefixed names
     * while its estate publishes prefixed ones is silent in both directions — nothing errors, no
     * client ever receives anything.
     */
    @KafkaListener(
        topics = {
            "${healthconnect.topics.booking-requested:healthconnect.booking.requested}",
            "${healthconnect.topics.booking-accepted:healthconnect.booking.accepted}",
            "${healthconnect.topics.booking-declined:healthconnect.booking.declined}",
            "${healthconnect.topics.booking-cancelled:healthconnect.booking.cancelled}",
            "${healthconnect.topics.booking-completed:healthconnect.booking.completed}",
            "${healthconnect.topics.notification-raised:healthconnect.notification.raised}",
        },
        // See the class comment: unique per instance, on purpose, and the inverse of what every
        // other consumer in this estate wants.
        groupId = "${healthconnect.sse.group-id:healthconnect-gateway-sse-${random.uuid}}",
        autoStartup = "${healthconnect.kafka.consumer-enabled:true}"
    )
    public void onEstateEvent(String message) {
        try {
            JsonNode envelope = mapper.readTree(message);
            String type = envelope.path("type").asText();
            JsonNode payload = envelope.path("payload");

            for (String recipient : recipientsOf(payload)) {
                // tryEmitNext, never emitNext with FAIL_FAST: an emission with no subscribers is the
                // normal case here — usually nobody is watching — and it must not throw on a Kafka
                // listener thread, where the exception would be retried as though the event itself
                // were unhandled.
                Sinks.EmitResult result = sink.tryEmitNext(new UserEvent(recipient, type, envelope.path("aggregateRef").asText(null), payload));
                if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                    LOG.debug("dropped {} for {}: {}", type, recipient, result);
                }
            }
        } catch (Exception e) {
            // Swallowed, and this is the one consumer in the estate where that is right. Rethrowing
            // makes the container retry, and a malformed event would then be replayed for ever
            // against a channel whose entire output is a toast somebody may not even be looking at.
            // Nothing here is a system of record.
            LOG.warn("could not fan out an estate event: {}", e.getMessage());
        }
    }

    /**
     * Who this event concerns. Both sides of a booking where both are affected — a cancellation is
     * news to the customer and to the professional — and the addressee alone where only one is.
     *
     * <p>A {@code LinkedHashSet} because a payload can name the same login twice (a professional
     * booking with themselves is not prevented anywhere), and one event should not arrive twice.
     */
    private static Set<String> recipientsOf(JsonNode payload) {
        Set<String> recipients = new LinkedHashSet<>();
        add(recipients, payload.path("customerLogin").asText(null));
        add(recipients, payload.path("professionalLogin").asText(null));
        // notification.raised is the generic fan-in and names its addressee directly.
        add(recipients, payload.path("recipientLogin").asText(null));
        return recipients;
    }

    private static void add(Set<String> recipients, String login) {
        if (login != null && !login.isBlank()) {
            recipients.add(login);
        }
    }

    /**
     * One broadcast item, addressed.
     *
     * @param recipientLogin who may see it — the resource filters on this and nothing else, so a
     *     wrong value here is a disclosure rather than a missing message
     */
    public record UserEvent(String recipientLogin, String type, String aggregateRef, JsonNode payload) {}
}
