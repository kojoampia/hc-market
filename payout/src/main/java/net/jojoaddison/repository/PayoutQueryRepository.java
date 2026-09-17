package net.jojoaddison.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * One batch by its reference, for READING — {@code decisions.md} D95.
     *
     * <p>{@code reference} is {@code unique} in the JDL, so this returns at most one row. The desk
     * addresses a batch by reference and never by {@code id}: the reference is what a settlement is
     * recorded against and what a person reads back off a bank statement, and a database-sequence id
     * is neither.
     *
     * <p><strong>Unlocked, deliberately.</strong> This is what {@code PayoutRun.byReference} serves
     * the desk's "did that settlement land" read from, and a read must not be able to block a
     * settlement or be blocked by one. Anything that goes on to <em>write</em> the row uses
     * {@link #findByReferenceForUpdate} instead — the distinction is the whole of D95's review
     * finding, and this method is the reason it is two methods and not a lock added to one.
     */
    Optional<Payout> findByReference(String reference);

    /**
     * The same lookup, holding the row until the transaction ends — {@code decisions.md} D95, as
     * reviewed.
     *
     * <p><strong>For {@code settle} and nothing else, and without it that method is check-then-act.</strong>
     * {@code settle} reads the batch, refuses a {@code PAID} one, refuses a {@code FAILED} one, and
     * then writes. Two settlements of one {@code OPEN} batch arriving together both read {@code OPEN},
     * both pass both refusals and both answer <strong>200</strong> — and the last writer's
     * {@code bankReference} survives, so **the first settlement's record is silently overwritten**.
     * That is the exact loss {@code PayoutRun.settle}'s own javadoc says must not be reachable, and
     * the window is not hypothetical: two desk operators, or one operator and a retrying client with
     * a corrected body. The reconciliation against the bank statement then fails on a record
     * everybody was told was written.
     *
     * <p>This is {@code BookingQueryRepository.findByReferenceForUpdate}'s shape, exactly — the
     * estate's recorded idempotency mechanism for the payment webhook (D43), where the rule is stated
     * as *"what must not happen twice is the transition rather than the callback"*. Here the
     * transition is {@code OPEN → PAID} and the same sentence applies: idempotency by "has this batch
     * already been settled?" is only idempotent if the answer cannot change between the question and
     * the write.
     *
     * <p><strong>Reasoned and not measured</strong>, like {@code SettlementLedgerRepository}'s lock:
     * no test drives two concurrent settlements, and the exclusion is PostgreSQL's.
     * {@code PayoutRunTest} asserts this annotation's mode <em>and</em> that {@code settle} reads
     * through this method rather than the unlocked one — the second half matters, because a lock
     * declared on a method nobody calls is a lock that does nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.reference = :reference")
    Optional<Payout> findByReferenceForUpdate(@Param("reference") String reference);

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
