package net.jojoaddison.repository;

import java.util.Collection;
import java.util.List;
import net.jojoaddison.domain.ProfessionalRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only access to the {@code professional_rating} view.
 *
 * <p>Deliberately extends {@link JpaRepository} rather than exposing writes of its own — the entity
 * is {@code @Immutable}, so any write attempt fails at the mapping layer. Nothing in this service
 * may create, update or delete a rating: the only way to move one is to publish a review.
 */
@Repository
public interface ProfessionalRatingRepository extends JpaRepository<ProfessionalRating, Long> {
    /**
     * Batch-load ratings for a page of professionals.
     *
     * <p>The Browse screen renders up to a page of professionals at once. Loading each rating
     * individually would issue one query per card; this loads them in one. Professionals with no
     * reviews are simply absent from the result — see {@link ProfessionalRating}.
     */
    List<ProfessionalRating> findByProfessionalIdIn(Collection<Long> professionalIds);
}
