package net.jojoaddison.repository;

import net.jojoaddison.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Idempotency keys for consumed events. */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {}
