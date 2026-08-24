package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.BookingStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The append-only audit, read back for one booking. */
@Repository
public interface BookingHistoryRepository extends JpaRepository<BookingStatusChange, Long> {
    List<BookingStatusChange> findByBookingId(Long bookingId);
}
