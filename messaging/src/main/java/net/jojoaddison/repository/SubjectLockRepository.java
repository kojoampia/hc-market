package net.jojoaddison.repository;

import net.jojoaddison.domain.ErasedSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Serialises an erasure against a concurrent consumer — {@code decisions.md} D32/D34.
 *
 * <h2>Why the register alone was not enough</h2>
 *
 * <p>D32 recorded the pseudonym at the top of the erasure transaction and said that covered an event
 * arriving mid-flight. Under {@code READ_COMMITTED} it does not: the register row is invisible to any
 * other transaction until the erasure <strong>commits</strong>. So a consumer that begins, reads
 * {@code isErased} → false, and commits after the erasure finishes still writes a conversation under
 * the original login — and the erasure's own sweep could not see that conversation either, because it
 * was uncommitted when the sweep ran. Both commit; the login survives; the receipt says otherwise.
 * Byte for byte the failure D32 describes, in a smaller window.
 *
 * <p>A Postgres advisory transaction lock keyed on the subject closes it. The consumer blocks until
 * the erasure commits and then sees the register; the erasure blocks until an in-flight consumer
 * commits and then sees its conversation. The lock is released with the transaction, so nothing has
 * to remember to unlock, including on the paths that throw.
 *
 * <p><strong>Keyed on the pseudonym's own bytes</strong>, not on the login and not through Postgres's
 * undocumented {@code hashtext}: the key is derived in Java from the same HMAC the alias comes from
 * (D35 peppered both together), so the erasure and the consumer compute the same number without
 * either sending a login to the database as a query parameter, where it would land in
 * {@code pg_stat_activity} and the slow-query log.
 *
 * <p>Contention is per-subject, so two erasures of different people never wait on each other, and the
 * consumer's cost is one round trip on events that carry a customer.
 */
@Repository
public interface SubjectLockRepository extends JpaRepository<ErasedSubject, String> {
    @Query(value = "select pg_advisory_xact_lock(:key)", nativeQuery = true)
    Object lock(@Param("key") long key);
}
