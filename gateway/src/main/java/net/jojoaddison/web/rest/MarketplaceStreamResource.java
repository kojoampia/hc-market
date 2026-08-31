package net.jojoaddison.web.rest;

import java.time.Duration;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.MarketplaceEventFanout;
import net.jojoaddison.service.MarketplaceEventFanout.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * The live channel the spec header has advertised since the beginning — {@code decisions.md}
 * D25/D29. "Kafka, SSE" was written on §1 and no SSE endpoint existed; this is it.
 *
 * <h2>One stream, filtered to the caller</h2>
 *
 * <p>{@code /api/stream} is {@code authenticated()} by the gateway's generated security rules, and
 * the login comes from the JWT subject and nowhere else — there is no {@code ?login=} and there must
 * never be one, for the same reason {@code /api/pro/**} takes no professional parameter.
 *
 * <p>The filtering itself is {@link MarketplaceEventFanout#streamFor}, not a predicate here.
 * Addressing is that class's concern from end to end — it decides who an event is <em>for</em>, so it
 * also decides who may see it — and keeping them together means the boundary between one customer
 * and everybody else's bookings is one file, testable against a real broker with no HTTP client in
 * the way.
 *
 * <h2>Heartbeats are not optional here</h2>
 *
 * <p>A marketplace stream is idle most of the time, and every hop between a browser and this endpoint
 * will close a connection it believes has stalled — nginx's {@code proxy_read_timeout} defaults to 60
 * seconds, and the quality box puts <em>two</em> nginx hops in front of this. Without a keep-alive the
 * failure is not an error anybody sees: the browser reconnects, the reconnect succeeds, and the only
 * symptom is a stream that mysteriously misses whatever happened during the gap.
 *
 * <p>So a comment frame goes out every 20 seconds. Comments are part of the SSE wire format and
 * {@code EventSource} discards them, so this costs the client nothing and keeps every proxy in the
 * path convinced the connection is alive.
 *
 * <h2>What this is not</h2>
 *
 * <p>Not durable and not a record. Events are dropped for a subscriber that cannot keep up and are
 * not replayed on reconnect — see {@code MarketplaceEventFanout}. The durable copy is messaging's
 * notification table, which is what a client reads on connect and after any gap. Treating this as
 * the source of truth would be reading a toast as an accounting entry.
 *
 * <h2>A new file</h2>
 *
 * <p>Regeneration leaves it alone, unlike the generated {@code HealthconnectGatewayKafkaResource}
 * beside it — which is a sample, serves every event to every subscriber, and is not SSE. The two are
 * easy to confuse by name, which is the reason this comment says so.
 */
@RestController
@RequestMapping("/api")
public class MarketplaceStreamResource {

    private static final Logger LOG = LoggerFactory.getLogger(MarketplaceStreamResource.class);

    /** Comfortably inside nginx's 60s default, and inside the two hops the quality box adds. */
    private static final Duration HEARTBEAT = Duration.ofSeconds(20);

    private final MarketplaceEventFanout fanout;

    public MarketplaceStreamResource(MarketplaceEventFanout fanout) {
        this.fanout = fanout;
    }

    /**
     * Everything happening to the authenticated caller, as it happens.
     *
     * <p>The stream never completes on its own; it ends when the client disconnects.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream() {
        // Mono, not Optional: the gateway is reactive and its SecurityUtils reads the context
        // asynchronously. flatMapMany also gives the right answer for free when there is no
        // authenticated subject — an empty Mono becomes an empty stream, where the alternative
        // would have been emitting everything.
        return SecurityUtils.getCurrentUserLogin().flatMapMany(this::streamFor);
    }

    private Flux<ServerSentEvent<Object>> streamFor(String login) {
        LOG.debug("opening event stream for {}", login);
        Flux<ServerSentEvent<Object>> events = fanout.streamFor(login).map(MarketplaceStreamResource::toSse);

        // merge, not concat or zip: the heartbeat is an independent clock and must keep ticking
        // through long silences and through bursts alike.
        Flux<ServerSentEvent<Object>> heartbeats = Flux.interval(HEARTBEAT, HEARTBEAT).map(tick ->
            ServerSentEvent.builder().comment("keep-alive").build()
        );

        return Flux.merge(events, heartbeats).doFinally(signal -> LOG.debug("closing event stream for {} ({})", login, signal));
    }

    /**
     * The event name is the domain event type, so a client can {@code addEventListener} per kind
     * rather than switching on a field. It is the unprefixed type from the envelope — the estate
     * prefix (D29) names a Kafka topic and is transport, which no client should ever see.
     */
    private static ServerSentEvent<Object> toSse(UserEvent event) {
        return ServerSentEvent.builder()
            .event(event.type())
            // The aggregate reference, not a monotonic cursor. SSE reconnection replays from
            // Last-Event-ID and this stream has nothing to replay from, so it is an identifier for
            // the client's benefit rather than a promise this endpoint cannot keep.
            .id(event.aggregateRef())
            .data(event.payload())
            .build();
    }
}
