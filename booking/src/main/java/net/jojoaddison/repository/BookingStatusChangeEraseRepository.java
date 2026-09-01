package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.BookingStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Status-history rows a customer is the actor of — {@code decisions.md} D24/D34.
 *
 * <p>The history is the thing {@code ErasureResourceIT} asserts <em>survives</em> an erasure, and it
 * survived with the customer's login sitting in {@code actor} on every row they caused. Requesting
 * and cancelling are both customer actions, so an erased customer left their login in the audit trail
 * of every booking they ever made.
 */
@Repository
public interface BookingStatusChangeEraseRepository extends JpaRepository<BookingStatusChange, Long> {
    List<BookingStatusChange> findByActor(String actor);
}
