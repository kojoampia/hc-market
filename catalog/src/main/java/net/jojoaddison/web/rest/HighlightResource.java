package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.Highlight;
import net.jojoaddison.repository.HighlightRepository;
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
 * REST controller for managing {@link net.jojoaddison.domain.Highlight}.
 */
@RestController
@RequestMapping("/api/highlights")
@Transactional
public class HighlightResource {

    private static final Logger LOG = LoggerFactory.getLogger(HighlightResource.class);

    private static final String ENTITY_NAME = "healthconnectCatalogHighlight";

    @Value("${jhipster.clientApp.name:healthconnectCatalog}")
    private String applicationName;

    private final HighlightRepository highlightRepository;

    public HighlightResource(HighlightRepository highlightRepository) {
        this.highlightRepository = highlightRepository;
    }

    /**
     * {@code POST  /highlights} : Create a new highlight.
     *
     * @param highlight the highlight to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new highlight, or with status {@code 400 (Bad Request)} if the highlight has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Highlight> createHighlight(@Valid @RequestBody Highlight highlight) throws URISyntaxException {
        LOG.debug("REST request to save Highlight : {}", highlight);
        if (highlight.getId() != null) {
            throw new BadRequestAlertException("A new highlight cannot already have an ID", ENTITY_NAME, "idexists");
        }
        highlight = highlightRepository.save(highlight);
        return ResponseEntity.created(new URI("/api/highlights/" + highlight.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, highlight.getId().toString()))
            .body(highlight);
    }

    /**
     * {@code PUT  /highlights/:id} : Updates an existing highlight.
     *
     * @param id the id of the highlight to save.
     * @param highlight the highlight to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated highlight,
     * or with status {@code 400 (Bad Request)} if the highlight is not valid,
     * or with status {@code 500 (Internal Server Error)} if the highlight couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Highlight> updateHighlight(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Highlight highlight
    ) throws URISyntaxException {
        LOG.debug("REST request to update Highlight : {}, {}", id, highlight);
        if (highlight.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, highlight.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!highlightRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        highlight = highlightRepository.save(highlight);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, highlight.getId().toString()))
            .body(highlight);
    }

    /**
     * {@code PATCH  /highlights/:id} : Partial updates given fields of an existing highlight, field will ignore if it is null
     *
     * @param id the id of the highlight to save.
     * @param highlight the highlight to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated highlight,
     * or with status {@code 400 (Bad Request)} if the highlight is not valid,
     * or with status {@code 404 (Not Found)} if the highlight is not found,
     * or with status {@code 500 (Internal Server Error)} if the highlight couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Highlight> partialUpdateHighlight(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Highlight highlight
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Highlight partially : {}, {}", id, highlight);
        if (highlight.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, highlight.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!highlightRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Highlight> result = highlightRepository
            .findById(highlight.getId())
            .map(existingHighlight -> {
                updateIfPresent(existingHighlight::setLabel, highlight.getLabel());
                updateIfPresent(existingHighlight::setSortOrder, highlight.getSortOrder());

                return existingHighlight;
            })
            .map(highlightRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, highlight.getId().toString())
        );
    }

    /**
     * {@code GET  /highlights} : get all the Highlights.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Highlights in body.
     */
    @GetMapping("")
    public List<Highlight> getAllHighlights() {
        LOG.debug("REST request to get all Highlights");
        return highlightRepository.findAll();
    }

    /**
     * {@code GET  /highlights/:id} : get the "id" highlight.
     *
     * @param id the id of the highlight to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the highlight, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Highlight> getHighlight(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Highlight : {}", id);
        Optional<Highlight> highlight = highlightRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(highlight);
    }

    /**
     * {@code DELETE  /highlights/:id} : delete the "id" highlight.
     *
     * @param id the id of the highlight to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHighlight(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Highlight : {}", id);
        highlightRepository.deleteById(id);
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
