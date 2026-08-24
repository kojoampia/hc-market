package net.jojoaddison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records domain events into the outbox.
 *
 * <p>{@link Propagation#MANDATORY} is the load-bearing annotation here. It refuses to run outside an
 * existing transaction, which means an event can only ever be recorded as part of the same
 * transaction that changed the booking. Without it this class would still <em>look</em> correct
 * while quietly opening its own transaction and committing the event independently — reintroducing
 * exactly the dual-write problem the outbox exists to remove, and doing it invisibly.
 */
@Service
public class OutboxRecorder {

    /** Spec §7's topic names, under the {@code healthconnect.} prefix. */
    public static final String TOPIC_PREFIX = "healthconnect.";

    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public OutboxRecorder(OutboxEventRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /**
     * Records one event. Must be called inside the transaction that made the change it describes.
     *
     * @param shortType the event name without the prefix, e.g. {@code booking.accepted}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(String shortType, Booking booking, String actor) {
        String type = TOPIC_PREFIX + shortType;
        return outbox.save(
            new OutboxEvent()
                .eventId(UUID.randomUUID().toString())
                .type(type)
                .topic(type)
                // Keyed by booking reference so every event for one booking lands on one partition
                // and their order survives. Accepted-then-cancelled must not arrive reversed.
                .aggregateRef(booking.getReference())
                .actor(actor)
                .occurredAt(Instant.now())
                .payload(payload(booking))
        );
    }

    /**
     * The event payload.
     *
     * <p>Carries what a consumer needs to act without calling back — the notification messaging
     * writes needs the names and the time, and a callback would make the consumer depend on booking
     * being up at exactly the moment it is catching up after booking was down.
     */
    private String payload(Booking b) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingRef", b.getReference());
        payload.put("professionalRef", b.getProfessionalRef());
        payload.put("professionalLogin", b.getProfessionalLogin());
        payload.put("customerLogin", b.getCustomerLogin());
        payload.put("customerName", b.getCustomerName());
        payload.put("serviceRef", b.getServiceRef());
        payload.put("serviceName", b.getServiceName());
        payload.put("scheduledDate", String.valueOf(b.getScheduledDate()));
        payload.put("scheduledTime", b.getScheduledTime());
        payload.put("deliveryMode", b.getDeliveryMode() == null ? null : b.getDeliveryMode().name());
        payload.put("status", b.getStatus() == null ? null : b.getStatus().name());
        payload.put("priceMinor", b.getPriceMinor());
        payload.put("currency", b.getCurrency());
        payload.put("lateCancellation", b.getLateCancellation());
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Nothing in that map is unserialisable, so this is a programming error rather than a
            // runtime condition — failing loudly beats writing a half-built event.
            throw new IllegalStateException("could not serialise the outbox payload for " + b.getReference(), e);
        }
    }
}
