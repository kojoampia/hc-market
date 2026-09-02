package net.jojoaddison.repository;

import net.jojoaddison.domain.ErasedSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The erased-subject register — {@code decisions.md} D39.
 *
 * <p>A top-level interface in its own file, beside {@code FavouriteQueryRepository} and for the same
 * reason: methods added to a generated repository are discarded by the next
 * {@code jhipster jdl --force}, and Spring Data creates no beans for repositories nested inside
 * another type.
 *
 * <p>Deliberately narrower than messaging's copy, which carries a length query its startup guard
 * needs. Nothing in catalog consults this register to decide anything — it records, and that is all —
 * so a query appearing here is the signal that something has started reading it and that catalog now
 * needs the guard messaging has.
 */
@Repository
public interface ErasedSubjectRepository extends JpaRepository<ErasedSubject, String> {}
