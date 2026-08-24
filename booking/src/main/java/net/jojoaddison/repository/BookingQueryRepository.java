package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<Booking> findByCustomerLoginOrderByScheduledDateDesc(String customerLogin);

    List<Booking> findByCustomerLoginAndStatusOrderByScheduledDateDesc(String customerLogin, BookingStatus status);

    List<Booking> findByProfessionalRefOrderByScheduledDateAsc(String professionalRef);

    List<Booking> findByProfessionalRefAndStatusOrderByScheduledDateAsc(String professionalRef, BookingStatus status);

    List<Booking> findByProfessionalLoginOrderByScheduledDateAsc(String professionalLogin);

    List<Booking> findByProfessionalLoginAndStatusOrderByScheduledDateAsc(String professionalLogin, BookingStatus status);

    boolean existsByReference(String reference);

    long countByStatus(BookingStatus status);
}
