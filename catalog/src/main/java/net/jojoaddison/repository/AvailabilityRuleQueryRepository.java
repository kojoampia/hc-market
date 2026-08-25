package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The rule reads slot generation needs, always scoped to one professional.
 *
 * <p>A NEW top-level interface rather than methods added to the generated
 * {@code AvailabilityRuleRepository}: regeneration rewrites that file and would drop them, the same
 * reason {@link FavouriteQueryRepository} sits beside the generated {@code FavouriteRepository}.
 *
 * <p>Top-level, and that is not a style preference. These three interfaces were first written as
 * nested types inside one outer class, which reads nicely and does not work: Spring Data's
 * repository scanner does not create beans for them, and the failure is a context that will not
 * start with {@code No qualifying bean of type ...$Rules}. One file each.
 */
@Repository
public interface AvailabilityRuleQueryRepository extends JpaRepository<AvailabilityRule, Long> {
    /**
     * Open-ended rules that have started.
     *
     * <p>{@code validUntil} is nullable and null means open-ended, so it needs its own query rather
     * than a range: a plain BETWEEN silently excludes every open-ended rule, which is most of them.
     */
    List<AvailabilityRule> findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilIsNull(
        Long professionalId,
        LocalDate day
    );

    List<AvailabilityRule> findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(
        Long professionalId,
        LocalDate day,
        LocalDate sameDay
    );

    List<AvailabilityRule> findByProfessionalIdOrderByWeekdayAscStartTimeAsc(Long professionalId);
}
