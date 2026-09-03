package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.repository.ConversationRepository;
import net.jojoaddison.repository.ErasedSubjectRepository;
import net.jojoaddison.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the consumer writes, and whose name it writes it under — {@code decisions.md} D32.
 *
 * <p>There was no test here at all until this class, which is worth saying plainly: the consumer is
 * the only code in this service that stores a person's login without a person having just
 * authenticated, and it was the one place erasure did not reach. The gap and the missing coverage
 * were the same gap.
 *
 * <p>Calls {@code onBookingEvent} directly rather than going through a broker. What is under test is
 * the decision the consumer makes about an identifier; standing up Kafka to deliver the string would
 * test Spring's container instead.
 */
@IntegrationTest
class BookingEventConsumerIT {

    private static final String CUSTOMER = "yaa.tobeforgotten";
    private static final String PRO_LOGIN = "akosua.mensah";
    private static final String PRO_REF = "p1";

    /**
     * The alias derivation, injected rather than called statically — decisions.md D35. It is peppered
     * from src/test/resources/config/application.yml, and SubjectPseudonymUnitTest pins what it
     * produces; here it is used only so the assertions ask for the same string the service wrote.
     */
    @Autowired
    private SubjectPseudonym pseudonyms;

    @Autowired
    private BookingEventConsumer consumer;

    @Autowired
    private ErasureWorkflow erasure;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private NotificationRepository notifications;

    /** Read for its {@code erasedAt} — WP-08's assertions are all relative to that instant. */
    @Autowired
    private ErasedSubjectRepository register;

    /**
     * An event carrying no {@code bookingRaisedAt} — which is every event published before WP-08 added
     * the field, and which {@code covers} must therefore keep treating as predating any erasure.
     */
    private static String requested(String bookingRef, String customerLogin) {
        return event("healthconnect.booking.requested", bookingRef, customerLogin, null);
    }

    /**
     * @param bookingRaisedAt when the BOOKING was created, not when the event was published — the
     *     distinction WP-08 turns on. Null omits the field.
     */
    private static String event(String type, String bookingRef, String customerLogin, Instant bookingRaisedAt) {
        String raisedAt = bookingRaisedAt == null ? "" : "\"bookingRaisedAt\":\"%s\",".formatted(bookingRaisedAt);
        return
            """
            {"eventId":"%s","type":"%s","payload":{
              "bookingRef":"%s",%s"customerLogin":"%s","customerName":"Yaa Tobeforgotten",
              "professionalLogin":"%s","professionalRef":"%s","serviceName":"Home visit",
              "scheduledDate":"2026-09-12","scheduledTime":"10:00"}}
            """.formatted("evt-" + type + "-" + bookingRef, type, bookingRef, raisedAt, customerLogin, PRO_LOGIN, PRO_REF);
    }

    /** When this service recorded the erasure — the instant every WP-08 assertion is relative to. */
    private Instant erasedAt() {
        return register.findById(pseudonyms.of(CUSTOMER)).orElseThrow(() -> new AssertionError("nobody was erased")).getErasedAt();
    }

    private Notification onlyNotificationFor(String bookingRef) {
        List<Notification> raised = notificationsFor(bookingRef);
        assertThat(raised).as("exactly one notification for %s", bookingRef).hasSize(1);
        return raised.get(0);
    }

    private Conversation threadFor(String bookingRef) {
        return conversations
            .findAll()
            .stream()
            .filter(c -> bookingRef.equals(c.getBookingReference()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no conversation was raised for " + bookingRef));
    }

    private List<Notification> notificationsFor(String bookingRef) {
        return notifications.findAll().stream().filter(n -> ("/bookings/" + bookingRef).equals(n.getDeepLink())).toList();
    }

    /** The ordinary path, so the erased path below is a difference rather than a coincidence. */
    @Test
    @Transactional
    @DisplayName("an ordinary customer's event stores their login and their name")
    void ordinaryCustomer() {
        consumer.onBookingEvent(requested("b-ordinary", CUSTOMER));

        assertThat(threadFor("b-ordinary").getCustomerLogin()).isEqualTo(CUSTOMER);
        assertThat(notificationsFor("b-ordinary")).anySatisfy(n -> assertThat(n.getBody()).contains("Yaa Tobeforgotten"));
    }

    /**
     * <strong>Every notification must carry {@code /bookings/<ref>} — {@code decisions.md} D36.</strong>
     *
     * <p>The deep link is the only handle erasure has on a notification that names a customer in
     * somebody <em>else's</em> bell menu, because no query by recipient can return it. A row raised
     * without one is invisible to erasure for good, and a row raised with a <em>blank</em> reference
     * is worse than invisible: {@code "/bookings/" + ""} is the literal {@code /bookings/}, which is
     * neither null nor blank, so it survives the filter and then matches every other row built the
     * same way — strangers' bodies overwritten, on a receipt that reads as a larger clean erasure.
     *
     * <p>So the notification is not raised at all. One bell row is a cheaper loss than a permanent
     * hole in what erasure can reach.
     */
    @Test
    @Transactional
    @DisplayName("an event with no booking reference raises no notification at all")
    void anEventWithoutABookingReferenceRaisesNothing() {
        consumer.onBookingEvent(requested("", CUSTOMER));

        assertThat(notifications.findAll()).noneMatch(n -> "/bookings/".equals(n.getDeepLink()));
    }

    /**
     * <strong>The race this table exists for.</strong>
     *
     * <p>The desk erases; a {@code booking.requested} still in flight lands a moment later. Before
     * {@link net.jojoaddison.domain.ErasedSubject}, that event re-created a conversation under the
     * original login — verified on the quality box, where {@code t-b-a2216d8d | verify.subject}
     * appeared seconds after that login had been erased and a clean receipt had been filed.
     *
     * <p>Since WP-08 it pins a second thing: this event carries <strong>no</strong>
     * {@code bookingRaisedAt}, which is every event published before that field existed, and an event
     * that cannot say how old its booking is must still be treated as older than the erasure. Scoping
     * the register (D37) must not become a way to bypass it.
     */
    @Test
    @Transactional
    @DisplayName("an event arriving after the erasure writes the pseudonym, not the login")
    void aLateEventCannotResurrectAnErasedCustomer() {
        erasure.eraseCustomer(CUSTOMER);

        consumer.onBookingEvent(requested("b-late", CUSTOMER));

        Conversation raised = threadFor("b-late");
        assertThat(raised.getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER)).isNotEqualTo(CUSTOMER);
        // The thread is still raised. Skipping it would leave the professional's list with a hole
        // where a real booking is, to protect an identifier that can simply be replaced.
        assertThat(raised.getProfessionalRef()).isEqualTo(PRO_REF);
    }

    /**
     * And the name too. The professional's notification is the one that carries it, so an erased
     * customer's name would otherwise arrive in somebody else's bell menu after the erasure.
     */
    @Test
    @Transactional
    @DisplayName("a late event does not put the erased customer's name in the professional's menu")
    void aLateEventDoesNotAnnounceTheName() {
        erasure.eraseCustomer(CUSTOMER);

        consumer.onBookingEvent(requested("b-late-name", CUSTOMER));

        assertThat(notificationsFor("b-late-name"))
            .isNotEmpty()
            .allSatisfy(n -> assertThat(n.getBody()).doesNotContain("Yaa").doesNotContain("Tobeforgotten"));
    }

    /**
     * <strong>WP-08, case 1 — the erasure still covers what existed when it ran.</strong>
     *
     * <p>{@code decisions.md} D37 scopes the register rather than removing it, so the first thing to
     * pin is that the scoping did not quietly turn it off. A booking raised before {@code erasedAt} is
     * covered whatever event about it turns up and whenever it turns up.
     *
     * <p>Green before WP-08 as well as after — the old code pseudonymised everything — and that is the
     * point of keeping it: it is the assertion that goes red if someone later scopes this by the
     * event's timestamp, or by the person, or not at all.
     */
    @Test
    @Transactional
    @DisplayName("a booking raised before the erasure is still pseudonymised")
    void aBookingOlderThanTheErasureStaysErased() {
        erasure.eraseCustomer(CUSTOMER);

        consumer.onBookingEvent(event("healthconnect.booking.requested", "b-old", CUSTOMER, erasedAt().minusSeconds(3600)));

        assertThat(threadFor("b-old").getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER)).isNotEqualTo(CUSTOMER);
        assertThat(onlyNotificationFor("b-old").getBody()).doesNotContain("Yaa").doesNotContain("Tobeforgotten");
    }

    /**
     * <strong>WP-08, case 2 — somebody who comes back is not erased again.</strong>
     *
     * <p>Erasure does not touch the gateway's user store, so an erased person can log in and book
     * again. Before {@code decisions.md} D37 the register was a permanent verdict on the person:
     * messaging pseudonymised the new booking's thread while booking and catalog stored the real login,
     * and the estate disagreed with itself about whether that customer existed. D37 answers that the
     * erasure covered what existed when it ran, so a booking raised afterwards is a new relationship
     * and is stored under the real login.
     *
     * <p>Confirmed red against the code before this package, where the thread came back keyed to
     * {@code erased-…} instead of to the login.
     */
    @Test
    @Transactional
    @DisplayName("a booking raised after the erasure is stored under the real login")
    void aCustomerWhoBooksAgainIsNotErasedAgain() {
        erasure.eraseCustomer(CUSTOMER);

        consumer.onBookingEvent(event("healthconnect.booking.requested", "b-new", CUSTOMER, erasedAt().plusSeconds(3600)));

        assertThat(threadFor("b-new").getCustomerLogin()).isEqualTo(CUSTOMER);
        // And the professional is told who is asking, which is the other half of the same decision —
        // a thread under the real login beside a bell row saying "A customer" is the estate
        // disagreeing with itself in a smaller way rather than not at all.
        assertThat(onlyNotificationFor("b-new").getBody()).contains("Yaa Tobeforgotten");
    }

    /**
     * <strong>WP-08, case 3 — the regression the obvious reading would introduce, and the most
     * important of the three.</strong>
     *
     * <p>D37's first wording said to compare the <em>event's</em> timestamp against {@code erasedAt}.
     * A booking that was already open when the erasure ran goes on emitting events afterwards — its
     * acceptance, its completion, its cancellation — and every one of those is stamped after
     * {@code erasedAt}. Under that reading each would be written under the customer's real login,
     * putting an erased person back into the estate one lifecycle step at a time, and breaking D36's
     * guarantee that its residual does not grow.
     *
     * <p>So the comparison is against the <em>booking's</em> {@code raisedAt}, carried on the payload
     * by booking's {@code OutboxRecorder} and unchanged by any transition. Here the booking predates
     * the erasure and the event does not: the event is a completion of it, delivered an hour later.
     * The notification must go to the alias.
     *
     * <p>Green against the code before this package, which pseudonymised unconditionally, and
     * <strong>confirmed red against the event-timestamp reading</strong> — with
     * {@code bookingRaisedAt} swapped for the event's own instant this asserts
     * {@code expected: "erased-…" but was: "yaa.tobeforgotten"}.
     */
    @Test
    @Transactional
    @DisplayName("a later event on a booking older than the erasure does not restore the login")
    void aLaterLifecycleEventOnAnOldBookingStaysErased() {
        erasure.eraseCustomer(CUSTOMER);
        Instant raisedBeforeTheErasure = erasedAt().minusSeconds(86400);

        // The completion happens now, an hour after the erasure. Only the booking's age may decide it.
        consumer.onBookingEvent(event("healthconnect.booking.completed", "b-open", CUSTOMER, raisedBeforeTheErasure));

        assertThat(onlyNotificationFor("b-open").getRecipientLogin()).isEqualTo(pseudonyms.of(CUSTOMER)).isNotEqualTo(CUSTOMER);
    }

    /**
     * And the same booking's later events, once it is the <em>new</em> booking, keep the real login —
     * so the scoping is a property of the booking rather than of the event that opened it.
     */
    @Test
    @Transactional
    @DisplayName("a later event on a booking raised after the erasure keeps the real login")
    void aLaterLifecycleEventOnANewBookingKeepsTheLogin() {
        erasure.eraseCustomer(CUSTOMER);

        consumer.onBookingEvent(event("healthconnect.booking.completed", "b-new-open", CUSTOMER, erasedAt().plusSeconds(60)));

        assertThat(onlyNotificationFor("b-new-open").getRecipientLogin()).isEqualTo(CUSTOMER);
    }
}
