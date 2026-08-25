package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Favourite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The Saved list, always scoped to one customer. */
@Repository
public interface FavouriteQueryRepository extends JpaRepository<Favourite, Long> {
    List<Favourite> findByCustomerLoginOrderByAddedAtDesc(String customerLogin);

    Optional<Favourite> findByCustomerLoginAndProfessionalRef(String customerLogin, String professionalRef);
}
