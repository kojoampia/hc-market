package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The payout batches — the table on the professional's earnings screen, and the one the brokerage
 * desk writes.
 *
 * <p>Kept out of the generated repository so regeneration cannot drop it. One hand-written repository
 * for the entity rather than one per caller: the desk's reads and the earnings screen's read are the
 * same table asked two questions, and two interfaces over it would be two places to look for the
 * query somebody is about to add.
 */
@Repository
public interface PayoutQueryRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByProfessionalRefInOrderByPeriodStartDesc(List<String> professionalRefs);

    /**
     * One batch by its reference — {@code decisions.md} D95.
     *
     * <p>{@code reference} is {@code unique} in the JDL, so this returns at most one row. The desk
     * addresses a batch by reference and never by {@code id}: the reference is what a settlement is
     * recorded against and what a person reads back off a bank statement, and a database-sequence id
     * is neither.
     */
    Optional<Payout> findByReference(String reference);

    /**
     * How many batches this professional already has whose reference begins with the given prefix.
     *
     * <p>Used to mint the next sequence number within a marketplace month — see
     * {@code PayoutRun.mintReference}. A count is not a lock: two concurrent runs for the same
     * professional and month both compute the same number, and the loser collides on the unique index
     * rather than writing a second batch under the same reference. That is the direction to fail in,
     * and it is why the uniqueness lives in the schema and not here.
     */
    long countByProfessionalRefAndReferenceStartingWith(String professionalRef, String prefix);
}
