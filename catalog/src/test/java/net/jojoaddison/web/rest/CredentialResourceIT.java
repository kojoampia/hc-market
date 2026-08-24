package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.CredentialAsserts.*;
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
import net.jojoaddison.domain.Credential;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.CredentialRepository;
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
 * Integration tests for the {@link CredentialResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class CredentialResourceIT {

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final Integer DEFAULT_SORT_ORDER = 1;
    private static final Integer UPDATED_SORT_ORDER = 2;

    private static final String ENTITY_API_URL = "/api/credentials";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restCredentialMockMvc;

    private Credential credential;

    private Credential insertedCredential;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Credential createEntity(EntityManager em) {
        Credential credential = new Credential().label(DEFAULT_LABEL).sortOrder(DEFAULT_SORT_ORDER);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        credential.setProfessional(professional);
        return credential;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Credential createUpdatedEntity(EntityManager em) {
        Credential updatedCredential = new Credential().label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createUpdatedEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        updatedCredential.setProfessional(professional);
        return updatedCredential;
    }

    @BeforeEach
    void initTest() {
        credential = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedCredential != null) {
            credentialRepository.delete(insertedCredential);
            insertedCredential = null;
        }
    }

    @Test
    @Transactional
    void createCredential() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Credential
        var returnedCredential = om.readValue(
            restCredentialMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(credential)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Credential.class
        );

        // Validate the Credential in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertCredentialUpdatableFieldsEquals(returnedCredential, getPersistedCredential(returnedCredential));

        insertedCredential = returnedCredential;
    }

    @Test
    @Transactional
    void createCredentialWithExistingId() throws Exception {
        // Create the Credential with an existing ID
        credential.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restCredentialMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(credential)))
            .andExpect(status().isBadRequest());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkLabelIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        credential.setLabel(null);

        // Create the Credential, which fails.

        restCredentialMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(credential)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSortOrderIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        credential.setSortOrder(null);

        // Create the Credential, which fails.

        restCredentialMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(credential)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllCredentials() throws Exception {
        // Initialize the database
        insertedCredential = credentialRepository.saveAndFlush(credential);

        // Get all the credentialList
        restCredentialMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(credential.getId().intValue())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].sortOrder").value(hasItem(DEFAULT_SORT_ORDER)));
    }

    @Test
    @Transactional
    void getCredential() throws Exception {
        // Initialize the database
        insertedCredential = credentialRepository.saveAndFlush(credential);

        // Get the credential
        restCredentialMockMvc
            .perform(get(ENTITY_API_URL_ID, credential.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(credential.getId().intValue()))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.sortOrder").value(DEFAULT_SORT_ORDER));
    }

    @Test
    @Transactional
    void getNonExistingCredential() throws Exception {
        // Get the credential
        restCredentialMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingCredential() throws Exception {
        // Initialize the database
        insertedCredential = credentialRepository.saveAndFlush(credential);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the credential
        Credential updatedCredential = credentialRepository.findById(credential.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedCredential are not directly saved in db
        em.detach(updatedCredential);
        updatedCredential.label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);

        restCredentialMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedCredential.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedCredential))
            )
            .andExpect(status().isOk());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedCredentialToMatchAllProperties(updatedCredential);
    }

    @Test
    @Transactional
    void putNonExistingCredential() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        credential.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCredentialMockMvc
            .perform(
                put(ENTITY_API_URL_ID, credential.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(credential))
            )
            .andExpect(status().isBadRequest());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchCredential() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        credential.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCredentialMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(credential))
            )
            .andExpect(status().isBadRequest());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamCredential() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        credential.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCredentialMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(credential)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateCredentialWithPatch() throws Exception {
        // Initialize the database
        insertedCredential = credentialRepository.saveAndFlush(credential);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the credential using partial update
        Credential partialUpdatedCredential = new Credential();
        partialUpdatedCredential.setId(credential.getId());

        partialUpdatedCredential.label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);

        restCredentialMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCredential.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCredential))
            )
            .andExpect(status().isOk());

        // Validate the Credential in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCredentialUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedCredential, credential),
            getPersistedCredential(credential)
        );
    }

    @Test
    @Transactional
    void fullUpdateCredentialWithPatch() throws Exception {
        // Initialize the database
        insertedCredential = credentialRepository.saveAndFlush(credential);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the credential using partial update
        Credential partialUpdatedCredential = new Credential();
        partialUpdatedCredential.setId(credential.getId());

        partialUpdatedCredential.label(UPDATED_LABEL).sortOrder(UPDATED_SORT_ORDER);

        restCredentialMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCredential.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCredential))
            )
            .andExpect(status().isOk());

        // Validate the Credential in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCredentialUpdatableFieldsEquals(partialUpdatedCredential, getPersistedCredential(partialUpdatedCredential));
    }

    @Test
    @Transactional
    void patchNonExistingCredential() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        credential.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCredentialMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, credential.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(credential))
            )
            .andExpect(status().isBadRequest());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchCredential() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        credential.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCredentialMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(credential))
            )
            .andExpect(status().isBadRequest());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamCredential() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        credential.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCredentialMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(credential)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Credential in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteCredential() throws Exception {
        // Initialize the database
        insertedCredential = credentialRepository.saveAndFlush(credential);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the credential
        restCredentialMockMvc
            .perform(delete(ENTITY_API_URL_ID, credential.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return credentialRepository.count();
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

    protected Credential getPersistedCredential(Credential credential) {
        return credentialRepository.findById(credential.getId()).orElseThrow();
    }

    protected void assertPersistedCredentialToMatchAllProperties(Credential expectedCredential) {
        assertCredentialAllPropertiesEquals(expectedCredential, getPersistedCredential(expectedCredential));
    }

    protected void assertPersistedCredentialToMatchUpdatableProperties(Credential expectedCredential) {
        assertCredentialAllUpdatablePropertiesEquals(expectedCredential, getPersistedCredential(expectedCredential));
    }
}
