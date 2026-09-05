package net.jojoaddison.service.seed;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.domain.enumeration.Direction;
import net.jojoaddison.repository.ConversationRepository;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the messaging service's slice of the seed. */
@Service
public class MessagingSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(MessagingSeeder.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final NotificationRepository notifications;

    public MessagingSeeder(ConversationRepository conversations, MessageRepository messages, NotificationRepository notifications) {
        this.conversations = conversations;
        this.messages = messages;
        this.notifications = notifications;
    }

    public boolean alreadySeeded() {
        return conversations.count() > 0 || notifications.count() > 0;
    }

    @Transactional
    public void clear() {
        messages.deleteAllInBatch();
        conversations.deleteAllInBatch();
        notifications.deleteAllInBatch();
    }

    @Transactional
    public void load(SeedFile seed, boolean anchorDates) {
        // decisions.md D48. Not LocalDate.now(): the calendar is the estate's, and the four seeded
        // services have to arrive at the same number or their dates stop lining up with each other.
        long shift = SeedCalendar.shiftDays(seed.meta().demoToday(), anchorDates);
        if (shift != 0) {
            // The day is DERIVED from the shift rather than read from the clock a second time: two
            // reads either side of Accra midnight would print a date the seed was not loaded against,
            // in the one log line whose job is to explain a disagreement between services.
            LOG.info(
                "shifting every seed date by {} days: {} -> {} in {}",
                shift,
                seed.meta().demoToday(),
                seed.meta().demoToday().plusDays(shift),
                SeedCalendar.SEED_ZONE
            );
        }

        for (SeedFile.SeedThread t : orEmpty(seed.threads())) {
            List<SeedFile.SeedMessage> ordered = orEmpty(t.messages())
                .stream()
                .sorted(Comparator.comparing(SeedFile.SeedMessage::seq, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

            Conversation conversation = conversations.save(
                new Conversation()
                    .reference(t.ref())
                    .customerLogin(t.customerLogin())
                    .professionalRef(t.professionalRef())
                    .bookingReference(t.bookingReference())
                    // Derived from the messages rather than stored separately in the seed: a
                    // lastMessageAt that disagrees with the newest message is exactly the kind of
                    // drift this project keeps designing out.
                    .lastMessageAt(ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).sentAt().plus(shift, ChronoUnit.DAYS))
            );

            for (SeedFile.SeedMessage m : ordered) {
                messages.save(
                    new Message()
                        .direction(Direction.valueOf(m.direction()))
                        .body(m.body())
                        .sentAt(m.sentAt().plus(shift, ChronoUnit.DAYS))
                        // An unread message has no readAt; the seed's `read` flag is the source.
                        .readAt(Boolean.TRUE.equals(m.read()) ? m.sentAt().plus(shift, ChronoUnit.DAYS) : null)
                        .conversation(conversation)
                );
            }
        }

        for (SeedFile.SeedNotification n : orEmpty(seed.notifications())) {
            notifications.save(
                new Notification()
                    .recipientLogin(n.recipientLogin())
                    .kind(n.kind())
                    .body(n.body())
                    .raisedAt(n.raisedOn().plusDays(shift).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC))
                    .readAt(Boolean.TRUE.equals(n.read()) ? n.raisedOn().plusDays(shift).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC) : null)
            );
        }

        LOG.info("seeded {} conversations and {} notifications", orEmpty(seed.threads()).size(), orEmpty(seed.notifications()).size());
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
