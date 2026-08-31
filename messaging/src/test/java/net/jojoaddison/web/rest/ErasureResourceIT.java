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
import net.jojoaddison.repository.MessageRepository;
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
