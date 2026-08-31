package net.jojoaddison.repository;

import net.jojoaddison.domain.ErasedSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The erased-subject register — {@code decisions.md} D24/D32.
 *
 * <p>A top-level interface in its own file rather than a method added to a generated repository,
 * because a regeneration discards the latter. Same reason {@code FavouriteQueryRepository} exists
 * beside catalog's generated one.
 */
@Repository
public interface ErasedSubjectRepository extends JpaRepository<ErasedSubject, String> {}
