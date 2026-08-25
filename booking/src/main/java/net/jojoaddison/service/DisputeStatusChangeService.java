package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.DisputeStatusChange;
import net.jojoaddison.repository.DisputeStatusChangeRepository;
import net.jojoaddison.service.dto.DisputeStatusChangeDTO;
import net.jojoaddison.service.mapper.DisputeStatusChangeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.DisputeStatusChange}.
 */
@Service
@Transactional
public class DisputeStatusChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(DisputeStatusChangeService.class);

    private final DisputeStatusChangeRepository disputeStatusChangeRepository;

    private final DisputeStatusChangeMapper disputeStatusChangeMapper;

    public DisputeStatusChangeService(
        DisputeStatusChangeRepository disputeStatusChangeRepository,
        DisputeStatusChangeMapper disputeStatusChangeMapper
    ) {
        this.disputeStatusChangeRepository = disputeStatusChangeRepository;
        this.disputeStatusChangeMapper = disputeStatusChangeMapper;
    }

    /**
     * Save a disputeStatusChange.
     *
     * @param disputeStatusChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public DisputeStatusChangeDTO save(DisputeStatusChangeDTO disputeStatusChangeDTO) {
        LOG.debug("Request to save DisputeStatusChange : {}", disputeStatusChangeDTO);
        DisputeStatusChange disputeStatusChange = disputeStatusChangeMapper.toEntity(disputeStatusChangeDTO);
        disputeStatusChange = disputeStatusChangeRepository.save(disputeStatusChange);
        return disputeStatusChangeMapper.toDto(disputeStatusChange);
    }

    /**
     * Update a disputeStatusChange.
     *
     * @param disputeStatusChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public DisputeStatusChangeDTO update(DisputeStatusChangeDTO disputeStatusChangeDTO) {
        LOG.debug("Request to update DisputeStatusChange : {}", disputeStatusChangeDTO);
        DisputeStatusChange disputeStatusChange = disputeStatusChangeMapper.toEntity(disputeStatusChangeDTO);
        disputeStatusChange = disputeStatusChangeRepository.save(disputeStatusChange);
        return disputeStatusChangeMapper.toDto(disputeStatusChange);
    }

    /**
     * Partially update a disputeStatusChange.
     *
     * @param disputeStatusChangeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DisputeStatusChangeDTO> partialUpdate(DisputeStatusChangeDTO disputeStatusChangeDTO) {
        LOG.debug("Request to partially update DisputeStatusChange : {}", disputeStatusChangeDTO);

        return disputeStatusChangeRepository
            .findById(disputeStatusChangeDTO.getId())
            .map(existingDisputeStatusChange -> {
                disputeStatusChangeMapper.partialUpdate(existingDisputeStatusChange, disputeStatusChangeDTO);

                return existingDisputeStatusChange;
            })
            .map(disputeStatusChangeRepository::save)
            .map(disputeStatusChangeMapper::toDto);
    }

    /**
     * Get all the disputeStatusChanges.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<DisputeStatusChangeDTO> findAll() {
        LOG.debug("Request to get all DisputeStatusChanges");
        return disputeStatusChangeRepository
            .findAll()
            .stream()
            .map(disputeStatusChangeMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one disputeStatusChange by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DisputeStatusChangeDTO> findOne(Long id) {
        LOG.debug("Request to get DisputeStatusChange : {}", id);
        return disputeStatusChangeRepository.findById(id).map(disputeStatusChangeMapper::toDto);
    }

    /**
     * Delete the disputeStatusChange by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete DisputeStatusChange : {}", id);
        disputeStatusChangeRepository.deleteById(id);
    }
}
