package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Published events that still carry a customer's identity — {@code decisions.md} D24/D34.
 *
 * <p><strong>The outbox was the worst of the gaps.</strong> Every event this service publishes puts
 * {@code customerLogin} and {@code customerName} into {@code payload}, and there is no purge of sent
 * rows anywhere in the service — {@code sent_at} is only ever set. So an erasure that reported success
 * left the person's login and display name in one row per event ever published about them,
 * indefinitely, in a table nothing reads and nobody looks at.
 *
 * <p>Found by {@code aggregateRef}, which is the booking reference, rather than by searching the
 * payload text: the customer's own bookings are already loaded by the erasure, the column is a plain
 * equality match, and a {@code LIKE '%login%'} over a JSON column would be both a table scan and a
 * way to redact somebody else's row whose payload happened to contain the string.
 */
@Repository
public interface OutboxEraseRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByAggregateRefIn(List<String> aggregateRefs);
}
