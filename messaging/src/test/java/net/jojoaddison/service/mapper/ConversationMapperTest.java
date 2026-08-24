package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.ConversationAsserts.*;
import static net.jojoaddison.domain.ConversationTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationMapperTest {

    private ConversationMapper conversationMapper;

    @BeforeEach
    void setUp() {
        conversationMapper = new ConversationMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getConversationSample1();
        var actual = conversationMapper.toEntity(conversationMapper.toDto(expected));
        assertConversationAllPropertiesEquals(expected, actual);
    }
}
