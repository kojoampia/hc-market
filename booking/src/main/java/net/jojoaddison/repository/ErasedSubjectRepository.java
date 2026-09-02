package net.jojoaddison.repository;

import net.jojoaddison.domain.ErasedSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The erased-subject register — {@code decisions.md} D39.
 *
 * <p>A top-level interface in its own file rather than a method on a generated repository, because a
 * regeneration discards the latter, and not nested inside another type, because Spring Data creates
 * no beans for those and the failure arrives at context startup rather than at compile time.
 *
 * <p>Narrower than messaging's copy on purpose. Messaging's carries a length query used by
 * {@code ErasureRegisterGuard} to refuse a startup on pre-D35 aliases; nothing in booking consults
 * this register to decide anything, so it needs no query beyond what {@code JpaRepository} gives.
 * A method added here is a sign that something has started reading it, which is the moment to ask
 * whether booking needs the guard too.
 */
@Repository
public interface ErasedSubjectRepository extends JpaRepository<ErasedSubject, String> {}
