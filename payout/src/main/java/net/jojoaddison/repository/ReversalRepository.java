package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Ledger lookups by reference, for dispute reversals.
 *
 * <p>A new top-level file beside the generated {@code LedgerRepository}, which regeneration
 * rewrites. Top-level rather than nested: Spring Data creates no beans for nested repository
 * interfaces and the context simply fails to start.
 */
@Repository
public interface ReversalRepository extends JpaRepository<Ledger, Long> {
    /**
     * Unique on the entity, so this returns at most one row. It is doing double duty here: finding
     * the earning to reverse (by booking reference) and checking whether a reversal already exists
     * (by dispute reference, which is what a compensating entry stores in the same column).
     */
    Optional<Ledger> findByBookingReference(String bookingReference);

    /** Every compensating entry against one booking. */
    List<Ledger> findByReversalOf(String bookingReference);
}
