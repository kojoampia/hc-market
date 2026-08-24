package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.repository.AvailabilitySlotRepository;
import net.jojoaddison.service.dto.AvailabilitySlotDTO;
import net.jojoaddison.service.mapper.AvailabilitySlotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.AvailabilitySlot}.
 */
@Service
@Transactional
public class AvailabilitySlotService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilitySlotService.class);

    private final AvailabilitySlotRepository availabilitySlotRepository;

    private final AvailabilitySlotMapper availabilitySlotMapper;

    public AvailabilitySlotService(AvailabilitySlotRepository availabilitySlotRepository, AvailabilitySlotMapper availabilitySlotMapper) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.availabilitySlotMapper = availabilitySlotMapper;
    }

    /**
     * Save a availabilitySlot.
     *
     * @param availabilitySlotDTO the entity to save.
     * @return the persisted entity.
     */
    public AvailabilitySlotDTO save(AvailabilitySlotDTO availabilitySlotDTO) {
        LOG.debug("Request to save AvailabilitySlot : {}", availabilitySlotDTO);
        AvailabilitySlot availabilitySlot = availabilitySlotMapper.toEntity(availabilitySlotDTO);
        availabilitySlot = availabilitySlotRepository.save(availabilitySlot);
        return availabilitySlotMapper.toDto(availabilitySlot);
    }

    /**
     * Update a availabilitySlot.
     *
     * @param availabilitySlotDTO the entity to save.
     * @return the persisted entity.
     */
    public AvailabilitySlotDTO update(AvailabilitySlotDTO availabilitySlotDTO) {
        LOG.debug("Request to update AvailabilitySlot : {}", availabilitySlotDTO);
        AvailabilitySlot availabilitySlot = availabilitySlotMapper.toEntity(availabilitySlotDTO);
        availabilitySlot = availabilitySlotRepository.save(availabilitySlot);
        return availabilitySlotMapper.toDto(availabilitySlot);
    }

    /**
     * Partially update a availabilitySlot.
     *
     * @param availabilitySlotDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<AvailabilitySlotDTO> partialUpdate(AvailabilitySlotDTO availabilitySlotDTO) {
        LOG.debug("Request to partially update AvailabilitySlot : {}", availabilitySlotDTO);

        return availabilitySlotRepository
            .findById(availabilitySlotDTO.getId())
            .map(existingAvailabilitySlot -> {
                availabilitySlotMapper.partialUpdate(existingAvailabilitySlot, availabilitySlotDTO);

                return existingAvailabilitySlot;
            })
            .map(availabilitySlotRepository::save)
            .map(availabilitySlotMapper::toDto);
    }

    /**
     * Get all the availabilitySlots.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<AvailabilitySlotDTO> findAll() {
        LOG.debug("Request to get all AvailabilitySlots");
        return availabilitySlotRepository
            .findAll()
            .stream()
            .map(availabilitySlotMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one availabilitySlot by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<AvailabilitySlotDTO> findOne(Long id) {
        LOG.debug("Request to get AvailabilitySlot : {}", id);
        return availabilitySlotRepository.findById(id).map(availabilitySlotMapper::toDto);
    }

    /**
     * Delete the availabilitySlot by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete AvailabilitySlot : {}", id);
        availabilitySlotRepository.deleteById(id);
    }
}
