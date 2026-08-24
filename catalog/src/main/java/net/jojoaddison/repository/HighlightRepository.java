package net.jojoaddison.repository;

import net.jojoaddison.domain.Highlight;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Highlight entity.
 */
@SuppressWarnings("unused")
@Repository
public interface HighlightRepository extends JpaRepository<Highlight, Long> {}
