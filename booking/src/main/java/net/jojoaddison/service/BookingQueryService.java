package net.jojoaddison.service;

import jakarta.persistence.criteria.JoinType;
import net.jojoaddison.domain.*; // for static metamodels
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.service.criteria.BookingCriteria;
import net.jojoaddison.service.dto.BookingDTO;
import net.jojoaddison.service.mapper.BookingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link Booking} entities in the database.
 * The main input is a {@link BookingCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link BookingDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class BookingQueryService extends QueryService<Booking> {

    private static final Logger LOG = LoggerFactory.getLogger(BookingQueryService.class);

    private final BookingRepository bookingRepository;

    private final BookingMapper bookingMapper;

    public BookingQueryService(BookingRepository bookingRepository, BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
    }

    /**
     * Return a {@link Page} of {@link BookingDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<BookingDTO> findByCriteria(BookingCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<Booking> specification = createSpecification(criteria);
        return bookingRepository.findAll(specification, page).map(bookingMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(BookingCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<Booking> specification = createSpecification(criteria);
        return bookingRepository.count(specification);
    }

    /**
     * Function to convert {@link BookingCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<Booking> createSpecification(BookingCriteria criteria) {
        Specification<Booking> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildRangeSpecification(criteria.getId(), Booking_.id),
                    buildStringSpecification(criteria.getReference(), Booking_.reference),
                    buildStringSpecification(criteria.getCustomerLogin(), Booking_.customerLogin),
                    buildStringSpecification(criteria.getCustomerName(), Booking_.customerName),
                    buildStringSpecification(criteria.getProfessionalRef(), Booking_.professionalRef),
                    buildStringSpecification(criteria.getProfessionalLogin(), Booking_.professionalLogin),
                    buildStringSpecification(criteria.getServiceRef(), Booking_.serviceRef),
                    buildStringSpecification(criteria.getServiceName(), Booking_.serviceName),
                    buildRangeSpecification(criteria.getPriceMinor(), Booking_.priceMinor),
                    buildStringSpecification(criteria.getCurrency(), Booking_.currency),
                    buildRangeSpecification(criteria.getScheduledDate(), Booking_.scheduledDate),
                    buildStringSpecification(criteria.getScheduledTime(), Booking_.scheduledTime),
                    buildSpecification(criteria.getDeliveryMode(), Booking_.deliveryMode),
                    buildSpecification(criteria.getStatus(), Booking_.status),
                    buildStringSpecification(criteria.getCustomerNote(), Booking_.customerNote),
                    buildStringSpecification(criteria.getOnBehalfOf(), Booking_.onBehalfOf),
                    buildStringSpecification(criteria.getVisitAddress(), Booking_.visitAddress),
                    buildSpecification(criteria.getCareSummaryShared(), Booking_.careSummaryShared),
                    buildRangeSpecification(criteria.getRaisedAt(), Booking_.raisedAt),
                    buildRangeSpecification(criteria.getRespondedAt(), Booking_.respondedAt),
                    buildRangeSpecification(criteria.getCompletedAt(), Booking_.completedAt),
                    buildRangeSpecification(criteria.getCancelledAt(), Booking_.cancelledAt),
                    buildSpecification(criteria.getCancelledBy(), Booking_.cancelledBy),
                    buildStringSpecification(criteria.getCancellationReason(), Booking_.cancellationReason),
                    buildSpecification(criteria.getLateCancellation(), Booking_.lateCancellation),
                    buildSpecification(criteria.getReviewed(), Booking_.reviewed),
                    buildSpecification(criteria.getHistoryId(), root ->
                        root.join(Booking_.histories, JoinType.LEFT).get(BookingStatusChange_.id)
                    )
                )
            );
        }
        return specification;
    }
}
