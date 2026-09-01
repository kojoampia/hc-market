package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.DisputeStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Dispute-history rows a customer is the actor of — {@code decisions.md} D24/D34. */
@Repository
public interface DisputeStatusChangeEraseRepository extends JpaRepository<DisputeStatusChange, Long> {
    List<DisputeStatusChange> findByActor(String actor);
}
