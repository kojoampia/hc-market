package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.PayoutRepository;
import net.jojoaddison.service.PayoutService;
import net.jojoaddison.service.dto.PayoutDTO;
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
 * REST controller for managing {@link net.jojoaddison.domain.Payout}.
 */
@RestController
@RequestMapping("/api/payouts")
public class PayoutResource {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutResource.class);

    private static final String ENTITY_NAME = "healthconnectPayoutPayout";

    @Value("${jhipster.clientApp.name:healthconnectPayout}")
    private String applicationName;

    private final PayoutService payoutService;

    private final PayoutRepository payoutRepository;

    public PayoutResource(PayoutService payoutService, PayoutRepository payoutRepository) {
        this.payoutService = payoutService;
        this.payoutRepository = payoutRepository;
    }

    /**
     * {@code POST  /payouts} : Create a new payout.
     *
     * @param payoutDTO the payoutDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new payoutDTO, or with status {@code 400 (Bad Request)} if the payout has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<PayoutDTO> createPayout(@Valid @RequestBody PayoutDTO payoutDTO) throws URISyntaxException {
        LOG.debug("REST request to save Payout : {}", payoutDTO);
        if (payoutDTO.getId() != null) {
            throw new BadRequestAlertException("A new payout cannot already have an ID", ENTITY_NAME, "idexists");
        }
        payoutDTO = payoutService.save(payoutDTO);
        return ResponseEntity.created(new URI("/api/payouts/" + payoutDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, payoutDTO.getId().toString()))
            .body(payoutDTO);
    }

    /**
     * {@code PUT  /payouts/:id} : Updates an existing payout.
     *
     * @param id the id of the payoutDTO to save.
     * @param payoutDTO the payoutDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated payoutDTO,
     * or with status {@code 400 (Bad Request)} if the payoutDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the payoutDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PayoutDTO> updatePayout(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody PayoutDTO payoutDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Payout : {}, {}", id, payoutDTO);
        if (payoutDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, payoutDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!payoutRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        payoutDTO = payoutService.update(payoutDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, payoutDTO.getId().toString()))
            .body(payoutDTO);
    }

    /**
     * {@code PATCH  /payouts/:id} : Partial updates given fields of an existing payout, field will ignore if it is null
     *
     * @param id the id of the payoutDTO to save.
     * @param payoutDTO the payoutDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated payoutDTO,
     * or with status {@code 400 (Bad Request)} if the payoutDTO is not valid,
     * or with status {@code 404 (Not Found)} if the payoutDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the payoutDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<PayoutDTO> partialUpdatePayout(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody PayoutDTO payoutDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Payout partially : {}, {}", id, payoutDTO);
        if (payoutDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, payoutDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!payoutRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<PayoutDTO> result = payoutService.partialUpdate(payoutDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, payoutDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /payouts} : get all the Payouts.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Payouts in body.
     */
    @GetMapping("")
    public ResponseEntity<List<PayoutDTO>> getAllPayouts(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of Payouts");
        Page<PayoutDTO> page = payoutService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /payouts/:id} : get the "id" payout.
     *
     * @param id the id of the payoutDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the payoutDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PayoutDTO> getPayout(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Payout : {}", id);
        Optional<PayoutDTO> payoutDTO = payoutService.findOne(id);
        return ResponseUtil.wrapOrNotFound(payoutDTO);
    }

    /**
     * {@code DELETE  /payouts/:id} : delete the "id" payout.
     *
     * @param id the id of the payoutDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayout(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Payout : {}", id);
        payoutService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
