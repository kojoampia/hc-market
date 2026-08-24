package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Payout;
import net.jojoaddison.repository.PayoutRepository;
import net.jojoaddison.service.dto.PayoutDTO;
import net.jojoaddison.service.mapper.PayoutMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Payout}.
 */
@Service
@Transactional
public class PayoutService {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutService.class);

    private final PayoutRepository payoutRepository;

    private final PayoutMapper payoutMapper;

    public PayoutService(PayoutRepository payoutRepository, PayoutMapper payoutMapper) {
        this.payoutRepository = payoutRepository;
        this.payoutMapper = payoutMapper;
    }

    /**
     * Save a payout.
     *
     * @param payoutDTO the entity to save.
     * @return the persisted entity.
     */
    public PayoutDTO save(PayoutDTO payoutDTO) {
        LOG.debug("Request to save Payout : {}", payoutDTO);
        Payout payout = payoutMapper.toEntity(payoutDTO);
        payout = payoutRepository.save(payout);
        return payoutMapper.toDto(payout);
    }

    /**
     * Update a payout.
     *
     * @param payoutDTO the entity to save.
     * @return the persisted entity.
     */
    public PayoutDTO update(PayoutDTO payoutDTO) {
        LOG.debug("Request to update Payout : {}", payoutDTO);
        Payout payout = payoutMapper.toEntity(payoutDTO);
        payout = payoutRepository.save(payout);
        return payoutMapper.toDto(payout);
    }

    /**
     * Partially update a payout.
     *
     * @param payoutDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<PayoutDTO> partialUpdate(PayoutDTO payoutDTO) {
        LOG.debug("Request to partially update Payout : {}", payoutDTO);

        return payoutRepository
            .findById(payoutDTO.getId())
            .map(existingPayout -> {
                payoutMapper.partialUpdate(existingPayout, payoutDTO);

                return existingPayout;
            })
            .map(payoutRepository::save)
            .map(payoutMapper::toDto);
    }

    /**
     * Get all the payouts.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<PayoutDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Payouts");
        return payoutRepository.findAll(pageable).map(payoutMapper::toDto);
    }

    /**
     * Get one payout by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<PayoutDTO> findOne(Long id) {
        LOG.debug("Request to get Payout : {}", id);
        return payoutRepository.findById(id).map(payoutMapper::toDto);
    }

    /**
     * Delete the payout by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Payout : {}", id);
        payoutRepository.deleteById(id);
    }
}
