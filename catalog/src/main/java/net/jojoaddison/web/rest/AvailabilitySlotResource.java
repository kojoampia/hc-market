package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.AvailabilitySlotRepository;
import net.jojoaddison.service.AvailabilitySlotService;
import net.jojoaddison.service.dto.AvailabilitySlotDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.AvailabilitySlot}.
 */
@RestController
@RequestMapping("/api/availability-slots")
public class AvailabilitySlotResource {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilitySlotResource.class);

    private static final String ENTITY_NAME = "healthconnectCatalogAvailabilitySlot";

    @Value("${jhipster.clientApp.name:healthconnectCatalog}")
    private String applicationName;

    private final AvailabilitySlotService availabilitySlotService;

    private final AvailabilitySlotRepository availabilitySlotRepository;

    public AvailabilitySlotResource(
        AvailabilitySlotService availabilitySlotService,
        AvailabilitySlotRepository availabilitySlotRepository
    ) {
        this.availabilitySlotService = availabilitySlotService;
        this.availabilitySlotRepository = availabilitySlotRepository;
    }

    /**
     * {@code POST  /availability-slots} : Create a new availabilitySlot.
     *
     * @param availabilitySlotDTO the availabilitySlotDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new availabilitySlotDTO, or with status {@code 400 (Bad Request)} if the availabilitySlot has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<AvailabilitySlotDTO> createAvailabilitySlot(@Valid @RequestBody AvailabilitySlotDTO availabilitySlotDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save AvailabilitySlot : {}", availabilitySlotDTO);
        if (availabilitySlotDTO.getId() != null) {
            throw new BadRequestAlertException("A new availabilitySlot cannot already have an ID", ENTITY_NAME, "idexists");
        }
        availabilitySlotDTO = availabilitySlotService.save(availabilitySlotDTO);
        return ResponseEntity.created(new URI("/api/availability-slots/" + availabilitySlotDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, availabilitySlotDTO.getId().toString()))
            .body(availabilitySlotDTO);
    }

    /**
     * {@code PUT  /availability-slots/:id} : Updates an existing availabilitySlot.
     *
     * @param id the id of the availabilitySlotDTO to save.
     * @param availabilitySlotDTO the availabilitySlotDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated availabilitySlotDTO,
     * or with status {@code 400 (Bad Request)} if the availabilitySlotDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the availabilitySlotDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AvailabilitySlotDTO> updateAvailabilitySlot(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody AvailabilitySlotDTO availabilitySlotDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update AvailabilitySlot : {}, {}", id, availabilitySlotDTO);
        if (availabilitySlotDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, availabilitySlotDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!availabilitySlotRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        availabilitySlotDTO = availabilitySlotService.update(availabilitySlotDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, availabilitySlotDTO.getId().toString()))
            .body(availabilitySlotDTO);
    }

    /**
     * {@code PATCH  /availability-slots/:id} : Partial updates given fields of an existing availabilitySlot, field will ignore if it is null
     *
     * @param id the id of the availabilitySlotDTO to save.
     * @param availabilitySlotDTO the availabilitySlotDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated availabilitySlotDTO,
     * or with status {@code 400 (Bad Request)} if the availabilitySlotDTO is not valid,
     * or with status {@code 404 (Not Found)} if the availabilitySlotDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the availabilitySlotDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<AvailabilitySlotDTO> partialUpdateAvailabilitySlot(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody AvailabilitySlotDTO availabilitySlotDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update AvailabilitySlot partially : {}, {}", id, availabilitySlotDTO);
        if (availabilitySlotDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, availabilitySlotDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!availabilitySlotRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<AvailabilitySlotDTO> result = availabilitySlotService.partialUpdate(availabilitySlotDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, availabilitySlotDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /availability-slots} : get all the Availability Slots.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Availability Slots in body.
     */
    @GetMapping("")
    public List<AvailabilitySlotDTO> getAllAvailabilitySlots() {
        LOG.debug("REST request to get all AvailabilitySlots");
        return availabilitySlotService.findAll();
    }

    /**
     * {@code GET  /availability-slots/:id} : get the "id" availabilitySlot.
     *
     * @param id the id of the availabilitySlotDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the availabilitySlotDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AvailabilitySlotDTO> getAvailabilitySlot(@PathVariable("id") Long id) {
        LOG.debug("REST request to get AvailabilitySlot : {}", id);
        Optional<AvailabilitySlotDTO> availabilitySlotDTO = availabilitySlotService.findOne(id);
        return ResponseUtil.wrapOrNotFound(availabilitySlotDTO);
    }

    /**
     * {@code DELETE  /availability-slots/:id} : delete the "id" availabilitySlot.
     *
     * @param id the id of the availabilitySlotDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAvailabilitySlot(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete AvailabilitySlot : {}", id);
        availabilitySlotService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
