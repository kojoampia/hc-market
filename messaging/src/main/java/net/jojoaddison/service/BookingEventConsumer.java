package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.domain.ProcessedEvent;
import net.jojoaddison.repository.MessagingQueryRepository;
import net.jojoaddison.repository.NotificationRepository;
import net.jojoaddison.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns booking domain events into notification rows, and opens a thread when a booking is first
 * requested — spec §7's consumer column.
 *
 * <h2>Why this is in {@code service} and not {@code broker}</h2>
 *
 * <p>{@code TechnicalStructureTest} enforces a layered architecture in which {@code domain} may only
 * be reached from {@code repository}, {@code service}, {@code security}, {@code web} or
 * {@code config}, and {@code service} may only be reached from {@code web} or {@code config}. A
 * listener in {@code broker} can therefore touch neither the entities nor a service that does — it
 * would have to be a transport shim that forwards to nothing, which is what the generated
 * {@code broker.KafkaConsumer} is.
 *
 * <p>Reacting to a domain event IS application logic, so it belongs in the service layer. Nothing
 * calls this class — Spring invokes it reflectively — so it introduces no inbound dependency of its
 * own. Putting it in {@code broker} cost 51 architecture violations and taught this the hard way.
 *
 * <h2>Idempotency, and why it matters more here than in payout</h2>
 *
 * <p>Outbox delivery is at-least-once. Payout has a second line of defence in
 * {@code Ledger.bookingReference} being unique; a notification has <strong>no natural unique
 * key</strong>, because two genuine notifications can legitimately look identical. So
 * {@link ProcessedEvent} is the only thing standing between a redelivery and the same message
 * appearing twice in someone's bell menu — and a duplicate notification is the kind of defect
 * nobody reports and everybody notices.
 */
@Component
public class BookingEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final NotificationRepository notifications;
    private final MessagingQueryRepository conversations;
    private final ProcessedEventRepository processed;
    private final ObjectMapper mapper;

    public BookingEventConsumer(
        NotificationRepository notifications,
        MessagingQueryRepository conversations,
        ProcessedEventRepository processed,
        ObjectMapper mapper
    ) {
        this.notifications = notifications;
        this.conversations = conversations;
        this.processed = processed;
        this.mapper = mapper;
    }

    @KafkaListener(
        topics = {
            "healthconnect.booking.requested",
            "healthconnect.booking.accepted",
            "healthconnect.booking.declined",
            "healthconnect.booking.cancelled",
            "healthconnect.booking.completed",
            "healthconnect.notification.raised",
        },
        groupId = "healthconnect-messaging",
        autoStartup = "${healthconnect.kafka.consumer-enabled:true}"
    )
    @Transactional
    public void onBookingEvent(String message) {
        try {
            JsonNode envelope = mapper.readTree(message);
            String eventId = envelope.path("eventId").asText();
            String type = envelope.path("type").asText();
            JsonNode p = envelope.path("payload");

            if (eventId.isBlank() || processed.existsById(eventId)) {
                LOG.debug("skipping already-processed event {}", eventId);
                return;
            }

            switch (type) {
                case "healthconnect.booking.requested" -> {
                    // The professional is the one who needs to act, so they are the recipient.
                    raise(p.path("professionalLogin").asText(), "Booking requested",
                        "%s asked for %s on %s at %s.".formatted(name(p), service(p), p.path("scheduledDate").asText(), p.path("scheduledTime").asText()),
                        p.path("bookingRef").asText());
                    openThreadIfNone(p);
                }
                case "healthconnect.booking.accepted" -> raise(p.path("customerLogin").asText(), "Booking confirmed",
                    "Your %s on %s at %s is confirmed.".formatted(service(p), p.path("scheduledDate").asText(), p.path("scheduledTime").asText()),
                    p.path("bookingRef").asText());
                case "healthconnect.booking.declined" -> raise(p.path("customerLogin").asText(), "Booking declined",
                    "Your request for %s on %s could not be taken.".formatted(service(p), p.path("scheduledDate").asText()),
                    p.path("bookingRef").asText());
                case "healthconnect.booking.cancelled" -> {
                    raise(p.path("customerLogin").asText(), "Booking cancelled",
                        "Your %s on %s was cancelled.".formatted(service(p), p.path("scheduledDate").asText()), p.path("bookingRef").asText());
                    raise(p.path("professionalLogin").asText(), "Booking cancelled",
                        "%s cancelled %s on %s.".formatted(name(p), service(p), p.path("scheduledDate").asText()), p.path("bookingRef").asText());
                }
                case "healthconnect.booking.completed" -> raise(p.path("customerLogin").asText(), "Review requested",
                    "How was your %s? Leave a review.".formatted(service(p)), p.path("bookingRef").asText());
                default -> LOG.debug("no notification defined for {}", type);
            }

            processed.save(new ProcessedEvent(eventId, type, Instant.now()));
        } catch (Exception e) {
            // Rethrown so the container retries rather than acknowledging an unhandled message.
            throw new IllegalStateException("could not handle booking event: " + e.getMessage(), e);
        }
    }

    /** Spec §7: booking.requested should "open a thread if none exists". */
    private void openThreadIfNone(JsonNode p) {
        String customerLogin = p.path("customerLogin").asText();
        String professionalRef = p.path("professionalRef").asText();
        boolean exists = conversations
            .findVisibleTo(customerLogin, professionalRef)
            .stream()
            .anyMatch(c -> customerLogin.equals(c.getCustomerLogin()) && professionalRef.equals(c.getProfessionalRef()));
        if (exists) {
            return;
        }
        conversations.save(
            new Conversation()
                .reference("t-" + p.path("bookingRef").asText())
                .customerLogin(customerLogin)
                .professionalRef(professionalRef)
                .bookingReference(p.path("bookingRef").asText())
                .lastMessageAt(Instant.now())
        );
    }

    private void raise(String recipient, String kind, String body, String bookingRef) {
        if (recipient == null || recipient.isBlank()) {
            LOG.warn("no recipient for a {} notification about {}", kind, bookingRef);
            return;
        }
        notifications.save(
            new Notification()
                .recipientLogin(recipient)
                .kind(kind)
                .body(body)
                .raisedAt(Instant.now())
                .deepLink("/bookings/" + bookingRef)
        );
    }

    private static String name(JsonNode p) {
        String n = p.path("customerName").asText("");
        return n.isBlank() ? p.path("customerLogin").asText("Someone") : n;
    }

    private static String service(JsonNode p) {
        String s = p.path("serviceName").asText("");
        return s.isBlank() ? "your session" : s;
    }
}
