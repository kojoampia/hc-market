package net.jojoaddison.repository;

import net.jojoaddison.domain.AvailabilityRule;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the AvailabilityRule entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {}
