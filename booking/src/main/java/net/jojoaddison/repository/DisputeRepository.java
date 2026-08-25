package net.jojoaddison.repository;

import net.jojoaddison.domain.Dispute;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Dispute entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long>, JpaSpecificationExecutor<Dispute> {}
