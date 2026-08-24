package net.jojoaddison.repository;

import net.jojoaddison.domain.Credential;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Credential entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {}
