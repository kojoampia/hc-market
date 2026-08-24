package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.service.dto.LedgerDTO;
import net.jojoaddison.service.mapper.LedgerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Ledger}.
 */
@Service
@Transactional
public class LedgerService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerRepository ledgerRepository;

    private final LedgerMapper ledgerMapper;

    public LedgerService(LedgerRepository ledgerRepository, LedgerMapper ledgerMapper) {
        this.ledgerRepository = ledgerRepository;
        this.ledgerMapper = ledgerMapper;
    }

    /**
     * Save a ledger.
     *
     * @param ledgerDTO the entity to save.
     * @return the persisted entity.
     */
    public LedgerDTO save(LedgerDTO ledgerDTO) {
        LOG.debug("Request to save Ledger : {}", ledgerDTO);
        Ledger ledger = ledgerMapper.toEntity(ledgerDTO);
        ledger = ledgerRepository.save(ledger);
        return ledgerMapper.toDto(ledger);
    }

    /**
     * Update a ledger.
     *
     * @param ledgerDTO the entity to save.
     * @return the persisted entity.
     */
    public LedgerDTO update(LedgerDTO ledgerDTO) {
        LOG.debug("Request to update Ledger : {}", ledgerDTO);
        Ledger ledger = ledgerMapper.toEntity(ledgerDTO);
        ledger = ledgerRepository.save(ledger);
        return ledgerMapper.toDto(ledger);
    }

    /**
     * Partially update a ledger.
     *
     * @param ledgerDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<LedgerDTO> partialUpdate(LedgerDTO ledgerDTO) {
        LOG.debug("Request to partially update Ledger : {}", ledgerDTO);

        return ledgerRepository
            .findById(ledgerDTO.getId())
            .map(existingLedger -> {
                ledgerMapper.partialUpdate(existingLedger, ledgerDTO);

                return existingLedger;
            })
            .map(ledgerRepository::save)
            .map(ledgerMapper::toDto);
    }

    /**
     * Get one ledger by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<LedgerDTO> findOne(Long id) {
        LOG.debug("Request to get Ledger : {}", id);
        return ledgerRepository.findById(id).map(ledgerMapper::toDto);
    }

    /**
     * Delete the ledger by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Ledger : {}", id);
        ledgerRepository.deleteById(id);
    }
}
