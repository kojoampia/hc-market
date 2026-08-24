package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.BookingStatusChangeAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.repository.BookingStatusChangeRepository;
import net.jojoaddison.service.dto.BookingStatusChangeDTO;
import net.jojoaddison.service.mapper.BookingStatusChangeMapper;
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
 * Integration tests for the {@link BookingStatusChangeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class BookingStatusChangeResourceIT {

    private static final BookingStatus DEFAULT_FROM_STATUS = BookingStatus.REQUESTED;
    private static final BookingStatus UPDATED_FROM_STATUS = BookingStatus.DECLINED;

    private static final BookingStatus DEFAULT_TO_STATUS = BookingStatus.REQUESTED;
    private static final BookingStatus UPDATED_TO_STATUS = BookingStatus.DECLINED;

    private static final String DEFAULT_ACTOR = "AAAAAAAAAA";
    private static final String UPDATED_ACTOR = "BBBBBBBBBB";

    private static final Instant DEFAULT_OCCURRED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_OCCURRED_AT = Instant.ofEpochMilli(1702119789970L);

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/booking-status-changes";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingStatusChangeRepository bookingStatusChangeRepository;

    @Autowired
    private BookingStatusChangeMapper bookingStatusChangeMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restBookingStatusChangeMockMvc;

    private BookingStatusChange bookingStatusChange;

    private BookingStatusChange insertedBookingStatusChange;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static BookingStatusChange createEntity(EntityManager em) {
        BookingStatusChange bookingStatusChange = new BookingStatusChange()
            .fromStatus(DEFAULT_FROM_STATUS)
            .toStatus(DEFAULT_TO_STATUS)
            .actor(DEFAULT_ACTOR)
            .occurredAt(DEFAULT_OCCURRED_AT)
            .note(DEFAULT_NOTE);
        // Add required entity
        Booking booking;
        if (TestUtil.findAll(em, Booking.class).isEmpty()) {
            booking = BookingResourceIT.createEntity();
            em.persist(booking);
            em.flush();
        } else {
            booking = TestUtil.findAll(em, Booking.class).get(0);
        }
        bookingStatusChange.setBooking(booking);
        return bookingStatusChange;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static BookingStatusChange createUpdatedEntity(EntityManager em) {
        BookingStatusChange updatedBookingStatusChange = new BookingStatusChange()
            .fromStatus(UPDATED_FROM_STATUS)
            .toStatus(UPDATED_TO_STATUS)
            .actor(UPDATED_ACTOR)
            .occurredAt(UPDATED_OCCURRED_AT)
            .note(UPDATED_NOTE);
        // Add required entity
        Booking booking;
        if (TestUtil.findAll(em, Booking.class).isEmpty()) {
            booking = BookingResourceIT.createUpdatedEntity();
            em.persist(booking);
            em.flush();
        } else {
            booking = TestUtil.findAll(em, Booking.class).get(0);
        }
        updatedBookingStatusChange.setBooking(booking);
        return updatedBookingStatusChange;
    }

    @BeforeEach
    void initTest() {
        bookingStatusChange = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedBookingStatusChange != null) {
            bookingStatusChangeRepository.delete(insertedBookingStatusChange);
            insertedBookingStatusChange = null;
        }
    }

    @Test
    @Transactional
    void createBookingStatusChange() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);
        var returnedBookingStatusChangeDTO = om.readValue(
            restBookingStatusChangeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingStatusChangeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BookingStatusChangeDTO.class
        );

        // Validate the BookingStatusChange in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedBookingStatusChange = bookingStatusChangeMapper.toEntity(returnedBookingStatusChangeDTO);
        assertBookingStatusChangeUpdatableFieldsEquals(
            returnedBookingStatusChange,
            getPersistedBookingStatusChange(returnedBookingStatusChange)
        );

        insertedBookingStatusChange = returnedBookingStatusChange;
    }

    @Test
    @Transactional
    void createBookingStatusChangeWithExistingId() throws Exception {
        // Create the BookingStatusChange with an existing ID
        bookingStatusChange.setId(1L);
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restBookingStatusChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingStatusChangeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkToStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bookingStatusChange.setToStatus(null);

        // Create the BookingStatusChange, which fails.
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        restBookingStatusChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingStatusChangeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bookingStatusChange.setActor(null);

        // Create the BookingStatusChange, which fails.
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        restBookingStatusChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingStatusChangeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkOccurredAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        bookingStatusChange.setOccurredAt(null);

        // Create the BookingStatusChange, which fails.
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        restBookingStatusChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingStatusChangeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllBookingStatusChanges() throws Exception {
        // Initialize the database
        insertedBookingStatusChange = bookingStatusChangeRepository.saveAndFlush(bookingStatusChange);

        // Get all the bookingStatusChangeList
        restBookingStatusChangeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(bookingStatusChange.getId().intValue())))
            .andExpect(jsonPath("$.[*].fromStatus").value(hasItem(DEFAULT_FROM_STATUS.toString())))
            .andExpect(jsonPath("$.[*].toStatus").value(hasItem(DEFAULT_TO_STATUS.toString())))
            .andExpect(jsonPath("$.[*].actor").value(hasItem(DEFAULT_ACTOR)))
            .andExpect(jsonPath("$.[*].occurredAt").value(hasItem(DEFAULT_OCCURRED_AT.toString())))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)));
    }

    @Test
    @Transactional
    void getBookingStatusChange() throws Exception {
        // Initialize the database
        insertedBookingStatusChange = bookingStatusChangeRepository.saveAndFlush(bookingStatusChange);

        // Get the bookingStatusChange
        restBookingStatusChangeMockMvc
            .perform(get(ENTITY_API_URL_ID, bookingStatusChange.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(bookingStatusChange.getId().intValue()))
            .andExpect(jsonPath("$.fromStatus").value(DEFAULT_FROM_STATUS.toString()))
            .andExpect(jsonPath("$.toStatus").value(DEFAULT_TO_STATUS.toString()))
            .andExpect(jsonPath("$.actor").value(DEFAULT_ACTOR))
            .andExpect(jsonPath("$.occurredAt").value(DEFAULT_OCCURRED_AT.toString()))
            .andExpect(jsonPath("$.note").value(DEFAULT_NOTE));
    }

    @Test
    @Transactional
    void getNonExistingBookingStatusChange() throws Exception {
        // Get the bookingStatusChange
        restBookingStatusChangeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingBookingStatusChange() throws Exception {
        // Initialize the database
        insertedBookingStatusChange = bookingStatusChangeRepository.saveAndFlush(bookingStatusChange);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the bookingStatusChange
        BookingStatusChange updatedBookingStatusChange = bookingStatusChangeRepository.findById(bookingStatusChange.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedBookingStatusChange are not directly saved in db
        em.detach(updatedBookingStatusChange);
        updatedBookingStatusChange
            .fromStatus(UPDATED_FROM_STATUS)
            .toStatus(UPDATED_TO_STATUS)
            .actor(UPDATED_ACTOR)
            .occurredAt(UPDATED_OCCURRED_AT)
            .note(UPDATED_NOTE);
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(updatedBookingStatusChange);

        restBookingStatusChangeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, bookingStatusChangeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bookingStatusChangeDTO))
            )
            .andExpect(status().isOk());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedBookingStatusChangeToMatchAllProperties(updatedBookingStatusChange);
    }

    @Test
    @Transactional
    void putNonExistingBookingStatusChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bookingStatusChange.setId(longCount.incrementAndGet());

        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBookingStatusChangeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, bookingStatusChangeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bookingStatusChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchBookingStatusChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bookingStatusChange.setId(longCount.incrementAndGet());

        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingStatusChangeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bookingStatusChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamBookingStatusChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bookingStatusChange.setId(longCount.incrementAndGet());

        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingStatusChangeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingStatusChangeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateBookingStatusChangeWithPatch() throws Exception {
        // Initialize the database
        insertedBookingStatusChange = bookingStatusChangeRepository.saveAndFlush(bookingStatusChange);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the bookingStatusChange using partial update
        BookingStatusChange partialUpdatedBookingStatusChange = new BookingStatusChange();
        partialUpdatedBookingStatusChange.setId(bookingStatusChange.getId());

        partialUpdatedBookingStatusChange.toStatus(UPDATED_TO_STATUS).occurredAt(UPDATED_OCCURRED_AT);

        restBookingStatusChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBookingStatusChange.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBookingStatusChange))
            )
            .andExpect(status().isOk());

        // Validate the BookingStatusChange in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBookingStatusChangeUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedBookingStatusChange, bookingStatusChange),
            getPersistedBookingStatusChange(bookingStatusChange)
        );
    }

    @Test
    @Transactional
    void fullUpdateBookingStatusChangeWithPatch() throws Exception {
        // Initialize the database
        insertedBookingStatusChange = bookingStatusChangeRepository.saveAndFlush(bookingStatusChange);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the bookingStatusChange using partial update
        BookingStatusChange partialUpdatedBookingStatusChange = new BookingStatusChange();
        partialUpdatedBookingStatusChange.setId(bookingStatusChange.getId());

        partialUpdatedBookingStatusChange
            .fromStatus(UPDATED_FROM_STATUS)
            .toStatus(UPDATED_TO_STATUS)
            .actor(UPDATED_ACTOR)
            .occurredAt(UPDATED_OCCURRED_AT)
            .note(UPDATED_NOTE);

        restBookingStatusChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBookingStatusChange.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBookingStatusChange))
            )
            .andExpect(status().isOk());

        // Validate the BookingStatusChange in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBookingStatusChangeUpdatableFieldsEquals(
            partialUpdatedBookingStatusChange,
            getPersistedBookingStatusChange(partialUpdatedBookingStatusChange)
        );
    }

    @Test
    @Transactional
    void patchNonExistingBookingStatusChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bookingStatusChange.setId(longCount.incrementAndGet());

        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBookingStatusChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, bookingStatusChangeDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(bookingStatusChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchBookingStatusChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bookingStatusChange.setId(longCount.incrementAndGet());

        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingStatusChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(bookingStatusChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamBookingStatusChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        bookingStatusChange.setId(longCount.incrementAndGet());

        // Create the BookingStatusChange
        BookingStatusChangeDTO bookingStatusChangeDTO = bookingStatusChangeMapper.toDto(bookingStatusChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingStatusChangeMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(bookingStatusChangeDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the BookingStatusChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteBookingStatusChange() throws Exception {
        // Initialize the database
        insertedBookingStatusChange = bookingStatusChangeRepository.saveAndFlush(bookingStatusChange);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the bookingStatusChange
        restBookingStatusChangeMockMvc
            .perform(delete(ENTITY_API_URL_ID, bookingStatusChange.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return bookingStatusChangeRepository.count();
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

    protected BookingStatusChange getPersistedBookingStatusChange(BookingStatusChange bookingStatusChange) {
        return bookingStatusChangeRepository.findById(bookingStatusChange.getId()).orElseThrow();
    }

    protected void assertPersistedBookingStatusChangeToMatchAllProperties(BookingStatusChange expectedBookingStatusChange) {
        assertBookingStatusChangeAllPropertiesEquals(
            expectedBookingStatusChange,
            getPersistedBookingStatusChange(expectedBookingStatusChange)
        );
    }

    protected void assertPersistedBookingStatusChangeToMatchUpdatableProperties(BookingStatusChange expectedBookingStatusChange) {
        assertBookingStatusChangeAllUpdatablePropertiesEquals(
            expectedBookingStatusChange,
            getPersistedBookingStatusChange(expectedBookingStatusChange)
        );
    }
}
