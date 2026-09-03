package net.jojoaddison.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Lookups behind the customer's four tabs and the professional's inbox and schedule.
 *
 * <p>Kept out of the generated {@link BookingRepository} so regenerating booking from JDL cannot
 * silently drop them.
 */
@Repository
public interface BookingQueryRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByReference(String reference);

    /**
     * The same lookup, holding the row until the transaction ends — {@code decisions.md} D43.
     *
     * <p>For webhooks and nothing else. Every provider retries a callback it did not get a 2xx for,
     * and two retries arriving together would both read {@code PENDING_PAYMENT}, both find the
     * transition legal and both apply it: two audit rows, and — much worse — two
     * {@code booking.requested} events, so the professional is told twice and messaging opens the
     * conversation against the second. Idempotency by "has the booking already moved?" is only
     * idempotent if the answer cannot change between the question and the write.
     *
     * <p>Deliberately not used by the ordinary customer and professional paths: those are one person
     * pressing one button, and a lock taken on every read of a booking would be a cost paid by
     * everybody for a case that only a machine produces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.reference = :reference")
    Optional<Booking> findByReferenceForUpdate(@Param("reference") String reference);

    List<Booking> findByCustomerLoginOrderByScheduledDateDesc(String customerLogin);

    List<Booking> findByCustomerLoginAndStatusOrderByScheduledDateDesc(String customerLogin, BookingStatus status);

    List<Booking> findByProfessionalRefOrderByScheduledDateAsc(String professionalRef);

    List<Booking> findByProfessionalRefAndStatusOrderByScheduledDateAsc(String professionalRef, BookingStatus status);

    List<Booking> findByProfessionalLoginOrderByScheduledDateAsc(String professionalLogin);

    List<Booking> findByProfessionalLoginAndStatusOrderByScheduledDateAsc(String professionalLogin, BookingStatus status);

    boolean existsByReference(String reference);

    long countByStatus(BookingStatus status);
}
