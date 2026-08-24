package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.repository.BookingStatusChangeRepository;
import net.jojoaddison.service.BookingStatusChangeService;
import net.jojoaddison.service.dto.BookingStatusChangeDTO;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.BookingStatusChange}.
 */
@RestController
@RequestMapping("/api/booking-status-changes")
public class BookingStatusChangeResource {

    private static final Logger LOG = LoggerFactory.getLogger(BookingStatusChangeResource.class);

    private static final String ENTITY_NAME = "healthconnectBookingBookingStatusChange";

    @Value("${jhipster.clientApp.name:healthconnectBooking}")
    private String applicationName;

    private final BookingStatusChangeService bookingStatusChangeService;

    private final BookingStatusChangeRepository bookingStatusChangeRepository;

    public BookingStatusChangeResource(
        BookingStatusChangeService bookingStatusChangeService,
        BookingStatusChangeRepository bookingStatusChangeRepository
    ) {
        this.bookingStatusChangeService = bookingStatusChangeService;
        this.bookingStatusChangeRepository = bookingStatusChangeRepository;
    }

    /**
     * {@code POST  /booking-status-changes} : Create a new bookingStatusChange.
     *
     * @param bookingStatusChangeDTO the bookingStatusChangeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new bookingStatusChangeDTO, or with status {@code 400 (Bad Request)} if the bookingStatusChange has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<BookingStatusChangeDTO> createBookingStatusChange(
        @Valid @RequestBody BookingStatusChangeDTO bookingStatusChangeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save BookingStatusChange : {}", bookingStatusChangeDTO);
        if (bookingStatusChangeDTO.getId() != null) {
            throw new BadRequestAlertException("A new bookingStatusChange cannot already have an ID", ENTITY_NAME, "idexists");
        }
        bookingStatusChangeDTO = bookingStatusChangeService.save(bookingStatusChangeDTO);
        return ResponseEntity.created(new URI("/api/booking-status-changes/" + bookingStatusChangeDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, bookingStatusChangeDTO.getId().toString()))
            .body(bookingStatusChangeDTO);
    }

    /**
     * {@code PUT  /booking-status-changes/:id} : Updates an existing bookingStatusChange.
     *
     * @param id the id of the bookingStatusChangeDTO to save.
     * @param bookingStatusChangeDTO the bookingStatusChangeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated bookingStatusChangeDTO,
     * or with status {@code 400 (Bad Request)} if the bookingStatusChangeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the bookingStatusChangeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BookingStatusChangeDTO> updateBookingStatusChange(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody BookingStatusChangeDTO bookingStatusChangeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update BookingStatusChange : {}, {}", id, bookingStatusChangeDTO);
        if (bookingStatusChangeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, bookingStatusChangeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!bookingStatusChangeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        bookingStatusChangeDTO = bookingStatusChangeService.update(bookingStatusChangeDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, bookingStatusChangeDTO.getId().toString()))
            .body(bookingStatusChangeDTO);
    }

    /**
     * {@code PATCH  /booking-status-changes/:id} : Partial updates given fields of an existing bookingStatusChange, field will ignore if it is null
     *
     * @param id the id of the bookingStatusChangeDTO to save.
     * @param bookingStatusChangeDTO the bookingStatusChangeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated bookingStatusChangeDTO,
     * or with status {@code 400 (Bad Request)} if the bookingStatusChangeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the bookingStatusChangeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the bookingStatusChangeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<BookingStatusChangeDTO> partialUpdateBookingStatusChange(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody BookingStatusChangeDTO bookingStatusChangeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update BookingStatusChange partially : {}, {}", id, bookingStatusChangeDTO);
        if (bookingStatusChangeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, bookingStatusChangeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!bookingStatusChangeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<BookingStatusChangeDTO> result = bookingStatusChangeService.partialUpdate(bookingStatusChangeDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, bookingStatusChangeDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /booking-status-changes} : get all the Booking Status Changes.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Booking Status Changes in body.
     */
    @GetMapping("")
    public List<BookingStatusChangeDTO> getAllBookingStatusChanges() {
        LOG.debug("REST request to get all BookingStatusChanges");
        return bookingStatusChangeService.findAll();
    }

    /**
     * {@code GET  /booking-status-changes/:id} : get the "id" bookingStatusChange.
     *
     * @param id the id of the bookingStatusChangeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the bookingStatusChangeDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingStatusChangeDTO> getBookingStatusChange(@PathVariable("id") Long id) {
        LOG.debug("REST request to get BookingStatusChange : {}", id);
        Optional<BookingStatusChangeDTO> bookingStatusChangeDTO = bookingStatusChangeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(bookingStatusChangeDTO);
    }

    /**
     * {@code DELETE  /booking-status-changes/:id} : delete the "id" bookingStatusChange.
     *
     * @param id the id of the bookingStatusChangeDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookingStatusChange(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete BookingStatusChange : {}", id);
        bookingStatusChangeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
