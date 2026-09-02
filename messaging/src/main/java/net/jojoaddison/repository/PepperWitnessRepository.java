package net.jojoaddison.repository;

import net.jojoaddison.domain.PepperWitness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The single-row record of what the pepper produced here first — {@code decisions.md} D35.
 *
 * <p>A top-level interface in its own file, like {@code ErasedSubjectRepository} beside it: methods
 * added to a generated repository are discarded by the next {@code jhipster jdl --force}, and Spring
 * Data creates no beans for repositories nested inside another type.
 */
@Repository
public interface PepperWitnessRepository extends JpaRepository<PepperWitness, String> {}
