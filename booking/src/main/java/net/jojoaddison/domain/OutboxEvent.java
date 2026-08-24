package net.jojoaddison.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * A domain event waiting to be published — the transactional outbox of spec §7.
 *
 * <h2>Why this table exists</h2>
 *
 * <p>Writing the booking and publishing to Kafka are two different systems, and there is no
 * transaction spanning both. Publish first and the broker may accept an event for a booking that
 * then fails to save. Save first and the process may die before publishing, losing the event with
 * nothing anywhere recording that it was owed.
 *
 * <p>So the service writes the booking <em>and</em> a row here in one database transaction — both
 * or neither — and a poller publishes afterwards. A crash between the two leaves an unsent row,
 * which is a to-do list, not a loss. This is the failure mode a prototype never has to think about
 * and a marketplace cannot afford: a booking accepted while the professional is never told.
 *
 * <h2>Delivery is at-least-once</h2>
 *
 * <p>The poller can publish and then die before marking the row sent, so consumers see repeats.
 * That is why {@link #eventId} exists and why every consumer is idempotent on it. Exactly-once
 * delivery is not available here and pretending otherwise would be worse than admitting it.
 *
 * <p>Not generated from JDL: the outbox is infrastructure, not part of the domain model, and
 * putting it in the JDL would give it REST endpoints nobody should call.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    /** The consumer's idempotency key. Stable across every redelivery of this event. */
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    /** Fully qualified: {@code healthconnect.booking.accepted}. */
    @Column(name = "type", nullable = false)
    private String type;

    /** The topic to publish on. Kept separate from {@code type} so one can change without the other. */
    @Column(name = "topic", nullable = false)
    private String topic;

    /**
     * The Kafka partition key — the booking reference, so every event for one booking lands on one
     * partition and their order is preserved. Accepted-then-cancelled must never arrive reversed.
     */
    @Column(name = "aggregate_ref", nullable = false)
    private String aggregateRef;

    @Column(name = "actor")
    private String actor;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Null until published. The poller's entire work queue is {@code where sent_at is null}. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** Counts publish failures, so a permanently poisoned row can be spotted rather than retried forever. */
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error")
    private String lastError;

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public OutboxEvent eventId(String eventId) {
        this.eventId = eventId;
        return this;
    }

    public String getType() {
        return type;
    }

    public OutboxEvent type(String type) {
        this.type = type;
        return this;
    }

    public String getTopic() {
        return topic;
    }

    public OutboxEvent topic(String topic) {
        this.topic = topic;
        return this;
    }

    public String getAggregateRef() {
        return aggregateRef;
    }

    public OutboxEvent aggregateRef(String aggregateRef) {
        this.aggregateRef = aggregateRef;
        return this;
    }

    public String getActor() {
        return actor;
    }

    public OutboxEvent actor(String actor) {
        this.actor = actor;
        return this;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEvent payload(String payload) {
        this.payload = payload;
        return this;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public OutboxEvent occurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
        return this;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Integer getAttempts() {
        return attempts == null ? 0 : attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public String toString() {
        return "OutboxEvent{eventId='" + eventId + "', type='" + type + "', sent=" + (sentAt != null) + "}";
    }
}
