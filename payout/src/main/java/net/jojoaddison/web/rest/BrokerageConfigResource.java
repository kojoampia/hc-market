package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.service.BrokerageConfigService;
import net.jojoaddison.service.dto.BrokerageConfigDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.BrokerageConfig}.
 */
@RestController
@RequestMapping("/api/brokerage-configs")
public class BrokerageConfigResource {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerageConfigResource.class);

    private static final String ENTITY_NAME = "healthconnectPayoutBrokerageConfig";

    @Value("${jhipster.clientApp.name:healthconnectPayout}")
    private String applicationName;

    private final BrokerageConfigService brokerageConfigService;

    private final BrokerageConfigRepository brokerageConfigRepository;

    public BrokerageConfigResource(BrokerageConfigService brokerageConfigService, BrokerageConfigRepository brokerageConfigRepository) {
        this.brokerageConfigService = brokerageConfigService;
        this.brokerageConfigRepository = brokerageConfigRepository;
    }

    /**
     * {@code POST  /brokerage-configs} : Create a new brokerageConfig.
     *
     * @param brokerageConfigDTO the brokerageConfigDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new brokerageConfigDTO, or with status {@code 400 (Bad Request)} if the brokerageConfig has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<BrokerageConfigDTO> createBrokerageConfig(@Valid @RequestBody BrokerageConfigDTO brokerageConfigDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save BrokerageConfig : {}", brokerageConfigDTO);
        if (brokerageConfigDTO.getId() != null) {
            throw new BadRequestAlertException("A new brokerageConfig cannot already have an ID", ENTITY_NAME, "idexists");
        }
        brokerageConfigDTO = brokerageConfigService.save(brokerageConfigDTO);
        return ResponseEntity.created(new URI("/api/brokerage-configs/" + brokerageConfigDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, brokerageConfigDTO.getId().toString()))
            .body(brokerageConfigDTO);
    }

    /**
     * {@code PUT  /brokerage-configs/:id} : Updates an existing brokerageConfig.
     *
     * @param id the id of the brokerageConfigDTO to save.
     * @param brokerageConfigDTO the brokerageConfigDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated brokerageConfigDTO,
     * or with status {@code 400 (Bad Request)} if the brokerageConfigDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the brokerageConfigDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BrokerageConfigDTO> updateBrokerageConfig(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody BrokerageConfigDTO brokerageConfigDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update BrokerageConfig : {}, {}", id, brokerageConfigDTO);
        if (brokerageConfigDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, brokerageConfigDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!brokerageConfigRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        brokerageConfigDTO = brokerageConfigService.update(brokerageConfigDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, brokerageConfigDTO.getId().toString()))
            .body(brokerageConfigDTO);
    }

    /**
     * {@code PATCH  /brokerage-configs/:id} : Partial updates given fields of an existing brokerageConfig, field will ignore if it is null
     *
     * @param id the id of the brokerageConfigDTO to save.
     * @param brokerageConfigDTO the brokerageConfigDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated brokerageConfigDTO,
     * or with status {@code 400 (Bad Request)} if the brokerageConfigDTO is not valid,
     * or with status {@code 404 (Not Found)} if the brokerageConfigDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the brokerageConfigDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<BrokerageConfigDTO> partialUpdateBrokerageConfig(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody BrokerageConfigDTO brokerageConfigDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update BrokerageConfig partially : {}, {}", id, brokerageConfigDTO);
        if (brokerageConfigDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, brokerageConfigDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!brokerageConfigRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<BrokerageConfigDTO> result = brokerageConfigService.partialUpdate(brokerageConfigDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, brokerageConfigDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /brokerage-configs} : get all the Brokerage Configs.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Brokerage Configs in body.
     */
    @GetMapping("")
    public List<BrokerageConfigDTO> getAllBrokerageConfigs() {
        LOG.debug("REST request to get all BrokerageConfigs");
        return brokerageConfigService.findAll();
    }

    /**
     * {@code GET  /brokerage-configs/:id} : get the "id" brokerageConfig.
     *
     * @param id the id of the brokerageConfigDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the brokerageConfigDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BrokerageConfigDTO> getBrokerageConfig(@PathVariable("id") Long id) {
        LOG.debug("REST request to get BrokerageConfig : {}", id);
        Optional<BrokerageConfigDTO> brokerageConfigDTO = brokerageConfigService.findOne(id);
        return ResponseUtil.wrapOrNotFound(brokerageConfigDTO);
    }

    /**
     * {@code DELETE  /brokerage-configs/:id} : delete the "id" brokerageConfig.
     *
     * @param id the id of the brokerageConfigDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBrokerageConfig(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete BrokerageConfig : {}", id);
        brokerageConfigService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
