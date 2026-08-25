package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The earnings aggregates.
 *
 * <p>Every figure the earnings screen shows is computed here, at read time, from the ledger rows
 * that justify it. There is no {@code total_earnings} column to read instead, and there must never
 * be one: a stored total is a number that can disagree with the receipts behind it, which is exactly
 * the failure the prototype's render-time arithmetic made impossible.
 *
 * <p>Kept separate from the generated {@link LedgerRepository} so regenerating payout from JDL
 * cannot silently drop these.
 */
@Repository
public interface EarningsRepository extends JpaRepository<Ledger, Long> {
    /**
     * Per-month rows for one professional, oldest first.
     *
     * <p>Returns the <em>rows</em>, not a rendered series — spec §6's contract rule. The client
     * draws either the chart or the table view from this same payload, which is how the prototype's
     * chart/table toggle stays honest.
     *
     * <p>Grouped on a formatted {@code YYYY-MM} rather than on year and month separately, so the
     * ordering is lexicographic and correct across a year boundary without a compound sort.
     */
    @Query(
        """
        select function('to_char', l.earnedOn, 'YYYY-MM') as month,
               count(l)             as sessions,
               sum(l.grossMinor)    as grossMinor,
               sum(l.commissionMinor) as commissionMinor,
               sum(l.netMinor)      as netMinor
        from Ledger l
        where l.professionalLogin = :login
        group by function('to_char', l.earnedOn, 'YYYY-MM')
        order by function('to_char', l.earnedOn, 'YYYY-MM')
        """
    )
    List<Object[]> earningsByMonth(@Param("login") String login);

    /**
     * Lifetime totals. Spec §14 asserts the gross equals ₵81,620 — the prototype's own figure.
     *
     * <p>Returns {@code List<Object[]>} and not {@code Object[]}, even though the query yields
     * exactly one row. Declaring {@code Object[]} makes Spring Data hand back the LIST wrapped in an
     * array, so the first element is itself an {@code Object[]} and the first cast to {@code Number}
     * fails at runtime with "class [Ljava.lang.Object; cannot be cast to class java.lang.Number" —
     * a 500 that says nothing about the real cause.
     */
    @Query(
        """
        select count(l), coalesce(sum(l.grossMinor),0), coalesce(sum(l.commissionMinor),0), coalesce(sum(l.netMinor),0)
        from Ledger l where l.professionalLogin = :login
        """
    )
    List<Object[]> lifetime(@Param("login") String login);

    /** Sessions and gross by delivery format — the earnings screen's donut. */
    @Query(
        """
        select l.deliveryMode, count(l), sum(l.grossMinor)
        from Ledger l where l.professionalLogin = :login
        group by l.deliveryMode order by l.deliveryMode
        """
    )
    List<Object[]> byDeliveryMode(@Param("login") String login);

    /** Sessions and gross by service. */
    @Query(
        """
        select l.serviceRef, l.serviceName, count(l), sum(l.grossMinor)
        from Ledger l where l.professionalLogin = :login
        group by l.serviceRef, l.serviceName order by sum(l.grossMinor) desc
        """
    )
    List<Object[]> byService(@Param("login") String login);

    /**
     * Gross over a window. Used for month-to-date and, crucially, for the <strong>same slice of
     * days</strong> in the previous month — comparing a partial month against a whole one makes
     * every month look like a collapse until its last day.
     */
    @Query(
        """
        select coalesce(sum(l.grossMinor),0), count(l)
        from Ledger l
        where l.professionalLogin = :login and l.earnedOn >= :from and l.earnedOn <= :to
        """
    )
    List<Object[]> grossBetween(@Param("login") String login, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Whether this booking already has a ledger entry.
     *
     * <p>An indexed existence check rather than scanning the ledger in memory: the consumer runs
     * this on every redelivered event, and the ledger only ever grows.
     */
    boolean existsByBookingReference(String bookingReference);

    /**
     * The professional reference behind a login.
     *
     * <p>{@code Payout} is keyed by {@code professionalRef} while a JWT carries a login, and this
     * service does not own {@code Professional}. Rather than add a column to an entity that has no
     * rows yet — or make every payouts read depend on the catalog service being up — the mapping is
     * taken from the ledger, which already carries both and is written by the same events.
     *
     * <p>A professional with no ledger entries has no payouts either, so the empty case is correct
     * rather than merely tolerable.
     */
    @Query("select distinct l.professionalRef from Ledger l where l.professionalLogin = :login")
    List<String> professionalRefsFor(@Param("login") String login);

}
