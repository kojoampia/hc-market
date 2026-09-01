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
    private final ErasureWorkflow erasure;

    public BookingEventConsumer(
        NotificationRepository notifications,
        MessagingQueryRepository conversations,
        ProcessedEventRepository processed,
        ObjectMapper mapper,
        ErasureWorkflow erasure
    ) {
        this.notifications = notifications;
        this.conversations = conversations;
        this.processed = processed;
        this.mapper = mapper;
        this.erasure = erasure;
    }

    /**
     * Resolves the login an event carries into the one this service may store — {@code decisions.md} D32.
     *
     * <p>Returns the pseudonym when the person has been erased, and the login otherwise. Every write
     * keyed to a person goes through here, because erasure is a standing fact and an event in flight
     * does not know about it: a lagging {@code booking.requested} re-created a conversation under a
     * login that had been erased seconds earlier, against a receipt already filed saying it was gone.
     *
     * <p>The row is still written. Skipping it would leave the professional's thread list and bell
     * menu with holes where a real booking was, to protect an identifier that can simply be replaced.
     */
    private String storable(String login) {
        if (login == null || login.isBlank()) {
            return login;
        }
        /* Taken before the question is asked, and held for the rest of this transaction. Without it,
           an erasure running concurrently commits its register row after this read and this event
           writes the original login anyway — the very failure D32 was written to close. See
           SubjectLockRepository. */
        erasure.lockSubject(login);
        return erasure.isErased(login) ? ErasureWorkflow.pseudonym(login) : login;
    }

    /** The customer's display name, or nothing anyone can be identified by once they are erased. */
    private String storableName(JsonNode p) {
        String login = p.path("customerLogin").asText();
        if (login == null || login.isBlank()) {
            return name(p);
        }
        // Same lock as storable(), for the same reason. Advisory locks are counted per transaction,
        // so taking it again here is a no-op rather than a second wait.
        erasure.lockSubject(login);
        return erasure.isErased(login) ? "A customer" : name(p);
    }

    @KafkaListener(
        // Property-driven, not literal — decisions.md D29. The prefix is empty in production and
        // quality and `dev.` in the dev estate, which is what stops two stacks on the one shared
        // broker consuming each other's events. The switch below is untouched: it matches the
        // envelope's event TYPE, which is never prefixed, rather than the topic it arrived by.
        // Each carries its canonical name as an INLINE DEFAULT, and that is not belt-and-braces.
        // JHipster's src/test/resources/config/application.yml SHADOWS the main one, so the
        // composed properties do not exist under test and a bare placeholder fails the context with
        // "Could not resolve placeholder" — which reads as a typo rather than as a config file that
        // was never loaded. The default also keeps the real topic name visible at the listener, and
        // survives a regeneration of application.yml.
        topics = {
            "${healthconnect.topics.booking-requested:healthconnect.booking.requested}",
            "${healthconnect.topics.booking-accepted:healthconnect.booking.accepted}",
            "${healthconnect.topics.booking-declined:healthconnect.booking.declined}",
            "${healthconnect.topics.booking-cancelled:healthconnect.booking.cancelled}",
            "${healthconnect.topics.booking-completed:healthconnect.booking.completed}",
            "${healthconnect.topics.notification-raised:healthconnect.notification.raised}",
        },
        groupId = "${healthconnect.kafka.group-id:healthconnect-messaging}",
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
                        "%s asked for %s on %s at %s.".formatted(storableName(p), service(p), p.path("scheduledDate").asText(), p.path("scheduledTime").asText()),
                        p.path("bookingRef").asText());
                    openThreadIfNone(p);
                }
                case "healthconnect.booking.accepted" -> raise(storable(p.path("customerLogin").asText()), "Booking confirmed",
                    "Your %s on %s at %s is confirmed.".formatted(service(p), p.path("scheduledDate").asText(), p.path("scheduledTime").asText()),
                    p.path("bookingRef").asText());
                case "healthconnect.booking.declined" -> raise(storable(p.path("customerLogin").asText()), "Booking declined",
                    "Your request for %s on %s could not be taken.".formatted(service(p), p.path("scheduledDate").asText()),
                    p.path("bookingRef").asText());
                case "healthconnect.booking.cancelled" -> {
                    raise(storable(p.path("customerLogin").asText()), "Booking cancelled",
                        "Your %s on %s was cancelled.".formatted(service(p), p.path("scheduledDate").asText()), p.path("bookingRef").asText());
                    raise(p.path("professionalLogin").asText(), "Booking cancelled",
                        "%s cancelled %s on %s.".formatted(storableName(p), service(p), p.path("scheduledDate").asText()), p.path("bookingRef").asText());
                }
                case "healthconnect.booking.completed" -> raise(storable(p.path("customerLogin").asText()), "Review requested",
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
        // storable, not the raw login: this is the write that reappeared under an erased login.
        String customerLogin = storable(p.path("customerLogin").asText());
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
