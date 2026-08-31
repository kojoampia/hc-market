package net.jojoaddison.service;

import jakarta.persistence.criteria.JoinType;
import net.jojoaddison.domain.*; // for static metamodels
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.service.criteria.ProfessionalCriteria;
import net.jojoaddison.service.dto.ProfessionalDTO;
import net.jojoaddison.service.mapper.ProfessionalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Professional} entities in the database.
 * The main input is a {@link ProfessionalCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link ProfessionalDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class ProfessionalQueryService extends QueryService<Professional> {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalQueryService.class);

    private final ProfessionalRepository professionalRepository;

    private final ProfessionalMapper professionalMapper;

    public ProfessionalQueryService(ProfessionalRepository professionalRepository, ProfessionalMapper professionalMapper) {
        this.professionalRepository = professionalRepository;
        this.professionalMapper = professionalMapper;
    }

    /**
     * Return a {@link Page} of {@link ProfessionalDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<ProfessionalDTO> findByCriteria(ProfessionalCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Professional> specification = createSpecification(criteria);
        return professionalRepository.findAll(specification, page).map(professionalMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(ProfessionalCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Professional> specification = createSpecification(criteria);
        return professionalRepository.count(specification);
    }

    /**
     * Function to convert {@link ProfessionalCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Professional> createSpecification(ProfessionalCriteria criteria) {
        Specification<Professional> specification = Specification.unrestricted();
        specification = specification.and((root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch(Professional_.category, JoinType.LEFT);
            }
            return null;
        });
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildRangeSpecification(criteria.getId(), Professional_.id),
                    buildStringSpecification(criteria.getReference(), Professional_.reference),
                    buildStringSpecification(criteria.getUserLogin(), Professional_.userLogin),
                    buildStringSpecification(criteria.getDisplayName(), Professional_.displayName),
                    buildStringSpecification(criteria.getInitials(), Professional_.initials),
                    buildStringSpecification(criteria.getHeadline(), Professional_.headline),
                    buildStringSpecification(criteria.getSpeciality(), Professional_.speciality),
                    buildStringSpecification(criteria.getCity(), Professional_.city),
                    buildStringSpecification(criteria.getCountryCode(), Professional_.countryCode),
                    buildRangeSpecification(criteria.getYearsPractising(), Professional_.yearsPractising),
                    buildSpecification(criteria.getVerification(), Professional_.verification),
                    buildSpecification(criteria.getInsured(), Professional_.insured),
                    buildSpecification(criteria.getPoliceClearance(), Professional_.policeClearance),
                    buildRangeSpecification(criteria.getResponseMinutes(), Professional_.responseMinutes),
                    buildRangeSpecification(criteria.getRebookRatePct(), Professional_.rebookRatePct),
                    buildStringSpecification(criteria.getLanguages(), Professional_.languages),
                    buildStringSpecification(criteria.getDeliveryModes(), Professional_.deliveryModes),
                    buildStringSpecification(criteria.getAvatarGradientFrom(), Professional_.avatarGradientFrom),
                    buildStringSpecification(criteria.getAvatarGradientTo(), Professional_.avatarGradientTo),
                    buildRangeSpecification(criteria.getPublishedAt(), Professional_.publishedAt),
                    buildStringSpecification(criteria.getZoneId(), Professional_.zoneId),
                    buildSpecification(criteria.getServiceId(), root ->
                        root.join(Professional_.services, JoinType.LEFT).get(ServiceOffering_.id)
                    ),
                    buildSpecification(criteria.getAvailabilityId(), root ->
                        root.join(Professional_.availabilities, JoinType.LEFT).get(AvailabilitySlot_.id)
                    ),
                    buildSpecification(criteria.getRuleId(), root ->
                        root.join(Professional_.rules, JoinType.LEFT).get(AvailabilityRule_.id)
                    ),
                    buildSpecification(criteria.getOverrideId(), root ->
                        root.join(Professional_.overrides, JoinType.LEFT).get(AvailabilityOverride_.id)
                    ),
                    buildSpecification(criteria.getReviewId(), root -> root.join(Professional_.reviews, JoinType.LEFT).get(Review_.id)),
                    buildSpecification(criteria.getCredentialId(), root ->
                        root.join(Professional_.credentials, JoinType.LEFT).get(Credential_.id)
                    ),
                    buildSpecification(criteria.getHighlightId(), root ->
                        root.join(Professional_.highlights, JoinType.LEFT).get(Highlight_.id)
                    ),
                    buildSpecification(criteria.getVerificationReviewId(), root ->
                        root.join(Professional_.verificationReviews, JoinType.LEFT).get(VerificationReview_.id)
                    ),
                    buildSpecification(criteria.getCategoryId(), root -> root.join(Professional_.category, JoinType.LEFT).get(Category_.id))
                )
            );
        }
        return specification;
    }
}
