package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ConversationDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ConversationDTO.class);
        ConversationDTO conversationDTO1 = new ConversationDTO();
        conversationDTO1.setId(1L);
        ConversationDTO conversationDTO2 = new ConversationDTO();
        assertThat(conversationDTO1).isNotEqualTo(conversationDTO2);
        conversationDTO2.setId(conversationDTO1.getId());
        assertThat(conversationDTO1).isEqualTo(conversationDTO2);
        conversationDTO2.setId(2L);
        assertThat(conversationDTO1).isNotEqualTo(conversationDTO2);
        conversationDTO1.setId(null);
        assertThat(conversationDTO1).isNotEqualTo(conversationDTO2);
    }
}
