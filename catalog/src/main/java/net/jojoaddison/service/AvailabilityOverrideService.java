package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.AvailabilityOverride;
import net.jojoaddison.repository.AvailabilityOverrideRepository;
import net.jojoaddison.service.dto.AvailabilityOverrideDTO;
import net.jojoaddison.service.mapper.AvailabilityOverrideMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.AvailabilityOverride}.
 */
@Service
@Transactional
public class AvailabilityOverrideService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityOverrideService.class);

    private final AvailabilityOverrideRepository availabilityOverrideRepository;

    private final AvailabilityOverrideMapper availabilityOverrideMapper;

    public AvailabilityOverrideService(
        AvailabilityOverrideRepository availabilityOverrideRepository,
        AvailabilityOverrideMapper availabilityOverrideMapper
    ) {
        this.availabilityOverrideRepository = availabilityOverrideRepository;
        this.availabilityOverrideMapper = availabilityOverrideMapper;
    }

    /**
     * Save a availabilityOverride.
     *
     * @param availabilityOverrideDTO the entity to save.
     * @return the persisted entity.
     */
    public AvailabilityOverrideDTO save(AvailabilityOverrideDTO availabilityOverrideDTO) {
        LOG.debug("Request to save AvailabilityOverride : {}", availabilityOverrideDTO);
        AvailabilityOverride availabilityOverride = availabilityOverrideMapper.toEntity(availabilityOverrideDTO);
        availabilityOverride = availabilityOverrideRepository.save(availabilityOverride);
        return availabilityOverrideMapper.toDto(availabilityOverride);
    }

    /**
     * Update a availabilityOverride.
     *
     * @param availabilityOverrideDTO the entity to save.
     * @return the persisted entity.
     */
    public AvailabilityOverrideDTO update(AvailabilityOverrideDTO availabilityOverrideDTO) {
        LOG.debug("Request to update AvailabilityOverride : {}", availabilityOverrideDTO);
        AvailabilityOverride availabilityOverride = availabilityOverrideMapper.toEntity(availabilityOverrideDTO);
        availabilityOverride = availabilityOverrideRepository.save(availabilityOverride);
        return availabilityOverrideMapper.toDto(availabilityOverride);
    }

    /**
     * Partially update a availabilityOverride.
     *
     * @param availabilityOverrideDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<AvailabilityOverrideDTO> partialUpdate(AvailabilityOverrideDTO availabilityOverrideDTO) {
        LOG.debug("Request to partially update AvailabilityOverride : {}", availabilityOverrideDTO);

        return availabilityOverrideRepository
            .findById(availabilityOverrideDTO.getId())
            .map(existingAvailabilityOverride -> {
                availabilityOverrideMapper.partialUpdate(existingAvailabilityOverride, availabilityOverrideDTO);

                return existingAvailabilityOverride;
            })
            .map(availabilityOverrideRepository::save)
            .map(availabilityOverrideMapper::toDto);
    }

    /**
     * Get all the availabilityOverrides.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityOverrideDTO> findAll() {
        LOG.debug("Request to get all AvailabilityOverrides");
        return availabilityOverrideRepository
            .findAll()
            .stream()
            .map(availabilityOverrideMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one availabilityOverride by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<AvailabilityOverrideDTO> findOne(Long id) {
        LOG.debug("Request to get AvailabilityOverride : {}", id);
        return availabilityOverrideRepository.findById(id).map(availabilityOverrideMapper::toDto);
    }

    /**
     * Delete the availabilityOverride by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete AvailabilityOverride : {}", id);
        availabilityOverrideRepository.deleteById(id);
    }
}
