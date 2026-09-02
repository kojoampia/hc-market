package net.jojoaddison.web.rest;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.domain.enumeration.Direction;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.MessagingQueryRepository;
import net.jojoaddison.repository.NotificationRepository;
import net.jojoaddison.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Messages and the bell menu — spec §6.
 *
 * <p>The REST paths are {@code /api/threads} even though the entity is {@code Conversation}: the
 * spec, the prototype and every screen call them threads, and the rename exists only to stop a
 * JHipster entity shadowing {@code java.lang.Thread} inside its own package. The generated
 * {@code ConversationResource} keeps {@code /api/conversations} for CRUD and does not collide.
 *
 * <p>{@code NotificationResource} was deleted: it mapped {@code /api/notifications}, which spec §6
 * needs for the recipient-scoped list, and two controllers on one path stop the app booting.
 */
@RestController
public class MessagingResource {

    private final MessagingQueryRepository messaging;
    private final MessageRepository messages;
    private final NotificationRepository notifications;

    public MessagingResource(MessagingQueryRepository messaging, MessageRepository messages, NotificationRepository notifications) {
        this.messaging = messaging;
        this.messages = messages;
        this.notifications = notifications;
    }

    public record ThreadView(String reference, String customerLogin, String professionalRef, String bookingReference, Instant lastMessageAt, long unread) {}

    public record MessageView(String direction, String body, Instant sentAt, Instant readAt) {}

    public record ThreadDetail(ThreadView thread, List<MessageView> messages) {}

    public record SendMessage(String body) {}

    public record NotificationView(String kind, String body, Instant raisedAt, Instant readAt, String deepLink) {}

    /**
     * The threads this caller can see.
     *
     * <p>A caller may be a customer or a professional, and the same token cannot tell us which
     * without asking catalog. So both are tried: threads where the login is the customer, plus
     * threads whose professional ref matches. A customer's login never equals a professional ref,
     * so the two sets cannot overlap by accident.
     */
    @GetMapping("/api/threads")
    public List<ThreadView> threads(@RequestParam(required = false) String professionalRef) {
        String login = currentLogin();
        return messaging.findVisibleTo(login, professionalRef == null ? "" : professionalRef).stream().map(this::toView).toList();
    }

    @GetMapping("/api/threads/{ref}")
    public ThreadDetail thread(@PathVariable String ref, @RequestParam(required = false) String professionalRef) {
        Conversation c = visibleOr404(ref, professionalRef);
        return new ThreadDetail(
            toView(c),
            messaging.findMessages(c.getId()).stream()
                .map(m -> new MessageView(m.getDirection() == null ? null : m.getDirection().name(), m.getBody(), m.getSentAt(), m.getReadAt()))
                .toList()
        );
    }

    @PostMapping("/api/threads/{ref}/messages")
    @Transactional
    public ResponseEntity<MessageView> send(
        @PathVariable String ref,
        @RequestBody SendMessage body,
        @RequestParam(required = false) String professionalRef
    ) {
        if (body == null || body.body() == null || body.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a message body is required");
        }
        Conversation c = visibleOr404(ref, professionalRef);
        Instant now = Instant.now();
        // Direction is inferred from who is sending, not taken from the request — a client that
        // could set its own direction could forge a message from the other party.
        Direction direction = currentLogin().equals(c.getCustomerLogin())
            ? Direction.CUSTOMER_TO_PROFESSIONAL
            : Direction.PROFESSIONAL_TO_CUSTOMER;

        Message saved = messages.save(new Message().direction(direction).body(body.body()).sentAt(now).conversation(c));
        c.setLastMessageAt(now);
        messaging.save(c);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new MessageView(saved.getDirection().name(), saved.getBody(), saved.getSentAt(), saved.getReadAt()));
    }

    @GetMapping("/api/notifications")
    public List<NotificationView> notifications() {
        return messaging.findNotifications(currentLogin()).stream()
            .map(n -> new NotificationView(n.getKind(), n.getBody(), n.getRaisedAt(), n.getReadAt(), n.getDeepLink()))
            .toList();
    }

    /**
     * Marks everything read. The bell menu has no per-item control, so neither does this.
     *
     * <p><strong>Notification rows are append-only — {@code decisions.md} D36.</strong> A "clear
     * notifications" button is an ordinary feature and the obvious way to build it is a delete; here
     * it must set {@code readAt}, as this endpoint does, and delete nothing. Erasure finds the
     * notifications that name an erased customer in <em>somebody else's</em> bell menu by unioning
     * their conversations' booking references with the deep links of the customer's <em>own</em>
     * notifications, because the two copies of one booking event share a link. Delete the customer's
     * copies and that bridge goes with them: the professional keeps a row naming a person who has
     * been erased, the receipt still reports a clean erasure with plausible counts, and nothing is
     * red — the exact defect D36 fixed, arriving from a feature nobody would think to connect to it.
     */
    @PostMapping("/api/notifications/read")
    @Transactional
    public ResponseEntity<Void> markRead() {
        Instant now = Instant.now();
        List<Notification> unread = messaging.findNotifications(currentLogin()).stream().filter(n -> n.getReadAt() == null).toList();
        unread.forEach(n -> n.setReadAt(now));
        notifications.saveAll(unread);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------- helpers --

    private ThreadView toView(Conversation c) {
        long unread = messaging.findMessages(c.getId()).stream().filter(m -> m.getReadAt() == null).count();
        return new ThreadView(c.getReference(), c.getCustomerLogin(), c.getProfessionalRef(), c.getBookingReference(), c.getLastMessageAt(), unread);
    }

    /** 404 rather than 403, so a thread reference cannot be probed for existence. */
    private Conversation visibleOr404(String ref, String professionalRef) {
        String login = currentLogin();
        return messaging
            .findByReference(ref)
            .filter(c -> login.equals(c.getCustomerLogin()) || c.getProfessionalRef().equals(professionalRef))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such thread"));
    }

    private String currentLogin() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated"));
    }
}
