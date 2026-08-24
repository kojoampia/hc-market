package net.jojoaddison.repository;

import net.jojoaddison.domain.Payout;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Payout entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {}
