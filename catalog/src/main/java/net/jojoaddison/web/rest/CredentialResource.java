package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Credential;
import net.jojoaddison.repository.CredentialRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Credential}.
 */
@RestController
@RequestMapping("/api/credentials")
@Transactional
public class CredentialResource {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialResource.class);

    private static final String ENTITY_NAME = "healthconnectCatalogCredential";

    @Value("${jhipster.clientApp.name:healthconnectCatalog}")
    private String applicationName;

    private final CredentialRepository credentialRepository;

    public CredentialResource(CredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    /**
     * {@code POST  /credentials} : Create a new credential.
     *
     * @param credential the credential to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new credential, or with status {@code 400 (Bad Request)} if the credential has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Credential> createCredential(@Valid @RequestBody Credential credential) throws URISyntaxException {
        LOG.debug("REST request to save Credential : {}", credential);
        if (credential.getId() != null) {
            throw new BadRequestAlertException("A new credential cannot already have an ID", ENTITY_NAME, "idexists");
        }
        credential = credentialRepository.save(credential);
        return ResponseEntity.created(new URI("/api/credentials/" + credential.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, credential.getId().toString()))
            .body(credential);
    }

    /**
     * {@code PUT  /credentials/:id} : Updates an existing credential.
     *
     * @param id the id of the credential to save.
     * @param credential the credential to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated credential,
     * or with status {@code 400 (Bad Request)} if the credential is not valid,
     * or with status {@code 500 (Internal Server Error)} if the credential couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Credential> updateCredential(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Credential credential
    ) throws URISyntaxException {
        LOG.debug("REST request to update Credential : {}, {}", id, credential);
        if (credential.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, credential.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!credentialRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        credential = credentialRepository.save(credential);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, credential.getId().toString()))
            .body(credential);
    }

    /**
     * {@code PATCH  /credentials/:id} : Partial updates given fields of an existing credential, field will ignore if it is null
     *
     * @param id the id of the credential to save.
     * @param credential the credential to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated credential,
     * or with status {@code 400 (Bad Request)} if the credential is not valid,
     * or with status {@code 404 (Not Found)} if the credential is not found,
     * or with status {@code 500 (Internal Server Error)} if the credential couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Credential> partialUpdateCredential(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Credential credential
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Credential partially : {}, {}", id, credential);
        if (credential.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, credential.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!credentialRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Credential> result = credentialRepository
            .findById(credential.getId())
            .map(existingCredential -> {
                updateIfPresent(existingCredential::setLabel, credential.getLabel());
                updateIfPresent(existingCredential::setSortOrder, credential.getSortOrder());

                return existingCredential;
            })
            .map(credentialRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, credential.getId().toString())
        );
    }

    /**
     * {@code GET  /credentials} : get all the Credentials.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Credentials in body.
     */
    @GetMapping("")
    public List<Credential> getAllCredentials() {
        LOG.debug("REST request to get all Credentials");
        return credentialRepository.findAll();
    }

    /**
     * {@code GET  /credentials/:id} : get the "id" credential.
     *
     * @param id the id of the credential to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the credential, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Credential> getCredential(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Credential : {}", id);
        Optional<Credential> credential = credentialRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(credential);
    }

    /**
     * {@code DELETE  /credentials/:id} : delete the "id" credential.
     *
     * @param id the id of the credential to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCredential(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Credential : {}", id);
        credentialRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
