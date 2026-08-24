package net.jojoaddison.repository;

import net.jojoaddison.domain.BrokerageConfig;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the BrokerageConfig entity.
 */
@SuppressWarnings("unused")
@Repository
public interface BrokerageConfigRepository extends JpaRepository<BrokerageConfig, Long> {}
