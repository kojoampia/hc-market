package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.AvailabilityRule;
import net.jojoaddison.repository.AvailabilityRuleRepository;
import net.jojoaddison.service.dto.AvailabilityRuleDTO;
import net.jojoaddison.service.mapper.AvailabilityRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.AvailabilityRule}.
 */
@Service
@Transactional
public class AvailabilityRuleService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityRuleService.class);

    private final AvailabilityRuleRepository availabilityRuleRepository;

    private final AvailabilityRuleMapper availabilityRuleMapper;

    public AvailabilityRuleService(AvailabilityRuleRepository availabilityRuleRepository, AvailabilityRuleMapper availabilityRuleMapper) {
        this.availabilityRuleRepository = availabilityRuleRepository;
        this.availabilityRuleMapper = availabilityRuleMapper;
    }

    /**
     * Save a availabilityRule.
     *
     * @param availabilityRuleDTO the entity to save.
     * @return the persisted entity.
     */
    public AvailabilityRuleDTO save(AvailabilityRuleDTO availabilityRuleDTO) {
        LOG.debug("Request to save AvailabilityRule : {}", availabilityRuleDTO);
        AvailabilityRule availabilityRule = availabilityRuleMapper.toEntity(availabilityRuleDTO);
        availabilityRule = availabilityRuleRepository.save(availabilityRule);
        return availabilityRuleMapper.toDto(availabilityRule);
    }

    /**
     * Update a availabilityRule.
     *
     * @param availabilityRuleDTO the entity to save.
     * @return the persisted entity.
     */
    public AvailabilityRuleDTO update(AvailabilityRuleDTO availabilityRuleDTO) {
        LOG.debug("Request to update AvailabilityRule : {}", availabilityRuleDTO);
        AvailabilityRule availabilityRule = availabilityRuleMapper.toEntity(availabilityRuleDTO);
        availabilityRule = availabilityRuleRepository.save(availabilityRule);
        return availabilityRuleMapper.toDto(availabilityRule);
    }

    /**
     * Partially update a availabilityRule.
     *
     * @param availabilityRuleDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<AvailabilityRuleDTO> partialUpdate(AvailabilityRuleDTO availabilityRuleDTO) {
        LOG.debug("Request to partially update AvailabilityRule : {}", availabilityRuleDTO);

        return availabilityRuleRepository
            .findById(availabilityRuleDTO.getId())
            .map(existingAvailabilityRule -> {
                availabilityRuleMapper.partialUpdate(existingAvailabilityRule, availabilityRuleDTO);

                return existingAvailabilityRule;
            })
            .map(availabilityRuleRepository::save)
            .map(availabilityRuleMapper::toDto);
    }

    /**
     * Get all the availabilityRules.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityRuleDTO> findAll() {
        LOG.debug("Request to get all AvailabilityRules");
        return availabilityRuleRepository
            .findAll()
            .stream()
            .map(availabilityRuleMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one availabilityRule by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<AvailabilityRuleDTO> findOne(Long id) {
        LOG.debug("Request to get AvailabilityRule : {}", id);
        return availabilityRuleRepository.findById(id).map(availabilityRuleMapper::toDto);
    }

    /**
     * Delete the availabilityRule by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete AvailabilityRule : {}", id);
        availabilityRuleRepository.deleteById(id);
    }
}
