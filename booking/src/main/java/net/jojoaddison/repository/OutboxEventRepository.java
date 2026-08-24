package net.jojoaddison.repository;

import java.util.List;
import net.jojoaddison.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** The outbox. The poller's entire work queue is "unsent, oldest first". */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Query("select e from OutboxEvent e where e.sentAt is null order by e.occurredAt asc, e.id asc")
    List<OutboxEvent> findUnsent(Pageable pageable);

    long countBySentAtIsNull();
}
