package net.jojoaddison.service;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the outbox to Kafka.
 *
 * <p>Runs on a timer rather than reacting to the write, so recovery needs no special path: an event
 * stranded by a broker outage or a crash is picked up by the next tick, and "catching up after
 * Kafka was down" is the same code as "publishing normally".
 *
 * <h2>Publish, then mark — never the reverse</h2>
 *
 * <p>Marking first would lose events whenever the publish failed. Publishing first means a crash
 * between the two republishes the event later, so delivery is <strong>at-least-once</strong> and
 * every consumer must be idempotent on {@code eventId}. That is a real constraint on consumers, and
 * it is the honest one: exactly-once is not available across a database and a broker.
 */
@Component
@ConditionalOnProperty(name = "healthconnect.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH = 50;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(
        OutboxEventRepository outbox,
        // By name: the autoconfigured template is Spring Cloud Stream's byte-array producer, and
        // injecting it here fails at publish time rather than at startup.
        @org.springframework.beans.factory.annotation.Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafka
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${healthconnect.outbox.poll-ms:2000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> pending = outbox.findUnsent(PageRequest.of(0, BATCH));
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            try {
                // Synchronous: the send must be known to have succeeded before the row is marked,
                // otherwise "publish then mark" degenerates into "mark and hope".
                kafka.send(event.getTopic(), event.getAggregateRef(), envelope(event)).get();
                event.setSentAt(Instant.now());
                event.setLastError(null);
                LOG.debug("published {} for {}", event.getType(), event.getAggregateRef());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Left unsent on purpose. The next tick retries; nothing is lost.
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.getMessage()));
                LOG.warn("could not publish {} for {} (attempt {}): {}", event.getType(), event.getAggregateRef(), event.getAttempts(), e.getMessage());
            }
        }
        outbox.saveAll(pending);
    }

    /** Spec §7's envelope. The payload is already JSON, so it is spliced in rather than re-encoded. */
    private static String envelope(OutboxEvent e) {
        return """
            {"eventId":"%s","type":"%s","occurredAt":"%s","aggregateRef":"%s","actor":%s,"payload":%s}"""
            .formatted(
                e.getEventId(),
                e.getType(),
                e.getOccurredAt(),
                e.getAggregateRef(),
                e.getActor() == null ? "null" : "\"" + e.getActor() + "\"",
                e.getPayload()
            );
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
