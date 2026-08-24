package net.jojoaddison.repository;

import net.jojoaddison.domain.BookingStatusChange;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the BookingStatusChange entity.
 */
@SuppressWarnings("unused")
@Repository
public interface BookingStatusChangeRepository extends JpaRepository<BookingStatusChange, Long> {}
