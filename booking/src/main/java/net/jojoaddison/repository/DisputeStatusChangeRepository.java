package net.jojoaddison.repository;

import net.jojoaddison.domain.DisputeStatusChange;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DisputeStatusChange entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DisputeStatusChangeRepository extends JpaRepository<DisputeStatusChange, Long> {}
