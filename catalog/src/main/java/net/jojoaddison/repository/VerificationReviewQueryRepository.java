package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.VerificationReview;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The queries {@code VerificationWorkflow} needs, in a NEW file beside the generated
 * {@code VerificationReviewRepository}.
 *
 * <p>A method added to the generated repository would be discarded by the next
 * {@code jhipster jdl --force}, and the failure is a compile error naming a method that existed
 * minutes ago. {@code FavouriteQueryRepository} beside {@code FavouriteRepository} is the pattern
 * this copies.
 *
 * <p>One interface, one file. Spring Data's scanner creates no beans for repository interfaces
 * nested inside another type — the failure arrives at context startup, in a test, as "No qualifying
 * bean of type ...$Rules", which reads as a wiring mistake rather than as a scanning rule.
 */
public interface VerificationReviewQueryRepository extends JpaRepository<VerificationReview, Long> {
    /**
     * Newest first — the order a desk reads a history in, and the order that makes the first element
     * the row {@code Professional.verification} is the projection of.
     */
    List<VerificationReview> findByProfessionalReferenceOrderByReviewedAtDesc(String professionalReference);
}
