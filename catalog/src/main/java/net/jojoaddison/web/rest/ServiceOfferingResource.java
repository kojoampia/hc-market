package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.ServiceOfferingRepository;
import net.jojoaddison.service.ServiceOfferingService;
import net.jojoaddison.service.dto.ServiceOfferingDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.ServiceOffering}.
 */
@RestController
@RequestMapping("/api/service-offerings")
public class ServiceOfferingResource {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceOfferingResource.class);

    private static final String ENTITY_NAME = "healthconnectCatalogServiceOffering";

    @Value("${jhipster.clientApp.name:healthconnectCatalog}")
    private String applicationName;

    private final ServiceOfferingService serviceOfferingService;

    private final ServiceOfferingRepository serviceOfferingRepository;

    public ServiceOfferingResource(ServiceOfferingService serviceOfferingService, ServiceOfferingRepository serviceOfferingRepository) {
        this.serviceOfferingService = serviceOfferingService;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    /**
     * {@code POST  /service-offerings} : Create a new serviceOffering.
     *
     * @param serviceOfferingDTO the serviceOfferingDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new serviceOfferingDTO, or with status {@code 400 (Bad Request)} if the serviceOffering has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ServiceOfferingDTO> createServiceOffering(@Valid @RequestBody ServiceOfferingDTO serviceOfferingDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save ServiceOffering : {}", serviceOfferingDTO);
        if (serviceOfferingDTO.getId() != null) {
            throw new BadRequestAlertException("A new serviceOffering cannot already have an ID", ENTITY_NAME, "idexists");
        }
        serviceOfferingDTO = serviceOfferingService.save(serviceOfferingDTO);
        return ResponseEntity.created(new URI("/api/service-offerings/" + serviceOfferingDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, serviceOfferingDTO.getId().toString()))
            .body(serviceOfferingDTO);
    }

    /**
     * {@code PUT  /service-offerings/:id} : Updates an existing serviceOffering.
     *
     * @param id the id of the serviceOfferingDTO to save.
     * @param serviceOfferingDTO the serviceOfferingDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated serviceOfferingDTO,
     * or with status {@code 400 (Bad Request)} if the serviceOfferingDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the serviceOfferingDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceOfferingDTO> updateServiceOffering(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody ServiceOfferingDTO serviceOfferingDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ServiceOffering : {}, {}", id, serviceOfferingDTO);
        if (serviceOfferingDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, serviceOfferingDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!serviceOfferingRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        serviceOfferingDTO = serviceOfferingService.update(serviceOfferingDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, serviceOfferingDTO.getId().toString()))
            .body(serviceOfferingDTO);
    }

    /**
     * {@code PATCH  /service-offerings/:id} : Partial updates given fields of an existing serviceOffering, field will ignore if it is null
     *
     * @param id the id of the serviceOfferingDTO to save.
     * @param serviceOfferingDTO the serviceOfferingDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated serviceOfferingDTO,
     * or with status {@code 400 (Bad Request)} if the serviceOfferingDTO is not valid,
     * or with status {@code 404 (Not Found)} if the serviceOfferingDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the serviceOfferingDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ServiceOfferingDTO> partialUpdateServiceOffering(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody ServiceOfferingDTO serviceOfferingDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ServiceOffering partially : {}, {}", id, serviceOfferingDTO);
        if (serviceOfferingDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, serviceOfferingDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!serviceOfferingRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ServiceOfferingDTO> result = serviceOfferingService.partialUpdate(serviceOfferingDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, serviceOfferingDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /service-offerings} : get all the Service Offerings.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Service Offerings in body.
     */
    @GetMapping("")
    public List<ServiceOfferingDTO> getAllServiceOfferings() {
        LOG.debug("REST request to get all ServiceOfferings");
        return serviceOfferingService.findAll();
    }

    /**
     * {@code GET  /service-offerings/:id} : get the "id" serviceOffering.
     *
     * @param id the id of the serviceOfferingDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the serviceOfferingDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceOfferingDTO> getServiceOffering(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ServiceOffering : {}", id);
        Optional<ServiceOfferingDTO> serviceOfferingDTO = serviceOfferingService.findOne(id);
        return ResponseUtil.wrapOrNotFound(serviceOfferingDTO);
    }

    /**
     * {@code DELETE  /service-offerings/:id} : delete the "id" serviceOffering.
     *
     * @param id the id of the serviceOfferingDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServiceOffering(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ServiceOffering : {}", id);
        serviceOfferingService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
