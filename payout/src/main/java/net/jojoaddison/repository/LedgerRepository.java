package net.jojoaddison.repository;

import net.jojoaddison.domain.Ledger;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Ledger entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long>, JpaSpecificationExecutor<Ledger> {}
