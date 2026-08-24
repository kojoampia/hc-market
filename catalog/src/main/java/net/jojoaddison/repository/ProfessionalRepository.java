package net.jojoaddison.repository;

import net.jojoaddison.domain.Professional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Professional entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, Long>, JpaSpecificationExecutor<Professional> {}
