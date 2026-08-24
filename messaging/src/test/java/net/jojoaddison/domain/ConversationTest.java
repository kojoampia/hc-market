package net.jojoaddison.domain;

import static net.jojoaddison.domain.ConversationTestSamples.*;
import static net.jojoaddison.domain.MessageTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ConversationTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Conversation.class);
        Conversation conversation1 = getConversationSample1();
        Conversation conversation2 = new Conversation();
        assertThat(conversation1).isNotEqualTo(conversation2);

        conversation2.setId(conversation1.getId());
        assertThat(conversation1).isEqualTo(conversation2);

        conversation2 = getConversationSample2();
        assertThat(conversation1).isNotEqualTo(conversation2);
    }

    @Test
    void messagesTest() {
        Conversation conversation = getConversationRandomSampleGenerator();
        Message messageBack = getMessageRandomSampleGenerator();

        conversation.addMessages(messageBack);
        assertThat(conversation.getMessageses()).containsOnly(messageBack);
        assertThat(messageBack.getConversation()).isEqualTo(conversation);

        conversation.removeMessages(messageBack);
        assertThat(conversation.getMessageses()).doesNotContain(messageBack);
        assertThat(messageBack.getConversation()).isNull();

        conversation.messageses(new HashSet<>(Set.of(messageBack)));
        assertThat(conversation.getMessageses()).containsOnly(messageBack);
        assertThat(messageBack.getConversation()).isEqualTo(conversation);

        conversation.setMessageses(new HashSet<>());
        assertThat(conversation.getMessageses()).doesNotContain(messageBack);
        assertThat(messageBack.getConversation()).isNull();
    }
}
