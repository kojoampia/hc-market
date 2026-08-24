package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.service.LedgerQueryService;
import net.jojoaddison.service.LedgerService;
import net.jojoaddison.service.criteria.LedgerCriteria;
import net.jojoaddison.service.dto.LedgerDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.Ledger}.
 */
@RestController
@RequestMapping("/api/ledgers")
public class LedgerResource {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerResource.class);

    private static final String ENTITY_NAME = "healthconnectPayoutLedger";

    @Value("${jhipster.clientApp.name:healthconnectPayout}")
    private String applicationName;

    private final LedgerService ledgerService;

    private final LedgerRepository ledgerRepository;

    private final LedgerQueryService ledgerQueryService;

    public LedgerResource(LedgerService ledgerService, LedgerRepository ledgerRepository, LedgerQueryService ledgerQueryService) {
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.ledgerQueryService = ledgerQueryService;
    }

    /**
     * {@code POST  /ledgers} : Create a new ledger.
     *
     * @param ledgerDTO the ledgerDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new ledgerDTO, or with status {@code 400 (Bad Request)} if the ledger has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<LedgerDTO> createLedger(@Valid @RequestBody LedgerDTO ledgerDTO) throws URISyntaxException {
        LOG.debug("REST request to save Ledger : {}", ledgerDTO);
        if (ledgerDTO.getId() != null) {
            throw new BadRequestAlertException("A new ledger cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ledgerDTO = ledgerService.save(ledgerDTO);
        return ResponseEntity.created(new URI("/api/ledgers/" + ledgerDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, ledgerDTO.getId().toString()))
            .body(ledgerDTO);
    }

    /**
     * {@code PUT  /ledgers/:id} : Updates an existing ledger.
     *
     * @param id the id of the ledgerDTO to save.
     * @param ledgerDTO the ledgerDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ledgerDTO,
     * or with status {@code 400 (Bad Request)} if the ledgerDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the ledgerDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<LedgerDTO> updateLedger(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody LedgerDTO ledgerDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Ledger : {}, {}", id, ledgerDTO);
        if (ledgerDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ledgerDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ledgerRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        ledgerDTO = ledgerService.update(ledgerDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, ledgerDTO.getId().toString()))
            .body(ledgerDTO);
    }

    /**
     * {@code PATCH  /ledgers/:id} : Partial updates given fields of an existing ledger, field will ignore if it is null
     *
     * @param id the id of the ledgerDTO to save.
     * @param ledgerDTO the ledgerDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ledgerDTO,
     * or with status {@code 400 (Bad Request)} if the ledgerDTO is not valid,
     * or with status {@code 404 (Not Found)} if the ledgerDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the ledgerDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<LedgerDTO> partialUpdateLedger(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody LedgerDTO ledgerDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Ledger partially : {}, {}", id, ledgerDTO);
        if (ledgerDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ledgerDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ledgerRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<LedgerDTO> result = ledgerService.partialUpdate(ledgerDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, ledgerDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /ledgers} : get all the Ledgers.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Ledgers in body.
     */
    @GetMapping("")
    public ResponseEntity<List<LedgerDTO>> getAllLedgers(
        LedgerCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get Ledgers by criteria: {}", criteria);

        Page<LedgerDTO> page = ledgerQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /ledgers/count} : count all the ledgers.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countLedgers(LedgerCriteria criteria) {
        LOG.debug("REST request to count Ledgers by criteria: {}", criteria);
        return ResponseEntity.ok().body(ledgerQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /ledgers/:id} : get the "id" ledger.
     *
     * @param id the id of the ledgerDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the ledgerDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LedgerDTO> getLedger(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Ledger : {}", id);
        Optional<LedgerDTO> ledgerDTO = ledgerService.findOne(id);
        return ResponseUtil.wrapOrNotFound(ledgerDTO);
    }

    /**
     * {@code DELETE  /ledgers/:id} : delete the "id" ledger.
     *
     * @param id the id of the ledgerDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLedger(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Ledger : {}", id);
        ledgerService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
