package net.jojoaddison.repository;

import net.jojoaddison.domain.AvailabilitySlot;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the AvailabilitySlot entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {}
