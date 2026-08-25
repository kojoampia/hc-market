package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.repository.DisputeRepository;
import net.jojoaddison.service.dto.DisputeDTO;
import net.jojoaddison.service.mapper.DisputeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Dispute}.
 */
@Service
@Transactional
public class DisputeService {

    private static final Logger LOG = LoggerFactory.getLogger(DisputeService.class);

    private final DisputeRepository disputeRepository;

    private final DisputeMapper disputeMapper;

    public DisputeService(DisputeRepository disputeRepository, DisputeMapper disputeMapper) {
        this.disputeRepository = disputeRepository;
        this.disputeMapper = disputeMapper;
    }

    /**
     * Save a dispute.
     *
     * @param disputeDTO the entity to save.
     * @return the persisted entity.
     */
    public DisputeDTO save(DisputeDTO disputeDTO) {
        LOG.debug("Request to save Dispute : {}", disputeDTO);
        Dispute dispute = disputeMapper.toEntity(disputeDTO);
        dispute = disputeRepository.save(dispute);
        return disputeMapper.toDto(dispute);
    }

    /**
     * Update a dispute.
     *
     * @param disputeDTO the entity to save.
     * @return the persisted entity.
     */
    public DisputeDTO update(DisputeDTO disputeDTO) {
        LOG.debug("Request to update Dispute : {}", disputeDTO);
        Dispute dispute = disputeMapper.toEntity(disputeDTO);
        dispute = disputeRepository.save(dispute);
        return disputeMapper.toDto(dispute);
    }

    /**
     * Partially update a dispute.
     *
     * @param disputeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DisputeDTO> partialUpdate(DisputeDTO disputeDTO) {
        LOG.debug("Request to partially update Dispute : {}", disputeDTO);

        return disputeRepository
            .findById(disputeDTO.getId())
            .map(existingDispute -> {
                disputeMapper.partialUpdate(existingDispute, disputeDTO);

                return existingDispute;
            })
            .map(disputeRepository::save)
            .map(disputeMapper::toDto);
    }

    /**
     * Get one dispute by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DisputeDTO> findOne(Long id) {
        LOG.debug("Request to get Dispute : {}", id);
        return disputeRepository.findById(id).map(disputeMapper::toDto);
    }

    /**
     * Delete the dispute by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Dispute : {}", id);
        disputeRepository.deleteById(id);
    }
}
