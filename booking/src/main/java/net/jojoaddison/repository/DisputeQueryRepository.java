package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Dispute reads, by the references people actually hold.
 *
 * <p>A new top-level file beside the generated {@code DisputeRepository}: regeneration rewrites that
 * one. Top-level and not nested — Spring Data creates no beans for nested repository interfaces, and
 * the failure is a context that will not start.
 */
@Repository
public interface DisputeQueryRepository extends JpaRepository<Dispute, Long> {
    Optional<Dispute> findByReference(String reference);

    /** Unique, so "one dispute per booking" is a schema guarantee rather than a check. */
    Optional<Dispute> findByBookingReference(String bookingReference);

    List<Dispute> findByRaisedByLoginOrderByRaisedAtDesc(String raisedByLogin);

    List<Dispute> findByStatusInOrderByDueByAsc(List<DisputeStatus> statuses);
}
