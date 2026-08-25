package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.AvailabilityOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Days that depart from the rules. New file, so regeneration leaves it alone. */
@Repository
public interface AvailabilityOverrideQueryRepository extends JpaRepository<AvailabilityOverride, Long> {
    List<AvailabilityOverride> findByProfessionalIdAndOverrideDateBetween(Long professionalId, LocalDate from, LocalDate to);

    Optional<AvailabilityOverride> findByProfessionalIdAndOverrideDate(Long professionalId, LocalDate date);

    List<AvailabilityOverride> findByProfessionalIdOrderByOverrideDateAsc(Long professionalId);
}
