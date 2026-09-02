package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.ErasureRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The durable record of erasure fan-outs — {@code decisions.md} D39.
 *
 * <p>A top-level interface in its own file: methods on a generated repository are discarded by the
 * next {@code jhipster jdl --force}, and Spring Data creates no beans for nested interfaces.
 *
 * <p>The one query is by alias, because that is the only question an operator has: <em>show me every
 * attempt at erasing this person</em>. They arrive holding the login, hash it through
 * {@code SubjectPseudonym} exactly as the erasure did, and get the attempts back in order — including
 * the failed ones, which are the reason any of this is written down.
 */
@Repository
public interface ErasureRunRepository extends JpaRepository<ErasureRun, String> {
    List<ErasureRun> findByPseudonymOrderByRanAtAsc(String pseudonym);
}
