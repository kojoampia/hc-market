package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
     * Resolves the login an event carries into the one this service may store — {@code decisions.md}
     * D32 and D37.
     *
     * <p>Returns the pseudonym when the erasure covers this booking, and the login otherwise. Every
     * write keyed to a person goes through here, because erasure is a standing fact and an event in
     * flight does not know about it: a lagging {@code booking.requested} re-created a conversation
     * under a login that had been erased seconds earlier, against a receipt already filed saying it
     * was gone.
     *
     * <p><strong>The register is scoped to the booking's age, not applied to the person for ever
     * — D37.</strong> An erased customer is not locked out and may book again; that new booking is
     * stored under their real login, while everything raised before the erasure stays pseudonymised.
     * The instant that decides it is {@code bookingRaisedAt} from the payload, which is when the
     * <em>booking</em> was created — see {@link #bookingRaisedAt(JsonNode)} for why the event's own
     * timestamp is the wrong clock and what it would cost.
     *
     * <p>The row is still written. Skipping it would leave the professional's thread list and bell
     * menu with holes where a real booking was, to protect an identifier that can simply be replaced.
     */
    private String storable(JsonNode p, String login) {
        if (login == null || login.isBlank()) {
            return login;
        }
        /* Taken before the question is asked, and held for the rest of this transaction. Without it,
           an erasure running concurrently commits its register row after this read and this event
           writes the original login anyway — the very failure D32 was written to close. See
           SubjectLockRepository. */
        erasure.lockSubject(login);
        return erasure.covers(login, bookingRaisedAt(p)) ? erasure.pseudonym(login) : login;
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
        // Same scoping as storable(), and it has to be the same or the two disagree: a returning
        // customer's thread would carry their login while the professional's bell menu said
        // "A customer", or the reverse.
        return erasure.covers(login, bookingRaisedAt(p)) ? "A customer" : name(p);
    }

    /**
     * When the booking this event is about was created — {@code decisions.md} D37, backlog WP-08.
     *
     * <p>{@code bookingRaisedAt} is {@code Booking.raisedAt} as booking's {@code OutboxRecorder} puts
     * it on the payload: a property of the booking, written once at creation and never moved by a
     * transition, so every event about one booking reports the same instant however late in the
     * lifecycle it is published. That is what makes it a truthful answer to "did this booking exist
     * before the erasure", which is the only question {@link ErasureWorkflow#covers} asks.
     *
     * <p><strong>Not the envelope's {@code occurredAt}.</strong> A booking still open when an erasure
     * ran goes on emitting events afterwards, so under an event-timestamp rule its acceptance and its
     * completion would each write the customer's real login and name back onto a booking erased
     * seconds earlier — the erasure growing back one lifecycle step at a time, with nothing red.
     *
     * <p>Absent, blank or unparseable answers null, and {@code covers} treats null as covered. An
     * event published before this field existed is still an event about a booking that predates the
     * erasure being consulted, and a malformed value must not be the thing that decides an identifier
     * is safe to store.
     */
    private static Instant bookingRaisedAt(JsonNode p) {
        String raw = p.path("bookingRaisedAt").asText("");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException notAnInstant) {
            LOG.warn("bookingRaisedAt {} is not an instant — treating the booking as predating any erasure", raw);
            return null;
        }
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
                case "healthconnect.booking.accepted" -> raise(storable(p, p.path("customerLogin").asText()), "Booking confirmed",
                    "Your %s on %s at %s is confirmed.".formatted(service(p), p.path("scheduledDate").asText(), p.path("scheduledTime").asText()),
                    p.path("bookingRef").asText());
                case "healthconnect.booking.declined" -> raise(storable(p, p.path("customerLogin").asText()), "Booking declined",
                    "Your request for %s on %s could not be taken.".formatted(service(p), p.path("scheduledDate").asText()),
                    p.path("bookingRef").asText());
                case "healthconnect.booking.cancelled" -> {
                    raise(storable(p, p.path("customerLogin").asText()), "Booking cancelled",
                        "Your %s on %s was cancelled.".formatted(service(p), p.path("scheduledDate").asText()), p.path("bookingRef").asText());
                    raise(p.path("professionalLogin").asText(), "Booking cancelled",
                        "%s cancelled %s on %s.".formatted(storableName(p), service(p), p.path("scheduledDate").asText()), p.path("bookingRef").asText());
                }
                case "healthconnect.booking.completed" -> raise(storable(p, p.path("customerLogin").asText()), "Review requested",
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
        String customerLogin = storable(p, p.path("customerLogin").asText());
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

    /**
     * Writes one row into somebody's bell menu.
     *
     * <p><strong>Every notification this service raises carries {@code /bookings/<ref>}, and that is
     * an erasure invariant rather than a display convenience — {@code decisions.md} D24/D36.</strong>
     * A notification <em>about</em> a customer sits in a different person's bell menu, keyed to a
     * different person's login, so no query by recipient can ever return it; the deep link is the
     * only handle the erasure has on it. A row written without one is invisible to erasure for good,
     * and nothing anywhere goes red — the receipt reports a clean erasure with plausible counts,
     * which is precisely the shape of the defect D36 was written to fix.
     *
     * <p>So raise notifications through here and never by constructing a {@link Notification} inline.
     * The {@code default} branch of the switch above is going to grow cases — booking already
     * publishes {@code notification.raised} for reschedule proposals and no-shows, and this consumer
     * swallows them <em>and marks them processed</em> — and whoever adds those cases inherits this
     * rule only if the code they copy already obeys it.
     *
     * <p>A blank recipient and a blank booking reference are both logged and skipped. Skipping costs
     * one bell row; writing the row would cost a permanent hole in what erasure can reach, and a
     * blank reference is the worse of the two, because {@code "/bookings/" + ""} is the literal
     * {@code /bookings/} — non-null, non-blank, and therefore matching every other malformed row in
     * the table rather than one booking.
     */
    private void raise(String recipient, String kind, String body, String bookingRef) {
        if (recipient == null || recipient.isBlank()) {
            LOG.warn("no recipient for a {} notification about {}", kind, bookingRef);
            return;
        }
        if (bookingRef == null || bookingRef.isBlank()) {
            LOG.warn("no booking reference on a {} notification for {} — not raising it, since erasure could never find it", kind, recipient);
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
