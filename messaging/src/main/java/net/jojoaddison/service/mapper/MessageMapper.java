package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.service.dto.ConversationDTO;
import net.jojoaddison.service.dto.MessageDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Message} and its DTO {@link MessageDTO}.
 */
@Mapper(componentModel = "spring")
public interface MessageMapper extends EntityMapper<MessageDTO, Message> {
    @Mapping(target = "conversation", source = "conversation", qualifiedByName = "conversationId")
    MessageDTO toDto(Message s);

    @Named("conversationId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ConversationDTO toDtoConversationId(Conversation conversation);
}
