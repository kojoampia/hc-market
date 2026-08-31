package net.jojoaddison.repository;

import net.jojoaddison.domain.VerificationReview;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the VerificationReview entity.
 */
@SuppressWarnings("unused")
@Repository
public interface VerificationReviewRepository extends JpaRepository<VerificationReview, Long> {}
