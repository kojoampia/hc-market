package net.jojoaddison.service.mapper;

import net.jojoaddison.domain.Conversation;
import net.jojoaddison.service.dto.ConversationDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Conversation} and its DTO {@link ConversationDTO}.
 */
@Mapper(componentModel = "spring")
public interface ConversationMapper extends EntityMapper<ConversationDTO, Conversation> {}
