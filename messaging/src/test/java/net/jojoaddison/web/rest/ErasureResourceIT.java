package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.enumeration.Direction;
import net.jojoaddison.repository.ConversationRepository;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.NotificationRepository;
import net.jojoaddison.service.ErasureWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure on the messaging service — {@code decisions.md} D24/D31.
 *
 * <p>The empty-thread case is the one this class exists for, and it came from a real erasure on the
 * quality box rather than from reasoning: a booking raises a conversation before anybody writes in
 * it, so a customer can have a thread keyed to their login and no messages at all. The receipt
 * reported the message count alone and answered <strong>zero</strong> — which an operator would file
 * against a data subject request as "messaging held nothing for this person", while a row keyed to
 * their login sat there re-keyed and uncounted.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureResourceIT {

    private static final String URL = "/api/desk/customers/{login}/erase";
    private static final String CUSTOMER = "ama.tobeforgotten";
    private static final String PRO = "akosua.mensah";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ErasureWorkflow erasure;

    @Autowired
    private net.jojoaddison.repository.ErasedSubjectRepository register;

    private Conversation thread(String reference) {
        return conversations.saveAndFlush(
            new Conversation()
                .reference(reference)
                .customerLogin(CUSTOMER)
                .professionalRef(PRO)
                .bookingReference("b-" + reference)
                .lastMessageAt(Instant.now())
        );
    }

    /**
     * The regression. A thread with nothing in it is still a row bearing the customer's login, so it
     * must be re-keyed <em>and</em> counted.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("an empty conversation is pseudonymised and reported, not silently counted as nothing")
    void anEmptyConversationIsStillReported() throws Exception {
        Conversation empty = thread("c-empty");

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(1))
            .andExpect(jsonPath("$.messagesErased").value(0))
            .andExpect(jsonPath("$.pseudonym").value(ErasureWorkflow.pseudonym(CUSTOMER)));

        assertThat(conversations.findById(empty.getId()).orElseThrow().getCustomerLogin()).isEqualTo(
            ErasureWorkflow.pseudonym(CUSTOMER)
        );
    }

    /**
     * Both directions are redacted, not just the customer's own. A professional's reply quotes what
     * it is replying to often enough that leaving one side intact would leave the other's content
     * sitting inside it.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("message bodies go in both directions, and the thread survives")
    void bodiesAreRedactedBothWays() throws Exception {
        Conversation c = thread("c-talkative");
        messages.saveAndFlush(
            new Message()
                .conversation(c)
                .direction(Direction.CUSTOMER_TO_PROFESSIONAL)
                .body("I have been getting headaches after meals")
                .sentAt(Instant.now())
        );
        messages.saveAndFlush(
            new Message()
                .conversation(c)
                .direction(Direction.PROFESSIONAL_TO_CUSTOMER)
                .body("Headaches after meals can mean a few things")
                .sentAt(Instant.now())
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(1))
            .andExpect(jsonPath("$.messagesErased").value(2));

        assertThat(messages.findAll().stream().map(Message::getBody)).allSatisfy(body ->
            assertThat(body).doesNotContain("headaches").doesNotContain("Headaches")
        );
        // The thread itself survives, so the professional's list does not develop holes.
        assertThat(conversations.findById(c.getId())).isPresent();
        assertThat(conversations.findById(c.getId()).orElseThrow().getProfessionalRef()).isEqualTo(PRO);
    }


    /**
     * The notification sitting in somebody <em>else's</em> bell menu.
     *
     * <p>{@code booking.requested} raises "Ama Mensah asked for a home visit" addressed to the
     * <strong>professional</strong>, so the customer's name lives in a row keyed to a different
     * person's login and no query by recipient will ever return it. That is why it survived the first
     * implementation untouched. The row stays — it is a real event in the professional's history —
     * and only the body goes.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a notification about the customer, addressed to the professional, is redacted")
    void redactsNotificationsHeldByOtherPeople() throws Exception {
        Conversation c = thread("c-named");
        Notification mine = notifications.saveAndFlush(
            new Notification()
                .recipientLogin(CUSTOMER)
                .kind("Booking confirmed")
                .body("Your home visit on 12 Sep at 10:00 is confirmed.")
                .raisedAt(Instant.now())
                .deepLink("/bookings/" + c.getBookingReference())
        );
        Notification theirs = notifications.saveAndFlush(
            new Notification()
                .recipientLogin(PRO)
                .kind("Booking requested")
                .body("Ama Tobeforgotten asked for a home visit on 12 Sep at 10:00.")
                .raisedAt(Instant.now())
                .deepLink("/bookings/" + c.getBookingReference())
        );

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsReKeyed").value(1))
            .andExpect(jsonPath("$.notificationsRedacted").value(1));

        assertThat(notifications.findById(mine.getId()).orElseThrow().getRecipientLogin()).isEqualTo(
            ErasureWorkflow.pseudonym(CUSTOMER)
        );
        Notification after = notifications.findById(theirs.getId()).orElseThrow();
        assertThat(after.getBody()).doesNotContain("Ama").doesNotContain("Tobeforgotten");
        // Still the professional's row, still in their history.
        assertThat(after.getRecipientLogin()).isEqualTo(PRO);
        assertThat(after.getDeepLink()).isEqualTo("/bookings/" + c.getBookingReference());
    }

    /**
     * <strong>Erasure has to outlive the moment it runs in.</strong>
     *
     * <p>Found on the quality box, not here: booking publishes {@code booking.requested} and messaging
     * raises the thread from it seconds later, so a desk that erased in between got a clean receipt
     * and a fresh row under the original login. {@code ErasedSubject} is what makes the erasure a
     * standing fact, and this asserts the register answers for a login it has never stored.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the register remembers, so a late event has something to consult")
    void erasureIsRememberedAfterwards() throws Exception {
        assertThat(erasure.isErased(CUSTOMER)).isFalse();

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        assertThat(erasure.isErased(CUSTOMER)).isTrue();
        assertThat(erasure.isErased("someone.else")).isFalse();
        assertThat(erasure.isErased(null)).isFalse();
        assertThat(erasure.isErased("")).isFalse();
    }


    /**
     * A re-run must not move {@code erasedAt}.
     *
     * <p>Data subject requests get retried — they arrive by email and get forwarded — and
     * {@code save()} on an existing primary key would overwrite the original timestamp with the date
     * of whoever ran it a second time. That timestamp is the one fact an audit of an irreversible
     * action will ask for.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("re-running does not move the erasure timestamp")
    void erasedAtSurvivesARerun() throws Exception {
        thread("c-twice");

        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());
        Instant first = register.findById(ErasureWorkflow.pseudonym(CUSTOMER)).orElseThrow().getErasedAt();

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationsPseudonymised").value(0));

        assertThat(register.findById(ErasureWorkflow.pseudonym(CUSTOMER)).orElseThrow().getErasedAt()).isEqualTo(first);
    }

    @Test
    @Transactional
    @WithMockUser(username = "ordinary.customer", authorities = "ROLE_USER")
    @DisplayName("an ordinary user cannot erase anyone")
    void ordinaryUserIsRefused() throws Exception {
        Conversation c = thread("c-guarded");
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isForbidden());
        assertThat(conversations.findById(c.getId()).orElseThrow().getCustomerLogin()).isEqualTo(CUSTOMER);
    }
}
