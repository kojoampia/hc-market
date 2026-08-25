package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.AvailabilitySlotAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.AvailabilitySlotRepository;
import net.jojoaddison.service.dto.AvailabilitySlotDTO;
import net.jojoaddison.service.mapper.AvailabilitySlotMapper;
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
 * Integration tests for the {@link AvailabilitySlotResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class AvailabilitySlotResourceIT {

    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final LocalDate DEFAULT_SLOT_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_SLOT_DATE = LocalDate.parse("2023-12-05");

    private static final LocalTime DEFAULT_SLOT_TIME = LocalTime.NOON;
    private static final LocalTime UPDATED_SLOT_TIME = LocalTime.MAX.withNano(0);

    private static final Boolean DEFAULT_TAKEN = false;
    private static final Boolean UPDATED_TAKEN = true;

    private static final String ENTITY_API_URL = "/api/availability-slots";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private AvailabilitySlotMapper availabilitySlotMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restAvailabilitySlotMockMvc;

    private AvailabilitySlot availabilitySlot;

    private AvailabilitySlot insertedAvailabilitySlot;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static AvailabilitySlot createEntity(EntityManager em) {
        AvailabilitySlot availabilitySlot = new AvailabilitySlot()
            .slotDate(DEFAULT_SLOT_DATE)
            .slotTime(DEFAULT_SLOT_TIME)
            .taken(DEFAULT_TAKEN);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        availabilitySlot.setProfessional(professional);
        return availabilitySlot;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static AvailabilitySlot createUpdatedEntity(EntityManager em) {
        AvailabilitySlot updatedAvailabilitySlot = new AvailabilitySlot()
            .slotDate(UPDATED_SLOT_DATE)
            .slotTime(UPDATED_SLOT_TIME)
            .taken(UPDATED_TAKEN);
        // Add required entity
        Professional professional;
        if (TestUtil.findAll(em, Professional.class).isEmpty()) {
            professional = ProfessionalResourceIT.createUpdatedEntity(em);
            em.persist(professional);
            em.flush();
        } else {
            professional = TestUtil.findAll(em, Professional.class).get(0);
        }
        updatedAvailabilitySlot.setProfessional(professional);
        return updatedAvailabilitySlot;
    }

    @BeforeEach
    void initTest() {
        availabilitySlot = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedAvailabilitySlot != null) {
            availabilitySlotRepository.delete(insertedAvailabilitySlot);
            insertedAvailabilitySlot = null;
        }
    }

    @Test
    @Transactional
    void createAvailabilitySlot() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);
        var returnedAvailabilitySlotDTO = om.readValue(
            restAvailabilitySlotMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(availabilitySlotDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            AvailabilitySlotDTO.class
        );

        // Validate the AvailabilitySlot in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedAvailabilitySlot = availabilitySlotMapper.toEntity(returnedAvailabilitySlotDTO);
        assertAvailabilitySlotUpdatableFieldsEquals(returnedAvailabilitySlot, getPersistedAvailabilitySlot(returnedAvailabilitySlot));

        insertedAvailabilitySlot = returnedAvailabilitySlot;
    }

    @Test
    @Transactional
    void createAvailabilitySlotWithExistingId() throws Exception {
        // Create the AvailabilitySlot with an existing ID
        availabilitySlot.setId(1L);
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restAvailabilitySlotMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(availabilitySlotDTO)))
            .andExpect(status().isBadRequest());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkSlotDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        availabilitySlot.setSlotDate(null);

        // Create the AvailabilitySlot, which fails.
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        restAvailabilitySlotMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(availabilitySlotDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSlotTimeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        availabilitySlot.setSlotTime(null);

        // Create the AvailabilitySlot, which fails.
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        restAvailabilitySlotMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(availabilitySlotDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTakenIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        availabilitySlot.setTaken(null);

        // Create the AvailabilitySlot, which fails.
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        restAvailabilitySlotMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(availabilitySlotDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllAvailabilitySlots() throws Exception {
        // Initialize the database
        insertedAvailabilitySlot = availabilitySlotRepository.saveAndFlush(availabilitySlot);

        // Get all the availabilitySlotList
        restAvailabilitySlotMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(availabilitySlot.getId().intValue())))
            .andExpect(jsonPath("$.[*].slotDate").value(hasItem(DEFAULT_SLOT_DATE.toString())))
            .andExpect(jsonPath("$.[*].slotTime").value(hasItem(DEFAULT_SLOT_TIME.format(LOCAL_DATE_TIME_FORMAT))))
            .andExpect(jsonPath("$.[*].taken").value(hasItem(DEFAULT_TAKEN)));
    }

    @Test
    @Transactional
    void getAvailabilitySlot() throws Exception {
        // Initialize the database
        insertedAvailabilitySlot = availabilitySlotRepository.saveAndFlush(availabilitySlot);

        // Get the availabilitySlot
        restAvailabilitySlotMockMvc
            .perform(get(ENTITY_API_URL_ID, availabilitySlot.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(availabilitySlot.getId().intValue()))
            .andExpect(jsonPath("$.slotDate").value(DEFAULT_SLOT_DATE.toString()))
            .andExpect(jsonPath("$.slotTime").value(DEFAULT_SLOT_TIME.format(LOCAL_DATE_TIME_FORMAT)))
            .andExpect(jsonPath("$.taken").value(DEFAULT_TAKEN));
    }

    @Test
    @Transactional
    void getNonExistingAvailabilitySlot() throws Exception {
        // Get the availabilitySlot
        restAvailabilitySlotMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingAvailabilitySlot() throws Exception {
        // Initialize the database
        insertedAvailabilitySlot = availabilitySlotRepository.saveAndFlush(availabilitySlot);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the availabilitySlot
        AvailabilitySlot updatedAvailabilitySlot = availabilitySlotRepository.findById(availabilitySlot.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedAvailabilitySlot are not directly saved in db
        em.detach(updatedAvailabilitySlot);
        updatedAvailabilitySlot.slotDate(UPDATED_SLOT_DATE).slotTime(UPDATED_SLOT_TIME).taken(UPDATED_TAKEN);
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(updatedAvailabilitySlot);

        restAvailabilitySlotMockMvc
            .perform(
                put(ENTITY_API_URL_ID, availabilitySlotDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(availabilitySlotDTO))
            )
            .andExpect(status().isOk());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedAvailabilitySlotToMatchAllProperties(updatedAvailabilitySlot);
    }

    @Test
    @Transactional
    void putNonExistingAvailabilitySlot() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        availabilitySlot.setId(longCount.incrementAndGet());

        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAvailabilitySlotMockMvc
            .perform(
                put(ENTITY_API_URL_ID, availabilitySlotDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(availabilitySlotDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchAvailabilitySlot() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        availabilitySlot.setId(longCount.incrementAndGet());

        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAvailabilitySlotMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(availabilitySlotDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamAvailabilitySlot() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        availabilitySlot.setId(longCount.incrementAndGet());

        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAvailabilitySlotMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(availabilitySlotDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateAvailabilitySlotWithPatch() throws Exception {
        // Initialize the database
        insertedAvailabilitySlot = availabilitySlotRepository.saveAndFlush(availabilitySlot);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the availabilitySlot using partial update
        AvailabilitySlot partialUpdatedAvailabilitySlot = new AvailabilitySlot();
        partialUpdatedAvailabilitySlot.setId(availabilitySlot.getId());

        restAvailabilitySlotMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAvailabilitySlot.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAvailabilitySlot))
            )
            .andExpect(status().isOk());

        // Validate the AvailabilitySlot in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAvailabilitySlotUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedAvailabilitySlot, availabilitySlot),
            getPersistedAvailabilitySlot(availabilitySlot)
        );
    }

    @Test
    @Transactional
    void fullUpdateAvailabilitySlotWithPatch() throws Exception {
        // Initialize the database
        insertedAvailabilitySlot = availabilitySlotRepository.saveAndFlush(availabilitySlot);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the availabilitySlot using partial update
        AvailabilitySlot partialUpdatedAvailabilitySlot = new AvailabilitySlot();
        partialUpdatedAvailabilitySlot.setId(availabilitySlot.getId());

        partialUpdatedAvailabilitySlot.slotDate(UPDATED_SLOT_DATE).slotTime(UPDATED_SLOT_TIME).taken(UPDATED_TAKEN);

        restAvailabilitySlotMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAvailabilitySlot.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAvailabilitySlot))
            )
            .andExpect(status().isOk());

        // Validate the AvailabilitySlot in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAvailabilitySlotUpdatableFieldsEquals(
            partialUpdatedAvailabilitySlot,
            getPersistedAvailabilitySlot(partialUpdatedAvailabilitySlot)
        );
    }

    @Test
    @Transactional
    void patchNonExistingAvailabilitySlot() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        availabilitySlot.setId(longCount.incrementAndGet());

        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAvailabilitySlotMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, availabilitySlotDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(availabilitySlotDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchAvailabilitySlot() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        availabilitySlot.setId(longCount.incrementAndGet());

        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAvailabilitySlotMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(availabilitySlotDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamAvailabilitySlot() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        availabilitySlot.setId(longCount.incrementAndGet());

        // Create the AvailabilitySlot
        AvailabilitySlotDTO availabilitySlotDTO = availabilitySlotMapper.toDto(availabilitySlot);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAvailabilitySlotMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(availabilitySlotDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the AvailabilitySlot in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteAvailabilitySlot() throws Exception {
        // Initialize the database
        insertedAvailabilitySlot = availabilitySlotRepository.saveAndFlush(availabilitySlot);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the availabilitySlot
        restAvailabilitySlotMockMvc
            .perform(delete(ENTITY_API_URL_ID, availabilitySlot.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return availabilitySlotRepository.count();
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

    protected AvailabilitySlot getPersistedAvailabilitySlot(AvailabilitySlot availabilitySlot) {
        return availabilitySlotRepository.findById(availabilitySlot.getId()).orElseThrow();
    }

    protected void assertPersistedAvailabilitySlotToMatchAllProperties(AvailabilitySlot expectedAvailabilitySlot) {
        assertAvailabilitySlotAllPropertiesEquals(expectedAvailabilitySlot, getPersistedAvailabilitySlot(expectedAvailabilitySlot));
    }

    protected void assertPersistedAvailabilitySlotToMatchUpdatableProperties(AvailabilitySlot expectedAvailabilitySlot) {
        assertAvailabilitySlotAllUpdatablePropertiesEquals(
            expectedAvailabilitySlot,
            getPersistedAvailabilitySlot(expectedAvailabilitySlot)
        );
    }
}
