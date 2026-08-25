package net.jojoaddison.service;

import jakarta.persistence.criteria.JoinType;
import net.jojoaddison.domain.*; // for static metamodels
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.service.criteria.LedgerCriteria;
import net.jojoaddison.service.dto.LedgerDTO;
import net.jojoaddison.service.mapper.LedgerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Ledger} entities in the database.
 * The main input is a {@link LedgerCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link LedgerDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class LedgerQueryService extends QueryService<Ledger> {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerQueryService.class);

    private final LedgerRepository ledgerRepository;

    private final LedgerMapper ledgerMapper;

    public LedgerQueryService(LedgerRepository ledgerRepository, LedgerMapper ledgerMapper) {
        this.ledgerRepository = ledgerRepository;
        this.ledgerMapper = ledgerMapper;
    }

    /**
     * Return a {@link Page} of {@link LedgerDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<LedgerDTO> findByCriteria(LedgerCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Ledger> specification = createSpecification(criteria);
        return ledgerRepository.findAll(specification, page).map(ledgerMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(LedgerCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Ledger> specification = createSpecification(criteria);
        return ledgerRepository.count(specification);
    }

    /**
     * Function to convert {@link LedgerCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Ledger> createSpecification(LedgerCriteria criteria) {
        Specification<Ledger> specification = Specification.unrestricted();
        specification = specification.and((root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch(Ledger_.payout, JoinType.LEFT);
            }
            return null;
        });
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildRangeSpecification(criteria.getId(), Ledger_.id),
                    buildStringSpecification(criteria.getBookingReference(), Ledger_.bookingReference),
                    buildStringSpecification(criteria.getProfessionalRef(), Ledger_.professionalRef),
                    buildStringSpecification(criteria.getProfessionalLogin(), Ledger_.professionalLogin),
                    buildRangeSpecification(criteria.getGrossMinor(), Ledger_.grossMinor),
                    buildRangeSpecification(criteria.getCommissionMinor(), Ledger_.commissionMinor),
                    buildRangeSpecification(criteria.getNetMinor(), Ledger_.netMinor),
                    buildStringSpecification(criteria.getCurrency(), Ledger_.currency),
                    buildSpecification(criteria.getDeliveryMode(), Ledger_.deliveryMode),
                    buildStringSpecification(criteria.getServiceRef(), Ledger_.serviceRef),
                    buildStringSpecification(criteria.getServiceName(), Ledger_.serviceName),
                    buildRangeSpecification(criteria.getEarnedOn(), Ledger_.earnedOn),
                    buildStringSpecification(criteria.getReversalOf(), Ledger_.reversalOf),
                    buildSpecification(criteria.getPayoutId(), root -> root.join(Ledger_.payout, JoinType.LEFT).get(Payout_.id))
                )
            );
        }
        return specification;
    }
}
