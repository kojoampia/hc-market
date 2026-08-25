package net.jojoaddison.repository;

import net.jojoaddison.domain.AvailabilityOverride;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the AvailabilityOverride entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AvailabilityOverrideRepository extends JpaRepository<AvailabilityOverride, Long> {}
