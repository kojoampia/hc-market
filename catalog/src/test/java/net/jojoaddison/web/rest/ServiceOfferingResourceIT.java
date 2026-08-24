package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ServiceOfferingAsserts.*;
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
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.repository.ServiceOfferingRepository;
import net.jojoaddison.service.dto.ServiceOfferingDTO;
import net.jojoaddison.service.mapper.ServiceOfferingMapper;
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
 * Integration tests for the {@link ServiceOfferingResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ServiceOfferingResourceIT {

    private static final String DEFAULT_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_REFERENCE = "BBBBBBBBBB";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final Integer DEFAULT_DURATION_MINUTES = 0;
    private static final Integer UPDATED_DURATION_MINUTES = 1;

    private static final Long DEFAULT_PRICE_MINOR = 0L;
    private static final Long UPDATED_PRICE_MINOR = 1L;

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final Integer DEFAULT_SORT_ORDER = 1;
    private static final Integer UPDATED_SORT_ORDER = 2;

    private static final String ENTITY_API_URL = "/api/service-offerings";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    @Autowired
    private ServiceOfferingMapper serviceOfferingMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restServiceOfferingMockMvc;

    private ServiceOffering serviceOffering;

    private ServiceOffering insertedServiceOffering;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServiceOffering createEntity(EntityManager em) {
        ServiceOffering serviceOffering = new ServiceOffering()
            .reference(DEFAULT_REFERENCE)
            .name(DEFAULT_NAME)
            .durationMinutes(DEFAULT_DURATION_MINUTES)
            .priceMinor(DEFAULT_PRICE_MINOR)
            .currency(DEFAULT_CURRENCY)
            .description(DEFAULT_DESCRIPTION)
            .active(DEFAULT_ACTIVE)
            .sortOrder(DEFAULT_SORT_ORDER);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        serviceOffering.setProfessional(professional);
        return serviceOffering;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServiceOffering createUpdatedEntity(EntityManager em) {
        ServiceOffering updatedServiceOffering = new ServiceOffering()
            .reference(UPDATED_REFERENCE)
            .name(UPDATED_NAME)
            .durationMinutes(UPDATED_DURATION_MINUTES)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .description(UPDATED_DESCRIPTION)
            .active(UPDATED_ACTIVE)
            .sortOrder(UPDATED_SORT_ORDER);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createUpdatedEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        updatedServiceOffering.setProfessional(professional);
        return updatedServiceOffering;
    }

    @BeforeEach
    void initTest() {
        serviceOffering = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedServiceOffering != null) {
            serviceOfferingRepository.delete(insertedServiceOffering);
            insertedServiceOffering = null;
        }
    }

    @Test
    @Transactional
    void createServiceOffering() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);
        var returnedServiceOfferingDTO = om.readValue(
            restServiceOfferingMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ServiceOfferingDTO.class
        );

        // Validate the ServiceOffering in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedServiceOffering = serviceOfferingMapper.toEntity(returnedServiceOfferingDTO);
        assertServiceOfferingUpdatableFieldsEquals(returnedServiceOffering, getPersistedServiceOffering(returnedServiceOffering));

        insertedServiceOffering = returnedServiceOffering;
    }

    @Test
    @Transactional
    void createServiceOfferingWithExistingId() throws Exception {
        // Create the ServiceOffering with an existing ID
        serviceOffering.setId(1L);
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restServiceOfferingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkReferenceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceOffering.setReference(null);

        // Create the ServiceOffering, which fails.
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        restServiceOfferingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceOffering.setName(null);

        // Create the ServiceOffering, which fails.
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        restServiceOfferingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPriceMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceOffering.setPriceMinor(null);

        // Create the ServiceOffering, which fails.
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        restServiceOfferingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceOffering.setCurrency(null);

        // Create the ServiceOffering, which fails.
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        restServiceOfferingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceOffering.setActive(null);

        // Create the ServiceOffering, which fails.
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        restServiceOfferingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllServiceOfferings() throws Exception {
        // Initialize the database
        insertedServiceOffering = serviceOfferingRepository.saveAndFlush(serviceOffering);

        // Get all the serviceOfferingList
        restServiceOfferingMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(serviceOffering.getId().intValue())))
            .andExpect(jsonPath("$.[*].reference").value(hasItem(DEFAULT_REFERENCE)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].durationMinutes").value(hasItem(DEFAULT_DURATION_MINUTES)))
            .andExpect(jsonPath("$.[*].priceMinor").value(hasItem(DEFAULT_PRICE_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)))
            .andExpect(jsonPath("$.[*].sortOrder").value(hasItem(DEFAULT_SORT_ORDER)));
    }

    @Test
    @Transactional
    void getServiceOffering() throws Exception {
        // Initialize the database
        insertedServiceOffering = serviceOfferingRepository.saveAndFlush(serviceOffering);

        // Get the serviceOffering
        restServiceOfferingMockMvc
            .perform(get(ENTITY_API_URL_ID, serviceOffering.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(serviceOffering.getId().intValue()))
            .andExpect(jsonPath("$.reference").value(DEFAULT_REFERENCE))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.durationMinutes").value(DEFAULT_DURATION_MINUTES))
            .andExpect(jsonPath("$.priceMinor").value(DEFAULT_PRICE_MINOR.intValue()))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE))
            .andExpect(jsonPath("$.sortOrder").value(DEFAULT_SORT_ORDER));
    }

    @Test
    @Transactional
    void getNonExistingServiceOffering() throws Exception {
        // Get the serviceOffering
        restServiceOfferingMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingServiceOffering() throws Exception {
        // Initialize the database
        insertedServiceOffering = serviceOfferingRepository.saveAndFlush(serviceOffering);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceOffering
        ServiceOffering updatedServiceOffering = serviceOfferingRepository.findById(serviceOffering.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedServiceOffering are not directly saved in db
        em.detach(updatedServiceOffering);
        updatedServiceOffering
            .reference(UPDATED_REFERENCE)
            .name(UPDATED_NAME)
            .durationMinutes(UPDATED_DURATION_MINUTES)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .description(UPDATED_DESCRIPTION)
            .active(UPDATED_ACTIVE)
            .sortOrder(UPDATED_SORT_ORDER);
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(updatedServiceOffering);

        restServiceOfferingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, serviceOfferingDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceOfferingDTO))
            )
            .andExpect(status().isOk());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedServiceOfferingToMatchAllProperties(updatedServiceOffering);
    }

    @Test
    @Transactional
    void putNonExistingServiceOffering() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceOffering.setId(longCount.incrementAndGet());

        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServiceOfferingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, serviceOfferingDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceOfferingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchServiceOffering() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceOffering.setId(longCount.incrementAndGet());

        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceOfferingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceOfferingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamServiceOffering() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceOffering.setId(longCount.incrementAndGet());

        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceOfferingMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateServiceOfferingWithPatch() throws Exception {
        // Initialize the database
        insertedServiceOffering = serviceOfferingRepository.saveAndFlush(serviceOffering);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceOffering using partial update
        ServiceOffering partialUpdatedServiceOffering = new ServiceOffering();
        partialUpdatedServiceOffering.setId(serviceOffering.getId());

        partialUpdatedServiceOffering
            .reference(UPDATED_REFERENCE)
            .durationMinutes(UPDATED_DURATION_MINUTES)
            .priceMinor(UPDATED_PRICE_MINOR)
            .description(UPDATED_DESCRIPTION)
            .active(UPDATED_ACTIVE);

        restServiceOfferingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServiceOffering.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServiceOffering))
            )
            .andExpect(status().isOk());

        // Validate the ServiceOffering in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServiceOfferingUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedServiceOffering, serviceOffering),
            getPersistedServiceOffering(serviceOffering)
        );
    }

    @Test
    @Transactional
    void fullUpdateServiceOfferingWithPatch() throws Exception {
        // Initialize the database
        insertedServiceOffering = serviceOfferingRepository.saveAndFlush(serviceOffering);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceOffering using partial update
        ServiceOffering partialUpdatedServiceOffering = new ServiceOffering();
        partialUpdatedServiceOffering.setId(serviceOffering.getId());

        partialUpdatedServiceOffering
            .reference(UPDATED_REFERENCE)
            .name(UPDATED_NAME)
            .durationMinutes(UPDATED_DURATION_MINUTES)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .description(UPDATED_DESCRIPTION)
            .active(UPDATED_ACTIVE)
            .sortOrder(UPDATED_SORT_ORDER);

        restServiceOfferingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServiceOffering.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServiceOffering))
            )
            .andExpect(status().isOk());

        // Validate the ServiceOffering in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServiceOfferingUpdatableFieldsEquals(
            partialUpdatedServiceOffering,
            getPersistedServiceOffering(partialUpdatedServiceOffering)
        );
    }

    @Test
    @Transactional
    void patchNonExistingServiceOffering() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceOffering.setId(longCount.incrementAndGet());

        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServiceOfferingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, serviceOfferingDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(serviceOfferingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchServiceOffering() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceOffering.setId(longCount.incrementAndGet());

        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceOfferingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(serviceOfferingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamServiceOffering() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceOffering.setId(longCount.incrementAndGet());

        // Create the ServiceOffering
        ServiceOfferingDTO serviceOfferingDTO = serviceOfferingMapper.toDto(serviceOffering);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceOfferingMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(serviceOfferingDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServiceOffering in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteServiceOffering() throws Exception {
        // Initialize the database
        insertedServiceOffering = serviceOfferingRepository.saveAndFlush(serviceOffering);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the serviceOffering
        restServiceOfferingMockMvc
            .perform(delete(ENTITY_API_URL_ID, serviceOffering.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return serviceOfferingRepository.count();
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

    protected ServiceOffering getPersistedServiceOffering(ServiceOffering serviceOffering) {
        return serviceOfferingRepository.findById(serviceOffering.getId()).orElseThrow();
    }

    protected void assertPersistedServiceOfferingToMatchAllProperties(ServiceOffering expectedServiceOffering) {
        assertServiceOfferingAllPropertiesEquals(expectedServiceOffering, getPersistedServiceOffering(expectedServiceOffering));
    }

    protected void assertPersistedServiceOfferingToMatchUpdatableProperties(ServiceOffering expectedServiceOffering) {
        assertServiceOfferingAllUpdatablePropertiesEquals(expectedServiceOffering, getPersistedServiceOffering(expectedServiceOffering));
    }
}
