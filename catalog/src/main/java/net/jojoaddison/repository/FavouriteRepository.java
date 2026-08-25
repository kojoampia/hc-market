package net.jojoaddison.repository;

import net.jojoaddison.domain.Favourite;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Favourite entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FavouriteRepository extends JpaRepository<Favourite, Long> {}
