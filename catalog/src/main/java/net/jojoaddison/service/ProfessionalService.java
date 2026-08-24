package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.service.dto.ProfessionalDTO;
import net.jojoaddison.service.mapper.ProfessionalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Professional}.
 */
@Service
@Transactional
public class ProfessionalService {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalService.class);

    private final ProfessionalRepository professionalRepository;

    private final ProfessionalMapper professionalMapper;

    public ProfessionalService(ProfessionalRepository professionalRepository, ProfessionalMapper professionalMapper) {
        this.professionalRepository = professionalRepository;
        this.professionalMapper = professionalMapper;
    }

    /**
     * Save a professional.
     *
     * @param professionalDTO the entity to save.
     * @return the persisted entity.
     */
    public ProfessionalDTO save(ProfessionalDTO professionalDTO) {
        LOG.debug("Request to save Professional : {}", professionalDTO);
        Professional professional = professionalMapper.toEntity(professionalDTO);
        professional = professionalRepository.save(professional);
        return professionalMapper.toDto(professional);
    }

    /**
     * Update a professional.
     *
     * @param professionalDTO the entity to save.
     * @return the persisted entity.
     */
    public ProfessionalDTO update(ProfessionalDTO professionalDTO) {
        LOG.debug("Request to update Professional : {}", professionalDTO);
        Professional professional = professionalMapper.toEntity(professionalDTO);
        professional = professionalRepository.save(professional);
        return professionalMapper.toDto(professional);
    }

    /**
     * Partially update a professional.
     *
     * @param professionalDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ProfessionalDTO> partialUpdate(ProfessionalDTO professionalDTO) {
        LOG.debug("Request to partially update Professional : {}", professionalDTO);

        return professionalRepository
            .findById(professionalDTO.getId())
            .map(existingProfessional -> {
                professionalMapper.partialUpdate(existingProfessional, professionalDTO);

                return existingProfessional;
            })
            .map(professionalRepository::save)
            .map(professionalMapper::toDto);
    }

    /**
     * Get one professional by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ProfessionalDTO> findOne(Long id) {
        LOG.debug("Request to get Professional : {}", id);
        return professionalRepository.findById(id).map(professionalMapper::toDto);
    }

    /**
     * Delete the professional by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Professional : {}", id);
        professionalRepository.deleteById(id);
    }
}
