package net.jojoaddison.repository;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Which customers this service holds nothing recent about — {@code decisions.md} D96, backlog NEW-52.
 *
 * <p>A top-level interface in its own new file, so a regeneration leaves it alone while a method added
 * to the generated {@code BookingRepository} would be discarded, and not nested inside another type,
 * because Spring Data creates no beans for those and the failure arrives at context startup rather
 * than at compile time.
 *
 * <h2>It answers "nothing recent about this person", not "this booking is old"</h2>
 *
 * <p>The distinction is the whole of why this query is shaped the way it is.
 * {@code ErasureWorkflow.eraseCustomer} takes a <strong>login</strong> and redacts every row in the
 * service belonging to it — so a sweep that selected <em>bookings</em> past the window and erased
 * their customers would erase last week's booking along with the six-year-old one that qualified. A
 * customer is eligible only when <strong>every</strong> booking of theirs is outside the window, which
 * is why the predicate is written as a customer-level exclusion rather than a row-level selection.
 *
 * <h2>Four instants, any one of which keeps a person</h2>
 *
 * <p>A booking's financial life does not end when it is raised. So the retention clock is read from
 * the <em>latest</em> thing that happened to it, expressed here as: a customer is kept if
 * <strong>any</strong> of {@code raisedAt}, {@code respondedAt}, {@code completedAt} or
 * {@code cancelledAt} on <strong>any</strong> of their bookings is at or after the cutoff. That is
 * deliberately not a {@code GREATEST} over the four — it is the same answer and it is monotone in the
 * safe direction by construction: every instant is a reason to keep somebody and none is a reason to
 * erase them, so an instant this query forgets to read can only ever make it keep a person longer.
 *
 * <p>The {@code raisedAt is null} arm is <strong>unreachable and free</strong>, which is D53's idiom
 * and the reason to keep it rather than tidy it away: the column is {@code @NotNull} with
 * {@code nullable = false}, so no booking that has ever existed can satisfy it. If one ever does — a
 * migration, a direct write — that customer is kept rather than erased on incomplete data, and an
 * irreversible act is the last place to let a null mean "old".
 *
 * <h2>An already-erased subject is excluded, and this is not an optimisation</h2>
 *
 * <p>{@code eraseCustomer} replaces {@code customerLogin} with {@code SubjectPseudonym.of(login)}, and
 * the alias inherits the row's instants. So without the {@code erased_subject} exclusion the sweep
 * would select the alias on its next run and erase <em>it</em> — deriving an HMAC <em>of the
 * alias</em>, a second and different pseudonym, and doing it again every night. That destroys the one
 * property D34/D35 built the pseudonym for: one person's rows staying grouped under one stable alias
 * for accounting and audit. The register is the exact record of who has been erased here (D39) and is
 * therefore the right thing to ask, rather than matching on the {@code erased-} prefix, which is a
 * guess about a format.
 */
@Repository
public interface RetentionSweepRepository extends JpaRepository<Booking, Long> {
    /**
     * Logins with at least one booking, none of which shows any activity at or after {@code cutoff},
     * and which are not already in the erased-subject register.
     *
     * @param cutoff the start of the retention window — {@code now - financialDays}.
     * @return distinct logins, each safe to hand to {@code ErasureWorkflow.eraseCustomer}.
     */
    @Query(
        """
        select distinct b.customerLogin from Booking b
        where b.customerLogin not in (select e.pseudonym from ErasedSubject e)
          and b.customerLogin not in (
            select b2.customerLogin from Booking b2
            where b2.raisedAt is null
               or b2.raisedAt >= :cutoff
               or b2.respondedAt >= :cutoff
               or b2.completedAt >= :cutoff
               or b2.cancelledAt >= :cutoff
          )
        """
    )
    List<String> customersWithNoActivitySince(@Param("cutoff") Instant cutoff);
}
