package net.jojoaddison.repository;

import net.jojoaddison.domain.ErasedSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The erased-subject register — {@code decisions.md} D24/D32.
 *
 * <p>A top-level interface in its own file rather than a method added to a generated repository,
 * because a regeneration discards the latter. Same reason {@code FavouriteQueryRepository} exists
 * beside catalog's generated one.
 */
@Repository
public interface ErasedSubjectRepository extends JpaRepository<ErasedSubject, String> {
    /**
     * Rows whose alias is not the length the current derivation produces — {@code decisions.md} D35.
     *
     * <p>The pre-D35 alias was {@code erased-} plus 12 hex characters, 19 in all; the peppered one is
     * {@code erased-} plus 16, so 23. A register still holding 19-character rows was written by an
     * implementation that no longer exists, and nothing this service computes can ever match one
     * again — the subjects behind them are unrecognisable, permanently, and no read will say so.
     * {@code ErasureRegisterGuard} refuses to start on it rather than let the service go on answering
     * {@code isErased} = false about people it erased.
     *
     * <p>Counted by length rather than by a stored version, because there is nothing else to go on:
     * the register holds pseudonyms and a timestamp, deliberately, and by construction cannot hold
     * anything that identifies which rule produced a row.
     */
    @Query("select count(e) from ErasedSubject e where length(e.pseudonym) <> :length")
    long countAliasesNotOfLength(@Param("length") int length);
}
