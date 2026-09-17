package net.jojoaddison.repository;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Disputes whose five working days have expired — {@code decisions.md} D96, backlog NEW-52.
 *
 * <p>A top-level interface in its own new file, for the reason every hand-written repository here is:
 * a method added to the generated {@code DisputeRepository} is discarded by the next regeneration.
 *
 * <h2>Two open statuses, not one</h2>
 *
 * <p>{@code OPEN} and {@code UNDER_REVIEW} are both unresolved as far as the promise is concerned —
 * the prototype offers the customer a resolution in five working days, and a dispute that has been
 * <em>looked at</em> and not decided has still missed it. {@code RESOLVED} and {@code REJECTED} are
 * both decisions and neither is overdue. The statuses are named rather than negated
 * ({@code not in (RESOLVED, REJECTED)}) so that a sixth value added to {@code DisputeStatus} is
 * excluded until somebody decides it belongs, which is the fail-closed direction for a query that
 * drives a warning: a new state silently inheriting "overdue" would put a claim in the log that
 * nobody made.
 *
 * <h2>It returns the rows, and the sweep reads only two fields off them</h2>
 *
 * <p>Deliberately not a projection of references alone. The sweep needs {@code dueBy} to say how long
 * overdue each one is, and a caller that already has the entity cannot be tempted into a second query
 * per row. What it must <strong>not</strong> touch is {@code reason} — a thousand characters the
 * customer typed — or {@code raisedByLogin}; {@link net.jojoaddison.service.DisputeSlaSweep} says so
 * at the log site, which is where the discipline has to live because nothing about this signature
 * enforces it.
 */
@Repository
public interface DisputeSlaRepository extends JpaRepository<Dispute, Long> {
    /**
     * Unresolved disputes whose {@code dueBy} has passed, oldest deadline first.
     *
     * @param now the instant to judge the deadline against, taken as a parameter rather than read
     *            from a clock in here so a test can put the boundary where it needs it.
     */
    @Query(
        """
        select d from Dispute d
        where d.status in (net.jojoaddison.domain.enumeration.DisputeStatus.OPEN,
                           net.jojoaddison.domain.enumeration.DisputeStatus.UNDER_REVIEW)
          and d.dueBy < :now
        order by d.dueBy asc
        """
    )
    List<Dispute> overdueAt(@Param("now") Instant now);
}
