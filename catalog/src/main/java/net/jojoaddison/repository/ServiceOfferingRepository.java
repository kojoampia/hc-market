package net.jojoaddison.repository;

import net.jojoaddison.domain.ServiceOffering;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ServiceOffering entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {}
