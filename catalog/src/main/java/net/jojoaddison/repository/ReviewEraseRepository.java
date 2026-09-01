package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * A customer's own reviews — {@code decisions.md} D24/D34.
 *
 * <p>Replaces a {@code findAll().stream().filter(...)} that loaded every review in the service into
 * memory to find one person's. Messaging's erasure carries a comment explaining why that shape is
 * wrong; catalog was doing it anyway, in the same feature.
 *
 * <p>A new top-level interface rather than a method on the generated {@code ReviewRepository}, which
 * a regeneration would discard — the {@code FavouriteQueryRepository} pattern.
 */
@Repository
public interface ReviewEraseRepository extends JpaRepository<Review, Long> {
    List<Review> findByCustomerLogin(String customerLogin);
}
