package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.repository.ConversationRepository;
import net.jojoaddison.service.dto.ConversationDTO;
import net.jojoaddison.service.mapper.ConversationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Conversation}.
 */
@Service
@Transactional
public class ConversationService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;

    private final ConversationMapper conversationMapper;

    public ConversationService(ConversationRepository conversationRepository, ConversationMapper conversationMapper) {
        this.conversationRepository = conversationRepository;
        this.conversationMapper = conversationMapper;
    }

    /**
     * Save a conversation.
     *
     * @param conversationDTO the entity to save.
     * @return the persisted entity.
     */
    public ConversationDTO save(ConversationDTO conversationDTO) {
        LOG.debug("Request to save Conversation : {}", conversationDTO);
        Conversation conversation = conversationMapper.toEntity(conversationDTO);
        conversation = conversationRepository.save(conversation);
        return conversationMapper.toDto(conversation);
    }

    /**
     * Update a conversation.
     *
     * @param conversationDTO the entity to save.
     * @return the persisted entity.
     */
    public ConversationDTO update(ConversationDTO conversationDTO) {
        LOG.debug("Request to update Conversation : {}", conversationDTO);
        Conversation conversation = conversationMapper.toEntity(conversationDTO);
        conversation = conversationRepository.save(conversation);
        return conversationMapper.toDto(conversation);
    }

    /**
     * Partially update a conversation.
     *
     * @param conversationDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ConversationDTO> partialUpdate(ConversationDTO conversationDTO) {
        LOG.debug("Request to partially update Conversation : {}", conversationDTO);

        return conversationRepository
            .findById(conversationDTO.getId())
            .map(existingConversation -> {
                conversationMapper.partialUpdate(existingConversation, conversationDTO);

                return existingConversation;
            })
            .map(conversationRepository::save)
            .map(conversationMapper::toDto);
    }

    /**
     * Get all the conversations.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<ConversationDTO> findAll() {
        LOG.debug("Request to get all Conversations");
        return conversationRepository.findAll().stream().map(conversationMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one conversation by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDTO> findOne(Long id) {
        LOG.debug("Request to get Conversation : {}", id);
        return conversationRepository.findById(id).map(conversationMapper::toDto);
    }

    /**
     * Delete the conversation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Conversation : {}", id);
        conversationRepository.deleteById(id);
    }
}
