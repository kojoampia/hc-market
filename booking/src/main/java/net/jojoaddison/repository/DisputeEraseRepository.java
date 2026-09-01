package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Disputes a customer raised — {@code decisions.md} D24/D34.
 *
 * <p>A new top-level interface rather than a method on the generated {@code DisputeRepository}, which
 * a regeneration would discard. Same reason {@code FavouriteQueryRepository} exists beside catalog's
 * generated one.
 */
@Repository
public interface DisputeEraseRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByRaisedByLogin(String raisedByLogin);
}
