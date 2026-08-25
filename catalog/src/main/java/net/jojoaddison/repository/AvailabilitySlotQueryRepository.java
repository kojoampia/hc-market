package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Slot reads for generation. New file, so regeneration leaves it alone. */
@Repository
public interface AvailabilitySlotQueryRepository extends JpaRepository<AvailabilitySlot, Long> {
    List<AvailabilitySlot> findByProfessionalIdAndSlotDateBetween(Long professionalId, LocalDate from, LocalDate to);

    List<AvailabilitySlot> findByProfessionalIdAndSlotDate(Long professionalId, LocalDate date);
}
