package net.jojoaddison.service;

import jakarta.persistence.criteria.JoinType;
import net.jojoaddison.domain.*; // for static metamodels
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.repository.DisputeRepository;
import net.jojoaddison.service.criteria.DisputeCriteria;
import net.jojoaddison.service.dto.DisputeDTO;
import net.jojoaddison.service.mapper.DisputeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Dispute} entities in the database.
 * The main input is a {@link DisputeCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link DisputeDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class DisputeQueryService extends QueryService<Dispute> {

    private static final Logger LOG = LoggerFactory.getLogger(DisputeQueryService.class);

    private final DisputeRepository disputeRepository;

    private final DisputeMapper disputeMapper;

    public DisputeQueryService(DisputeRepository disputeRepository, DisputeMapper disputeMapper) {
        this.disputeRepository = disputeRepository;
        this.disputeMapper = disputeMapper;
    }

    /**
     * Return a {@link Page} of {@link DisputeDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<DisputeDTO> findByCriteria(DisputeCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Dispute> specification = createSpecification(criteria);
        return disputeRepository.findAll(specification, page).map(disputeMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(DisputeCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Dispute> specification = createSpecification(criteria);
        return disputeRepository.count(specification);
    }

    /**
     * Function to convert {@link DisputeCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Dispute> createSpecification(DisputeCriteria criteria) {
        Specification<Dispute> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildRangeSpecification(criteria.getId(), Dispute_.id),
                    buildStringSpecification(criteria.getReference(), Dispute_.reference),
                    buildStringSpecification(criteria.getBookingReference(), Dispute_.bookingReference),
                    buildSpecification(criteria.getRaisedBy(), Dispute_.raisedBy),
                    buildStringSpecification(criteria.getRaisedByLogin(), Dispute_.raisedByLogin),
                    buildStringSpecification(criteria.getProfessionalRef(), Dispute_.professionalRef),
                    buildStringSpecification(criteria.getReason(), Dispute_.reason),
                    buildSpecification(criteria.getStatus(), Dispute_.status),
                    buildRangeSpecification(criteria.getRaisedAt(), Dispute_.raisedAt),
                    buildRangeSpecification(criteria.getDueBy(), Dispute_.dueBy),
                    buildStringSpecification(criteria.getResolution(), Dispute_.resolution),
                    buildStringSpecification(criteria.getResolvedBy(), Dispute_.resolvedBy),
                    buildRangeSpecification(criteria.getResolvedAt(), Dispute_.resolvedAt),
                    buildRangeSpecification(criteria.getRefundMinor(), Dispute_.refundMinor),
                    buildStringSpecification(criteria.getCurrency(), Dispute_.currency),
                    buildSpecification(criteria.getHistoryId(), root ->
                        root.join(Dispute_.histories, JoinType.LEFT).get(DisputeStatusChange_.id)
                    )
                )
            );
        }
        return specification;
    }
}
