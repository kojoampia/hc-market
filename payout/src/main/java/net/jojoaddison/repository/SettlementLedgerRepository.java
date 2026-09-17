package net.jojoaddison.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The ledger rows a payout run may settle, and the rows one already did — {@code decisions.md} D95,
 * backlog NEW-51.
 *
 * <h2>{@code payout is null} is the whole guard, and it must not be anything else</h2>
 *
 * <p>{@code Ledger{payout} to Payout{entries}} is the relationship the JDL declares, so a row that
 * has been batched carries the batch and a row that has not carries {@code null}. That single
 * predicate is what stops one earning being paid twice: attaching it to a second batch would have to
 * overwrite the first, which is a write nothing here performs.
 *
 * <p><strong>A reversal is one of these rows and has to be.</strong> D23's compensating entries carry
 * negative amounts, and the professional is paid the <em>sum</em>, so leaving them out pays somebody
 * for a booking that was refunded. They are easy to leave out by accident, because a reversal is not
 * shaped like an earning: {@code Ledger.bookingReference} is unique and that uniqueness is the guard
 * against a replayed {@code booking.completed} double-crediting, so a compensating entry cannot reuse
 * the booking's reference and carries the <em>dispute</em> reference there instead, naming the
 * booking in {@code reversalOf}. A query that selects on the shape of {@code bookingReference}
 * therefore drops every reversal silently and overpays. This one selects on {@code payout is null}
 * and on nothing else about what a row is.
 *
 * <p>Kept in its own top-level file beside the generated {@link LedgerRepository}, which a
 * regeneration rewrites wholesale, and top-level rather than nested because Spring Data creates no
 * beans for nested repository interfaces — the context simply fails to start with "No qualifying bean
 * of type ...$Inner".
 */
@Repository
public interface SettlementLedgerRepository extends JpaRepository<Ledger, Long> {
    /**
     * Every unsettled row for one professional, earned within an inclusive day range.
     *
     * <p>The <em>rows</em>, not a {@code sum}. Three reasons, and the first is the one that matters:
     * the run has to attach each row to the batch it wrote, so it needs them anyway and a second
     * aggregate query would be a second answer that could disagree with the first. The amounts are
     * then summed in Java as {@code long}, which is what money is here — and a one-row JPQL aggregate
     * declared as {@code Object[]} hands back the list wrapped in an array, which is the trap
     * {@code EarningsRepository.lifetime} documents. And a batch is one professional's rows for one
     * period: tens of rows, not a scan.
     *
     * <p>Ordered by {@code earnedOn} then {@code id} so two runs over the same rows produce the same
     * list. Nothing depends on the order; a query whose order is unstated is one whose output cannot
     * be compared between runs.
     *
     * <h2>{@code PESSIMISTIC_WRITE}, and why the guard is false without it</h2>
     *
     * <p>{@code payout is null} stops a <em>later</em> run claiming a settled row. It does not stop a
     * <em>simultaneous</em> one: two runs reading these rows at the same time both see them unclaimed,
     * both compute the same amounts, and both write a batch. The attachment is last-writer-wins, so one
     * batch ends up holding the rows and the other holds none — while <strong>both report the same
     * money</strong>. Settle them both and the professional is paid twice, which is the exact harm this
     * whole file exists to prevent, and nothing in either batch would disagree with anything.
     *
     * <p>So this is {@code select … for update}. The second reader blocks until the first commits, and
     * then — under {@code READ COMMITTED}, which is PostgreSQL's default — re-evaluates the predicate
     * against the committed row, finds {@code payout_id} no longer null, and gets an empty list. The
     * run it belongs to then refuses with {@code NothingToSettle}, which is the right answer.
     *
     * <p>It locks nothing anybody else writes: {@code DisputeEventConsumer} only ever <em>inserts</em> a
     * compensating row and never updates the one it reverses, which is the append-only discipline D23
     * exists for, so the reversal path cannot contend with a payout run.
     *
     * <p><strong>Reasoned and not measured</strong> — no test drives two concurrent runs, and the
     * property rests on PostgreSQL's row-locking rather than on anything in this repository.
     * {@code PayoutRunTest.theBatchQueryTakesAWriteLock} asserts the annotation is here and its mode is
     * this one, which is structural and goes red if it is removed; it does not observe a race. Said
     * plainly because "a lock is declared" and "a race was ruled out" are different claims and the
     * first is the only one anything here establishes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select l from Ledger l
        where l.professionalRef = :professionalRef
          and l.payout is null
          and l.earnedOn >= :from
          and l.earnedOn <= :to
        order by l.earnedOn, l.id
        """
    )
    List<Ledger> unsettledBetween(
        @Param("professionalRef") String professionalRef,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /**
     * How many ledger rows are attached to one batch.
     *
     * <p>Reported on every batch the desk is handed, because it is the one figure that says the
     * attachment actually happened: a batch whose amounts are right and whose entry count is zero is
     * a settlement against nothing, and every row it should have claimed is still {@code payout is
     * null} and will be paid again by the next run. The amounts alone cannot show that — they are
     * computed from the rows before they are attached.
     */
    long countByPayoutId(Long payoutId);
}
