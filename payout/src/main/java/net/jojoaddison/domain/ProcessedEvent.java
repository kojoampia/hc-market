package net.jojoaddison.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * An event this service has already handled.
 *
 * <p>Outbox delivery is at-least-once: the publisher can send and then die before marking the row
 * sent, so consumers see repeats. This table is how "idempotent on eventId" is actually implemented
 * — the primary key does the work, so a redelivered event fails to insert and is skipped rather
 * than double-crediting a professional.
 *
 * <p>Ledger already has a unique {@code bookingReference}, which would catch a duplicate
 * {@code booking.completed} on its own. This table is still worth having: it catches duplicates of
 * events that do <em>not</em> write a uniquely-keyed row, and it makes the intent explicit rather
 * than relying on a constraint somewhere else to mean something it was not written to mean.
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String type, Instant processedAt) {
        this.eventId = eventId;
        this.type = type;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
