package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.HighlightAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Highlight;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.HighlightRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link HighlightResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class HighlightResourceIT {

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final Integer DEFAULT_SORT_ORDER = 1;
    private static final Integer UPDATED_SORT_ORDER = 2;

    private static final String ENTITY_API_URL = "/api/highlights";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private HighlightRepository highlightRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restHighlightMockMvc;

    private Highlight highlight;

    private Highlight insertedHighlight;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Highlight createEntity(EntityManager em) {
        Highlight highlight = new Highlight().label(DEFAULT_LABEL).sortOrder(DEFAULT_SORT_ORDER);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        highlight.setProfessional(professional);
        return highlight;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Highlight createUpdatedEntity(EntityManager em) {
        Highlight updatedHighlight = new Highlight().label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createUpdatedEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        updatedHighlight.setProfessional(professional);
        return updatedHighlight;
    }

    @BeforeEach
    void initTest() {
        highlight = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedHighlight != null) {
            highlightRepository.delete(insertedHighlight);
            insertedHighlight = null;
        }
    }

    @Test
    @Transactional
    void createHighlight() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Highlight
        var returnedHighlight = om.readValue(
            restHighlightMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(highlight)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Highlight.class
        );

        // Validate the Highlight in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertHighlightUpdatableFieldsEquals(returnedHighlight, getPersistedHighlight(returnedHighlight));

        insertedHighlight = returnedHighlight;
    }

    @Test
    @Transactional
    void createHighlightWithExistingId() throws Exception {
        // Create the Highlight with an existing ID
        highlight.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restHighlightMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(highlight)))
            .andExpect(status().isBadRequest());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkLabelIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        highlight.setLabel(null);

        // Create the Highlight, which fails.

        restHighlightMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(highlight)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSortOrderIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        highlight.setSortOrder(null);

        // Create the Highlight, which fails.

        restHighlightMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(highlight)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllHighlights() throws Exception {
        // Initialize the database
        insertedHighlight = highlightRepository.saveAndFlush(highlight);

        // Get all the highlightList
        restHighlightMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(highlight.getId().intValue())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].sortOrder").value(hasItem(DEFAULT_SORT_ORDER)));
    }

    @Test
    @Transactional
    void getHighlight() throws Exception {
        // Initialize the database
        insertedHighlight = highlightRepository.saveAndFlush(highlight);

        // Get the highlight
        restHighlightMockMvc
            .perform(get(ENTITY_API_URL_ID, highlight.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(highlight.getId().intValue()))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.sortOrder").value(DEFAULT_SORT_ORDER));
    }

    @Test
    @Transactional
    void getNonExistingHighlight() throws Exception {
        // Get the highlight
        restHighlightMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingHighlight() throws Exception {
        // Initialize the database
        insertedHighlight = highlightRepository.saveAndFlush(highlight);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the highlight
        Highlight updatedHighlight = highlightRepository.findById(highlight.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedHighlight are not directly saved in db
        em.detach(updatedHighlight);
        updatedHighlight.label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);

        restHighlightMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedHighlight.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedHighlight))
            )
            .andExpect(status().isOk());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedHighlightToMatchAllProperties(updatedHighlight);
    }

    @Test
    @Transactional
    void putNonExistingHighlight() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        highlight.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHighlightMockMvc
            .perform(
                put(ENTITY_API_URL_ID, highlight.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(highlight))
            )
            .andExpect(status().isBadRequest());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchHighlight() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        highlight.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHighlightMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(highlight))
            )
            .andExpect(status().isBadRequest());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamHighlight() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        highlight.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHighlightMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(highlight)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateHighlightWithPatch() throws Exception {
        // Initialize the database
        insertedHighlight = highlightRepository.saveAndFlush(highlight);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the highlight using partial update
        Highlight partialUpdatedHighlight = new Highlight();
        partialUpdatedHighlight.setId(highlight.getId());

        restHighlightMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHighlight.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedHighlight))
            )
            .andExpect(status().isOk());

        // Validate the Highlight in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertHighlightUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedHighlight, highlight),
            getPersistedHighlight(highlight)
        );
    }

    @Test
    @Transactional
    void fullUpdateHighlightWithPatch() throws Exception {
        // Initialize the database
        insertedHighlight = highlightRepository.saveAndFlush(highlight);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the highlight using partial update
        Highlight partialUpdatedHighlight = new Highlight();
        partialUpdatedHighlight.setId(highlight.getId());

        partialUpdatedHighlight.label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);

        restHighlightMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHighlight.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedHighlight))
            )
            .andExpect(status().isOk());

        // Validate the Highlight in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertHighlightUpdatableFieldsEquals(partialUpdatedHighlight, getPersistedHighlight(partialUpdatedHighlight));
    }

    @Test
    @Transactional
    void patchNonExistingHighlight() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        highlight.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHighlightMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, highlight.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(highlight))
            )
            .andExpect(status().isBadRequest());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchHighlight() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        highlight.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHighlightMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(highlight))
            )
            .andExpect(status().isBadRequest());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamHighlight() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        highlight.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHighlightMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(highlight)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Highlight in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteHighlight() throws Exception {
        // Initialize the database
        insertedHighlight = highlightRepository.saveAndFlush(highlight);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the highlight
        restHighlightMockMvc
            .perform(delete(ENTITY_API_URL_ID, highlight.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return highlightRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Highlight getPersistedHighlight(Highlight highlight) {
        return highlightRepository.findById(highlight.getId()).orElseThrow();
    }

    protected void assertPersistedHighlightToMatchAllProperties(Highlight expectedHighlight) {
        assertHighlightAllPropertiesEquals(expectedHighlight, getPersistedHighlight(expectedHighlight));
    }

    protected void assertPersistedHighlightToMatchUpdatableProperties(Highlight expectedHighlight) {
        assertHighlightAllUpdatablePropertiesEquals(expectedHighlight, getPersistedHighlight(expectedHighlight));
    }
}
