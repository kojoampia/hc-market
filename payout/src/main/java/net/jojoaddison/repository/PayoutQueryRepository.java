package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The payout table on the earnings screen. Kept out of the generated repository so regeneration cannot drop it. */
@Repository
public interface PayoutQueryRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByProfessionalRefInOrderByPeriodStartDesc(List<String> professionalRefs);
}
