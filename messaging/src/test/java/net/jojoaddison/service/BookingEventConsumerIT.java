package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.repository.ConversationRepository;
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

    private static String requested(String bookingRef, String customerLogin) {
        return
            """
            {"eventId":"%s","type":"healthconnect.booking.requested","payload":{
              "bookingRef":"%s","customerLogin":"%s","customerName":"Yaa Tobeforgotten",
              "professionalLogin":"%s","professionalRef":"%s","serviceName":"Home visit",
              "scheduledDate":"2026-09-12","scheduledTime":"10:00"}}
            """.formatted("evt-" + bookingRef, bookingRef, customerLogin, PRO_LOGIN, PRO_REF);
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
     * <strong>The race this table exists for.</strong>
     *
     * <p>The desk erases; a {@code booking.requested} still in flight lands a moment later. Before
     * {@link net.jojoaddison.domain.ErasedSubject}, that event re-created a conversation under the
     * original login — verified on the quality box, where {@code t-b-a2216d8d | verify.subject}
     * appeared seconds after that login had been erased and a clean receipt had been filed.
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
}
