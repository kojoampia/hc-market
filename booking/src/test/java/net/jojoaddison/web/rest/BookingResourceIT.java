package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.BookingAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.service.dto.BookingDTO;
import net.jojoaddison.service.mapper.BookingMapper;
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
 * Integration tests for the {@link BookingResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class BookingResourceIT {

    private static final String DEFAULT_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_REFERENCE = "BBBBBBBBBB";

    private static final String DEFAULT_CUSTOMER_LOGIN = "AAAAAAAAAA";
    private static final String UPDATED_CUSTOMER_LOGIN = "BBBBBBBBBB";

    private static final String DEFAULT_CUSTOMER_NAME = "AAAAAAAAAA";
    private static final String UPDATED_CUSTOMER_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_PROFESSIONAL_REF = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_REF = "BBBBBBBBBB";

    private static final String DEFAULT_SERVICE_REF = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_REF = "BBBBBBBBBB";

    private static final String DEFAULT_SERVICE_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_NAME = "BBBBBBBBBB";

    private static final Long DEFAULT_PRICE_MINOR = 0L;
    private static final Long UPDATED_PRICE_MINOR = 1L;
    private static final Long SMALLER_PRICE_MINOR = 0L - 1L;

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final LocalDate DEFAULT_SCHEDULED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_SCHEDULED_DATE = LocalDate.parse("2023-12-09");
    private static final LocalDate SMALLER_SCHEDULED_DATE = LocalDate.ofEpochDay(-1L);

    private static final String DEFAULT_SCHEDULED_TIME = "AAAAA";
    private static final String UPDATED_SCHEDULED_TIME = "BBBBB";

    private static final DeliveryMode DEFAULT_DELIVERY_MODE = DeliveryMode.IN_PERSON;
    private static final DeliveryMode UPDATED_DELIVERY_MODE = DeliveryMode.ONLINE;

    private static final BookingStatus DEFAULT_STATUS = BookingStatus.REQUESTED;
    private static final BookingStatus UPDATED_STATUS = BookingStatus.DECLINED;

    private static final String DEFAULT_CUSTOMER_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_CUSTOMER_NOTE = "BBBBBBBBBB";

    private static final String DEFAULT_ON_BEHALF_OF = "AAAAAAAAAA";
    private static final String UPDATED_ON_BEHALF_OF = "BBBBBBBBBB";

    private static final String DEFAULT_VISIT_ADDRESS = "AAAAAAAAAA";
    private static final String UPDATED_VISIT_ADDRESS = "BBBBBBBBBB";

    private static final Boolean DEFAULT_CARE_SUMMARY_SHARED = false;
    private static final Boolean UPDATED_CARE_SUMMARY_SHARED = true;

    private static final Instant DEFAULT_RAISED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_RAISED_AT = Instant.ofEpochMilli(1702119789970L);

    private static final Instant DEFAULT_RESPONDED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_RESPONDED_AT = Instant.ofEpochMilli(1702119789970L);

    private static final Instant DEFAULT_COMPLETED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_COMPLETED_AT = Instant.ofEpochMilli(1702119789970L);

    private static final Instant DEFAULT_CANCELLED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CANCELLED_AT = Instant.ofEpochMilli(1702119789970L);

    private static final CancelledBy DEFAULT_CANCELLED_BY = CancelledBy.CUSTOMER;
    private static final CancelledBy UPDATED_CANCELLED_BY = CancelledBy.PROFESSIONAL;

    private static final String DEFAULT_CANCELLATION_REASON = "AAAAAAAAAA";
    private static final String UPDATED_CANCELLATION_REASON = "BBBBBBBBBB";

    private static final Boolean DEFAULT_LATE_CANCELLATION = false;
    private static final Boolean UPDATED_LATE_CANCELLATION = true;

    private static final Boolean DEFAULT_REVIEWED = false;
    private static final Boolean UPDATED_REVIEWED = true;

    private static final String ENTITY_API_URL = "/api/bookings";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingMapper bookingMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restBookingMockMvc;

    private Booking booking;

    private Booking insertedBooking;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Booking createEntity() {
        return new Booking()
            .reference(DEFAULT_REFERENCE)
            .customerLogin(DEFAULT_CUSTOMER_LOGIN)
            .customerName(DEFAULT_CUSTOMER_NAME)
            .professionalRef(DEFAULT_PROFESSIONAL_REF)
            .serviceRef(DEFAULT_SERVICE_REF)
            .serviceName(DEFAULT_SERVICE_NAME)
            .priceMinor(DEFAULT_PRICE_MINOR)
            .currency(DEFAULT_CURRENCY)
            .scheduledDate(DEFAULT_SCHEDULED_DATE)
            .scheduledTime(DEFAULT_SCHEDULED_TIME)
            .deliveryMode(DEFAULT_DELIVERY_MODE)
            .status(DEFAULT_STATUS)
            .customerNote(DEFAULT_CUSTOMER_NOTE)
            .onBehalfOf(DEFAULT_ON_BEHALF_OF)
            .visitAddress(DEFAULT_VISIT_ADDRESS)
            .careSummaryShared(DEFAULT_CARE_SUMMARY_SHARED)
            .raisedAt(DEFAULT_RAISED_AT)
            .respondedAt(DEFAULT_RESPONDED_AT)
            .completedAt(DEFAULT_COMPLETED_AT)
            .cancelledAt(DEFAULT_CANCELLED_AT)
            .cancelledBy(DEFAULT_CANCELLED_BY)
            .cancellationReason(DEFAULT_CANCELLATION_REASON)
            .lateCancellation(DEFAULT_LATE_CANCELLATION)
            .reviewed(DEFAULT_REVIEWED);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Booking createUpdatedEntity() {
        return new Booking()
            .reference(UPDATED_REFERENCE)
            .customerLogin(UPDATED_CUSTOMER_LOGIN)
            .customerName(UPDATED_CUSTOMER_NAME)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .serviceRef(UPDATED_SERVICE_REF)
            .serviceName(UPDATED_SERVICE_NAME)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .scheduledDate(UPDATED_SCHEDULED_DATE)
            .scheduledTime(UPDATED_SCHEDULED_TIME)
            .deliveryMode(UPDATED_DELIVERY_MODE)
            .status(UPDATED_STATUS)
            .customerNote(UPDATED_CUSTOMER_NOTE)
            .onBehalfOf(UPDATED_ON_BEHALF_OF)
            .visitAddress(UPDATED_VISIT_ADDRESS)
            .careSummaryShared(UPDATED_CARE_SUMMARY_SHARED)
            .raisedAt(UPDATED_RAISED_AT)
            .respondedAt(UPDATED_RESPONDED_AT)
            .completedAt(UPDATED_COMPLETED_AT)
            .cancelledAt(UPDATED_CANCELLED_AT)
            .cancelledBy(UPDATED_CANCELLED_BY)
            .cancellationReason(UPDATED_CANCELLATION_REASON)
            .lateCancellation(UPDATED_LATE_CANCELLATION)
            .reviewed(UPDATED_REVIEWED);
    }

    @BeforeEach
    void initTest() {
        booking = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedBooking != null) {
            bookingRepository.delete(insertedBooking);
            insertedBooking = null;
        }
    }

    @Test
    @Transactional
    void createBooking() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);
        var returnedBookingDTO = om.readValue(
            restBookingMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BookingDTO.class
        );

        // Validate the Booking in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedBooking = bookingMapper.toEntity(returnedBookingDTO);
        assertBookingUpdatableFieldsEquals(returnedBooking, getPersistedBooking(returnedBooking));

        insertedBooking = returnedBooking;
    }

    @Test
    @Transactional
    void createBookingWithExistingId() throws Exception {
        // Create the Booking with an existing ID
        booking.setId(1L);
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkReferenceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setReference(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCustomerLoginIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setCustomerLogin(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCustomerNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setCustomerName(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkProfessionalRefIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setProfessionalRef(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkServiceRefIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setServiceRef(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkServiceNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setServiceName(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPriceMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setPriceMinor(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setCurrency(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkScheduledDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setScheduledDate(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkScheduledTimeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setScheduledTime(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDeliveryModeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setDeliveryMode(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setStatus(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCareSummarySharedIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setCareSummaryShared(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkRaisedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setRaisedAt(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkReviewedIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        booking.setReviewed(null);

        // Create the Booking, which fails.
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        restBookingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllBookings() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList
        restBookingMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(booking.getId().intValue())))
            .andExpect(jsonPath("$.[*].reference").value(hasItem(DEFAULT_REFERENCE)))
            .andExpect(jsonPath("$.[*].customerLogin").value(hasItem(DEFAULT_CUSTOMER_LOGIN)))
            .andExpect(jsonPath("$.[*].customerName").value(hasItem(DEFAULT_CUSTOMER_NAME)))
            .andExpect(jsonPath("$.[*].professionalRef").value(hasItem(DEFAULT_PROFESSIONAL_REF)))
            .andExpect(jsonPath("$.[*].serviceRef").value(hasItem(DEFAULT_SERVICE_REF)))
            .andExpect(jsonPath("$.[*].serviceName").value(hasItem(DEFAULT_SERVICE_NAME)))
            .andExpect(jsonPath("$.[*].priceMinor").value(hasItem(DEFAULT_PRICE_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].scheduledDate").value(hasItem(DEFAULT_SCHEDULED_DATE.toString())))
            .andExpect(jsonPath("$.[*].scheduledTime").value(hasItem(DEFAULT_SCHEDULED_TIME)))
            .andExpect(jsonPath("$.[*].deliveryMode").value(hasItem(DEFAULT_DELIVERY_MODE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].customerNote").value(hasItem(DEFAULT_CUSTOMER_NOTE)))
            .andExpect(jsonPath("$.[*].onBehalfOf").value(hasItem(DEFAULT_ON_BEHALF_OF)))
            .andExpect(jsonPath("$.[*].visitAddress").value(hasItem(DEFAULT_VISIT_ADDRESS)))
            .andExpect(jsonPath("$.[*].careSummaryShared").value(hasItem(DEFAULT_CARE_SUMMARY_SHARED)))
            .andExpect(jsonPath("$.[*].raisedAt").value(hasItem(DEFAULT_RAISED_AT.toString())))
            .andExpect(jsonPath("$.[*].respondedAt").value(hasItem(DEFAULT_RESPONDED_AT.toString())))
            .andExpect(jsonPath("$.[*].completedAt").value(hasItem(DEFAULT_COMPLETED_AT.toString())))
            .andExpect(jsonPath("$.[*].cancelledAt").value(hasItem(DEFAULT_CANCELLED_AT.toString())))
            .andExpect(jsonPath("$.[*].cancelledBy").value(hasItem(DEFAULT_CANCELLED_BY.toString())))
            .andExpect(jsonPath("$.[*].cancellationReason").value(hasItem(DEFAULT_CANCELLATION_REASON)))
            .andExpect(jsonPath("$.[*].lateCancellation").value(hasItem(DEFAULT_LATE_CANCELLATION)))
            .andExpect(jsonPath("$.[*].reviewed").value(hasItem(DEFAULT_REVIEWED)));
    }

    @Test
    @Transactional
    void getBooking() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get the booking
        restBookingMockMvc
            .perform(get(ENTITY_API_URL_ID, booking.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(booking.getId().intValue()))
            .andExpect(jsonPath("$.reference").value(DEFAULT_REFERENCE))
            .andExpect(jsonPath("$.customerLogin").value(DEFAULT_CUSTOMER_LOGIN))
            .andExpect(jsonPath("$.customerName").value(DEFAULT_CUSTOMER_NAME))
            .andExpect(jsonPath("$.professionalRef").value(DEFAULT_PROFESSIONAL_REF))
            .andExpect(jsonPath("$.serviceRef").value(DEFAULT_SERVICE_REF))
            .andExpect(jsonPath("$.serviceName").value(DEFAULT_SERVICE_NAME))
            .andExpect(jsonPath("$.priceMinor").value(DEFAULT_PRICE_MINOR.intValue()))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.scheduledDate").value(DEFAULT_SCHEDULED_DATE.toString()))
            .andExpect(jsonPath("$.scheduledTime").value(DEFAULT_SCHEDULED_TIME))
            .andExpect(jsonPath("$.deliveryMode").value(DEFAULT_DELIVERY_MODE.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.customerNote").value(DEFAULT_CUSTOMER_NOTE))
            .andExpect(jsonPath("$.onBehalfOf").value(DEFAULT_ON_BEHALF_OF))
            .andExpect(jsonPath("$.visitAddress").value(DEFAULT_VISIT_ADDRESS))
            .andExpect(jsonPath("$.careSummaryShared").value(DEFAULT_CARE_SUMMARY_SHARED))
            .andExpect(jsonPath("$.raisedAt").value(DEFAULT_RAISED_AT.toString()))
            .andExpect(jsonPath("$.respondedAt").value(DEFAULT_RESPONDED_AT.toString()))
            .andExpect(jsonPath("$.completedAt").value(DEFAULT_COMPLETED_AT.toString()))
            .andExpect(jsonPath("$.cancelledAt").value(DEFAULT_CANCELLED_AT.toString()))
            .andExpect(jsonPath("$.cancelledBy").value(DEFAULT_CANCELLED_BY.toString()))
            .andExpect(jsonPath("$.cancellationReason").value(DEFAULT_CANCELLATION_REASON))
            .andExpect(jsonPath("$.lateCancellation").value(DEFAULT_LATE_CANCELLATION))
            .andExpect(jsonPath("$.reviewed").value(DEFAULT_REVIEWED));
    }

    @Test
    @Transactional
    void getBookingsByIdFiltering() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        Long id = booking.getId();

        defaultBookingFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultBookingFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultBookingFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllBookingsByReferenceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reference equals to
        defaultBookingFiltering("reference.equals=" + DEFAULT_REFERENCE, "reference.equals=" + UPDATED_REFERENCE);
    }

    @Test
    @Transactional
    void getAllBookingsByReferenceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reference in
        defaultBookingFiltering("reference.in=" + DEFAULT_REFERENCE + "," + UPDATED_REFERENCE, "reference.in=" + UPDATED_REFERENCE);
    }

    @Test
    @Transactional
    void getAllBookingsByReferenceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reference is not null
        defaultBookingFiltering("reference.specified=true", "reference.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByReferenceContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reference contains
        defaultBookingFiltering("reference.contains=" + DEFAULT_REFERENCE, "reference.contains=" + UPDATED_REFERENCE);
    }

    @Test
    @Transactional
    void getAllBookingsByReferenceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reference does not contain
        defaultBookingFiltering("reference.doesNotContain=" + UPDATED_REFERENCE, "reference.doesNotContain=" + DEFAULT_REFERENCE);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerLoginIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerLogin equals to
        defaultBookingFiltering("customerLogin.equals=" + DEFAULT_CUSTOMER_LOGIN, "customerLogin.equals=" + UPDATED_CUSTOMER_LOGIN);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerLoginIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerLogin in
        defaultBookingFiltering(
            "customerLogin.in=" + DEFAULT_CUSTOMER_LOGIN + "," + UPDATED_CUSTOMER_LOGIN,
            "customerLogin.in=" + UPDATED_CUSTOMER_LOGIN
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerLoginIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerLogin is not null
        defaultBookingFiltering("customerLogin.specified=true", "customerLogin.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerLoginContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerLogin contains
        defaultBookingFiltering("customerLogin.contains=" + DEFAULT_CUSTOMER_LOGIN, "customerLogin.contains=" + UPDATED_CUSTOMER_LOGIN);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerLoginNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerLogin does not contain
        defaultBookingFiltering(
            "customerLogin.doesNotContain=" + UPDATED_CUSTOMER_LOGIN,
            "customerLogin.doesNotContain=" + DEFAULT_CUSTOMER_LOGIN
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerName equals to
        defaultBookingFiltering("customerName.equals=" + DEFAULT_CUSTOMER_NAME, "customerName.equals=" + UPDATED_CUSTOMER_NAME);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerName in
        defaultBookingFiltering(
            "customerName.in=" + DEFAULT_CUSTOMER_NAME + "," + UPDATED_CUSTOMER_NAME,
            "customerName.in=" + UPDATED_CUSTOMER_NAME
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerName is not null
        defaultBookingFiltering("customerName.specified=true", "customerName.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNameContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerName contains
        defaultBookingFiltering("customerName.contains=" + DEFAULT_CUSTOMER_NAME, "customerName.contains=" + UPDATED_CUSTOMER_NAME);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerName does not contain
        defaultBookingFiltering(
            "customerName.doesNotContain=" + UPDATED_CUSTOMER_NAME,
            "customerName.doesNotContain=" + DEFAULT_CUSTOMER_NAME
        );
    }

    @Test
    @Transactional
    void getAllBookingsByProfessionalRefIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where professionalRef equals to
        defaultBookingFiltering("professionalRef.equals=" + DEFAULT_PROFESSIONAL_REF, "professionalRef.equals=" + UPDATED_PROFESSIONAL_REF);
    }

    @Test
    @Transactional
    void getAllBookingsByProfessionalRefIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where professionalRef in
        defaultBookingFiltering(
            "professionalRef.in=" + DEFAULT_PROFESSIONAL_REF + "," + UPDATED_PROFESSIONAL_REF,
            "professionalRef.in=" + UPDATED_PROFESSIONAL_REF
        );
    }

    @Test
    @Transactional
    void getAllBookingsByProfessionalRefIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where professionalRef is not null
        defaultBookingFiltering("professionalRef.specified=true", "professionalRef.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByProfessionalRefContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where professionalRef contains
        defaultBookingFiltering(
            "professionalRef.contains=" + DEFAULT_PROFESSIONAL_REF,
            "professionalRef.contains=" + UPDATED_PROFESSIONAL_REF
        );
    }

    @Test
    @Transactional
    void getAllBookingsByProfessionalRefNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where professionalRef does not contain
        defaultBookingFiltering(
            "professionalRef.doesNotContain=" + UPDATED_PROFESSIONAL_REF,
            "professionalRef.doesNotContain=" + DEFAULT_PROFESSIONAL_REF
        );
    }

    @Test
    @Transactional
    void getAllBookingsByServiceRefIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceRef equals to
        defaultBookingFiltering("serviceRef.equals=" + DEFAULT_SERVICE_REF, "serviceRef.equals=" + UPDATED_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllBookingsByServiceRefIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceRef in
        defaultBookingFiltering("serviceRef.in=" + DEFAULT_SERVICE_REF + "," + UPDATED_SERVICE_REF, "serviceRef.in=" + UPDATED_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllBookingsByServiceRefIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceRef is not null
        defaultBookingFiltering("serviceRef.specified=true", "serviceRef.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByServiceRefContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceRef contains
        defaultBookingFiltering("serviceRef.contains=" + DEFAULT_SERVICE_REF, "serviceRef.contains=" + UPDATED_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllBookingsByServiceRefNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceRef does not contain
        defaultBookingFiltering("serviceRef.doesNotContain=" + UPDATED_SERVICE_REF, "serviceRef.doesNotContain=" + DEFAULT_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllBookingsByServiceNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceName equals to
        defaultBookingFiltering("serviceName.equals=" + DEFAULT_SERVICE_NAME, "serviceName.equals=" + UPDATED_SERVICE_NAME);
    }

    @Test
    @Transactional
    void getAllBookingsByServiceNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceName in
        defaultBookingFiltering(
            "serviceName.in=" + DEFAULT_SERVICE_NAME + "," + UPDATED_SERVICE_NAME,
            "serviceName.in=" + UPDATED_SERVICE_NAME
        );
    }

    @Test
    @Transactional
    void getAllBookingsByServiceNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceName is not null
        defaultBookingFiltering("serviceName.specified=true", "serviceName.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByServiceNameContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceName contains
        defaultBookingFiltering("serviceName.contains=" + DEFAULT_SERVICE_NAME, "serviceName.contains=" + UPDATED_SERVICE_NAME);
    }

    @Test
    @Transactional
    void getAllBookingsByServiceNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where serviceName does not contain
        defaultBookingFiltering("serviceName.doesNotContain=" + UPDATED_SERVICE_NAME, "serviceName.doesNotContain=" + DEFAULT_SERVICE_NAME);
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor equals to
        defaultBookingFiltering("priceMinor.equals=" + DEFAULT_PRICE_MINOR, "priceMinor.equals=" + UPDATED_PRICE_MINOR);
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor in
        defaultBookingFiltering("priceMinor.in=" + DEFAULT_PRICE_MINOR + "," + UPDATED_PRICE_MINOR, "priceMinor.in=" + UPDATED_PRICE_MINOR);
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor is not null
        defaultBookingFiltering("priceMinor.specified=true", "priceMinor.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor is greater than or equal to
        defaultBookingFiltering(
            "priceMinor.greaterThanOrEqual=" + DEFAULT_PRICE_MINOR,
            "priceMinor.greaterThanOrEqual=" + UPDATED_PRICE_MINOR
        );
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor is less than or equal to
        defaultBookingFiltering("priceMinor.lessThanOrEqual=" + DEFAULT_PRICE_MINOR, "priceMinor.lessThanOrEqual=" + SMALLER_PRICE_MINOR);
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor is less than
        defaultBookingFiltering("priceMinor.lessThan=" + UPDATED_PRICE_MINOR, "priceMinor.lessThan=" + DEFAULT_PRICE_MINOR);
    }

    @Test
    @Transactional
    void getAllBookingsByPriceMinorIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where priceMinor is greater than
        defaultBookingFiltering("priceMinor.greaterThan=" + SMALLER_PRICE_MINOR, "priceMinor.greaterThan=" + DEFAULT_PRICE_MINOR);
    }

    @Test
    @Transactional
    void getAllBookingsByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where currency equals to
        defaultBookingFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBookingsByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where currency in
        defaultBookingFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBookingsByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where currency is not null
        defaultBookingFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCurrencyContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where currency contains
        defaultBookingFiltering("currency.contains=" + DEFAULT_CURRENCY, "currency.contains=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBookingsByCurrencyNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where currency does not contain
        defaultBookingFiltering("currency.doesNotContain=" + UPDATED_CURRENCY, "currency.doesNotContain=" + DEFAULT_CURRENCY);
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate equals to
        defaultBookingFiltering("scheduledDate.equals=" + DEFAULT_SCHEDULED_DATE, "scheduledDate.equals=" + UPDATED_SCHEDULED_DATE);
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate in
        defaultBookingFiltering(
            "scheduledDate.in=" + DEFAULT_SCHEDULED_DATE + "," + UPDATED_SCHEDULED_DATE,
            "scheduledDate.in=" + UPDATED_SCHEDULED_DATE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate is not null
        defaultBookingFiltering("scheduledDate.specified=true", "scheduledDate.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate is greater than or equal to
        defaultBookingFiltering(
            "scheduledDate.greaterThanOrEqual=" + DEFAULT_SCHEDULED_DATE,
            "scheduledDate.greaterThanOrEqual=" + UPDATED_SCHEDULED_DATE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate is less than or equal to
        defaultBookingFiltering(
            "scheduledDate.lessThanOrEqual=" + DEFAULT_SCHEDULED_DATE,
            "scheduledDate.lessThanOrEqual=" + SMALLER_SCHEDULED_DATE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate is less than
        defaultBookingFiltering("scheduledDate.lessThan=" + UPDATED_SCHEDULED_DATE, "scheduledDate.lessThan=" + DEFAULT_SCHEDULED_DATE);
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledDate is greater than
        defaultBookingFiltering(
            "scheduledDate.greaterThan=" + SMALLER_SCHEDULED_DATE,
            "scheduledDate.greaterThan=" + DEFAULT_SCHEDULED_DATE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledTimeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledTime equals to
        defaultBookingFiltering("scheduledTime.equals=" + DEFAULT_SCHEDULED_TIME, "scheduledTime.equals=" + UPDATED_SCHEDULED_TIME);
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledTimeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledTime in
        defaultBookingFiltering(
            "scheduledTime.in=" + DEFAULT_SCHEDULED_TIME + "," + UPDATED_SCHEDULED_TIME,
            "scheduledTime.in=" + UPDATED_SCHEDULED_TIME
        );
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledTimeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledTime is not null
        defaultBookingFiltering("scheduledTime.specified=true", "scheduledTime.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledTimeContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledTime contains
        defaultBookingFiltering("scheduledTime.contains=" + DEFAULT_SCHEDULED_TIME, "scheduledTime.contains=" + UPDATED_SCHEDULED_TIME);
    }

    @Test
    @Transactional
    void getAllBookingsByScheduledTimeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where scheduledTime does not contain
        defaultBookingFiltering(
            "scheduledTime.doesNotContain=" + UPDATED_SCHEDULED_TIME,
            "scheduledTime.doesNotContain=" + DEFAULT_SCHEDULED_TIME
        );
    }

    @Test
    @Transactional
    void getAllBookingsByDeliveryModeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where deliveryMode equals to
        defaultBookingFiltering("deliveryMode.equals=" + DEFAULT_DELIVERY_MODE, "deliveryMode.equals=" + UPDATED_DELIVERY_MODE);
    }

    @Test
    @Transactional
    void getAllBookingsByDeliveryModeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where deliveryMode in
        defaultBookingFiltering(
            "deliveryMode.in=" + DEFAULT_DELIVERY_MODE + "," + UPDATED_DELIVERY_MODE,
            "deliveryMode.in=" + UPDATED_DELIVERY_MODE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByDeliveryModeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where deliveryMode is not null
        defaultBookingFiltering("deliveryMode.specified=true", "deliveryMode.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where status equals to
        defaultBookingFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllBookingsByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where status in
        defaultBookingFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllBookingsByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where status is not null
        defaultBookingFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNoteIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerNote equals to
        defaultBookingFiltering("customerNote.equals=" + DEFAULT_CUSTOMER_NOTE, "customerNote.equals=" + UPDATED_CUSTOMER_NOTE);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNoteIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerNote in
        defaultBookingFiltering(
            "customerNote.in=" + DEFAULT_CUSTOMER_NOTE + "," + UPDATED_CUSTOMER_NOTE,
            "customerNote.in=" + UPDATED_CUSTOMER_NOTE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNoteIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerNote is not null
        defaultBookingFiltering("customerNote.specified=true", "customerNote.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNoteContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerNote contains
        defaultBookingFiltering("customerNote.contains=" + DEFAULT_CUSTOMER_NOTE, "customerNote.contains=" + UPDATED_CUSTOMER_NOTE);
    }

    @Test
    @Transactional
    void getAllBookingsByCustomerNoteNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where customerNote does not contain
        defaultBookingFiltering(
            "customerNote.doesNotContain=" + UPDATED_CUSTOMER_NOTE,
            "customerNote.doesNotContain=" + DEFAULT_CUSTOMER_NOTE
        );
    }

    @Test
    @Transactional
    void getAllBookingsByOnBehalfOfIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where onBehalfOf equals to
        defaultBookingFiltering("onBehalfOf.equals=" + DEFAULT_ON_BEHALF_OF, "onBehalfOf.equals=" + UPDATED_ON_BEHALF_OF);
    }

    @Test
    @Transactional
    void getAllBookingsByOnBehalfOfIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where onBehalfOf in
        defaultBookingFiltering(
            "onBehalfOf.in=" + DEFAULT_ON_BEHALF_OF + "," + UPDATED_ON_BEHALF_OF,
            "onBehalfOf.in=" + UPDATED_ON_BEHALF_OF
        );
    }

    @Test
    @Transactional
    void getAllBookingsByOnBehalfOfIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where onBehalfOf is not null
        defaultBookingFiltering("onBehalfOf.specified=true", "onBehalfOf.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByOnBehalfOfContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where onBehalfOf contains
        defaultBookingFiltering("onBehalfOf.contains=" + DEFAULT_ON_BEHALF_OF, "onBehalfOf.contains=" + UPDATED_ON_BEHALF_OF);
    }

    @Test
    @Transactional
    void getAllBookingsByOnBehalfOfNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where onBehalfOf does not contain
        defaultBookingFiltering("onBehalfOf.doesNotContain=" + UPDATED_ON_BEHALF_OF, "onBehalfOf.doesNotContain=" + DEFAULT_ON_BEHALF_OF);
    }

    @Test
    @Transactional
    void getAllBookingsByVisitAddressIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where visitAddress equals to
        defaultBookingFiltering("visitAddress.equals=" + DEFAULT_VISIT_ADDRESS, "visitAddress.equals=" + UPDATED_VISIT_ADDRESS);
    }

    @Test
    @Transactional
    void getAllBookingsByVisitAddressIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where visitAddress in
        defaultBookingFiltering(
            "visitAddress.in=" + DEFAULT_VISIT_ADDRESS + "," + UPDATED_VISIT_ADDRESS,
            "visitAddress.in=" + UPDATED_VISIT_ADDRESS
        );
    }

    @Test
    @Transactional
    void getAllBookingsByVisitAddressIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where visitAddress is not null
        defaultBookingFiltering("visitAddress.specified=true", "visitAddress.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByVisitAddressContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where visitAddress contains
        defaultBookingFiltering("visitAddress.contains=" + DEFAULT_VISIT_ADDRESS, "visitAddress.contains=" + UPDATED_VISIT_ADDRESS);
    }

    @Test
    @Transactional
    void getAllBookingsByVisitAddressNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where visitAddress does not contain
        defaultBookingFiltering(
            "visitAddress.doesNotContain=" + UPDATED_VISIT_ADDRESS,
            "visitAddress.doesNotContain=" + DEFAULT_VISIT_ADDRESS
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCareSummarySharedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where careSummaryShared equals to
        defaultBookingFiltering(
            "careSummaryShared.equals=" + DEFAULT_CARE_SUMMARY_SHARED,
            "careSummaryShared.equals=" + UPDATED_CARE_SUMMARY_SHARED
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCareSummarySharedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where careSummaryShared in
        defaultBookingFiltering(
            "careSummaryShared.in=" + DEFAULT_CARE_SUMMARY_SHARED + "," + UPDATED_CARE_SUMMARY_SHARED,
            "careSummaryShared.in=" + UPDATED_CARE_SUMMARY_SHARED
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCareSummarySharedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where careSummaryShared is not null
        defaultBookingFiltering("careSummaryShared.specified=true", "careSummaryShared.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByRaisedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where raisedAt equals to
        defaultBookingFiltering("raisedAt.equals=" + DEFAULT_RAISED_AT, "raisedAt.equals=" + UPDATED_RAISED_AT);
    }

    @Test
    @Transactional
    void getAllBookingsByRaisedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where raisedAt in
        defaultBookingFiltering("raisedAt.in=" + DEFAULT_RAISED_AT + "," + UPDATED_RAISED_AT, "raisedAt.in=" + UPDATED_RAISED_AT);
    }

    @Test
    @Transactional
    void getAllBookingsByRaisedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where raisedAt is not null
        defaultBookingFiltering("raisedAt.specified=true", "raisedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByRespondedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where respondedAt equals to
        defaultBookingFiltering("respondedAt.equals=" + DEFAULT_RESPONDED_AT, "respondedAt.equals=" + UPDATED_RESPONDED_AT);
    }

    @Test
    @Transactional
    void getAllBookingsByRespondedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where respondedAt in
        defaultBookingFiltering(
            "respondedAt.in=" + DEFAULT_RESPONDED_AT + "," + UPDATED_RESPONDED_AT,
            "respondedAt.in=" + UPDATED_RESPONDED_AT
        );
    }

    @Test
    @Transactional
    void getAllBookingsByRespondedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where respondedAt is not null
        defaultBookingFiltering("respondedAt.specified=true", "respondedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCompletedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where completedAt equals to
        defaultBookingFiltering("completedAt.equals=" + DEFAULT_COMPLETED_AT, "completedAt.equals=" + UPDATED_COMPLETED_AT);
    }

    @Test
    @Transactional
    void getAllBookingsByCompletedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where completedAt in
        defaultBookingFiltering(
            "completedAt.in=" + DEFAULT_COMPLETED_AT + "," + UPDATED_COMPLETED_AT,
            "completedAt.in=" + UPDATED_COMPLETED_AT
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCompletedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where completedAt is not null
        defaultBookingFiltering("completedAt.specified=true", "completedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCancelledAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancelledAt equals to
        defaultBookingFiltering("cancelledAt.equals=" + DEFAULT_CANCELLED_AT, "cancelledAt.equals=" + UPDATED_CANCELLED_AT);
    }

    @Test
    @Transactional
    void getAllBookingsByCancelledAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancelledAt in
        defaultBookingFiltering(
            "cancelledAt.in=" + DEFAULT_CANCELLED_AT + "," + UPDATED_CANCELLED_AT,
            "cancelledAt.in=" + UPDATED_CANCELLED_AT
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCancelledAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancelledAt is not null
        defaultBookingFiltering("cancelledAt.specified=true", "cancelledAt.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCancelledByIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancelledBy equals to
        defaultBookingFiltering("cancelledBy.equals=" + DEFAULT_CANCELLED_BY, "cancelledBy.equals=" + UPDATED_CANCELLED_BY);
    }

    @Test
    @Transactional
    void getAllBookingsByCancelledByIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancelledBy in
        defaultBookingFiltering(
            "cancelledBy.in=" + DEFAULT_CANCELLED_BY + "," + UPDATED_CANCELLED_BY,
            "cancelledBy.in=" + UPDATED_CANCELLED_BY
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCancelledByIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancelledBy is not null
        defaultBookingFiltering("cancelledBy.specified=true", "cancelledBy.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCancellationReasonIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancellationReason equals to
        defaultBookingFiltering(
            "cancellationReason.equals=" + DEFAULT_CANCELLATION_REASON,
            "cancellationReason.equals=" + UPDATED_CANCELLATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCancellationReasonIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancellationReason in
        defaultBookingFiltering(
            "cancellationReason.in=" + DEFAULT_CANCELLATION_REASON + "," + UPDATED_CANCELLATION_REASON,
            "cancellationReason.in=" + UPDATED_CANCELLATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCancellationReasonIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancellationReason is not null
        defaultBookingFiltering("cancellationReason.specified=true", "cancellationReason.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByCancellationReasonContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancellationReason contains
        defaultBookingFiltering(
            "cancellationReason.contains=" + DEFAULT_CANCELLATION_REASON,
            "cancellationReason.contains=" + UPDATED_CANCELLATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllBookingsByCancellationReasonNotContainsSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where cancellationReason does not contain
        defaultBookingFiltering(
            "cancellationReason.doesNotContain=" + UPDATED_CANCELLATION_REASON,
            "cancellationReason.doesNotContain=" + DEFAULT_CANCELLATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllBookingsByLateCancellationIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where lateCancellation equals to
        defaultBookingFiltering(
            "lateCancellation.equals=" + DEFAULT_LATE_CANCELLATION,
            "lateCancellation.equals=" + UPDATED_LATE_CANCELLATION
        );
    }

    @Test
    @Transactional
    void getAllBookingsByLateCancellationIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where lateCancellation in
        defaultBookingFiltering(
            "lateCancellation.in=" + DEFAULT_LATE_CANCELLATION + "," + UPDATED_LATE_CANCELLATION,
            "lateCancellation.in=" + UPDATED_LATE_CANCELLATION
        );
    }

    @Test
    @Transactional
    void getAllBookingsByLateCancellationIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where lateCancellation is not null
        defaultBookingFiltering("lateCancellation.specified=true", "lateCancellation.specified=false");
    }

    @Test
    @Transactional
    void getAllBookingsByReviewedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reviewed equals to
        defaultBookingFiltering("reviewed.equals=" + DEFAULT_REVIEWED, "reviewed.equals=" + UPDATED_REVIEWED);
    }

    @Test
    @Transactional
    void getAllBookingsByReviewedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reviewed in
        defaultBookingFiltering("reviewed.in=" + DEFAULT_REVIEWED + "," + UPDATED_REVIEWED, "reviewed.in=" + UPDATED_REVIEWED);
    }

    @Test
    @Transactional
    void getAllBookingsByReviewedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        // Get all the bookingList where reviewed is not null
        defaultBookingFiltering("reviewed.specified=true", "reviewed.specified=false");
    }

    private void defaultBookingFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultBookingShouldBeFound(shouldBeFound);
        defaultBookingShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultBookingShouldBeFound(String filter) throws Exception {
        restBookingMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(booking.getId().intValue())))
            .andExpect(jsonPath("$.[*].reference").value(hasItem(DEFAULT_REFERENCE)))
            .andExpect(jsonPath("$.[*].customerLogin").value(hasItem(DEFAULT_CUSTOMER_LOGIN)))
            .andExpect(jsonPath("$.[*].customerName").value(hasItem(DEFAULT_CUSTOMER_NAME)))
            .andExpect(jsonPath("$.[*].professionalRef").value(hasItem(DEFAULT_PROFESSIONAL_REF)))
            .andExpect(jsonPath("$.[*].serviceRef").value(hasItem(DEFAULT_SERVICE_REF)))
            .andExpect(jsonPath("$.[*].serviceName").value(hasItem(DEFAULT_SERVICE_NAME)))
            .andExpect(jsonPath("$.[*].priceMinor").value(hasItem(DEFAULT_PRICE_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].scheduledDate").value(hasItem(DEFAULT_SCHEDULED_DATE.toString())))
            .andExpect(jsonPath("$.[*].scheduledTime").value(hasItem(DEFAULT_SCHEDULED_TIME)))
            .andExpect(jsonPath("$.[*].deliveryMode").value(hasItem(DEFAULT_DELIVERY_MODE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].customerNote").value(hasItem(DEFAULT_CUSTOMER_NOTE)))
            .andExpect(jsonPath("$.[*].onBehalfOf").value(hasItem(DEFAULT_ON_BEHALF_OF)))
            .andExpect(jsonPath("$.[*].visitAddress").value(hasItem(DEFAULT_VISIT_ADDRESS)))
            .andExpect(jsonPath("$.[*].careSummaryShared").value(hasItem(DEFAULT_CARE_SUMMARY_SHARED)))
            .andExpect(jsonPath("$.[*].raisedAt").value(hasItem(DEFAULT_RAISED_AT.toString())))
            .andExpect(jsonPath("$.[*].respondedAt").value(hasItem(DEFAULT_RESPONDED_AT.toString())))
            .andExpect(jsonPath("$.[*].completedAt").value(hasItem(DEFAULT_COMPLETED_AT.toString())))
            .andExpect(jsonPath("$.[*].cancelledAt").value(hasItem(DEFAULT_CANCELLED_AT.toString())))
            .andExpect(jsonPath("$.[*].cancelledBy").value(hasItem(DEFAULT_CANCELLED_BY.toString())))
            .andExpect(jsonPath("$.[*].cancellationReason").value(hasItem(DEFAULT_CANCELLATION_REASON)))
            .andExpect(jsonPath("$.[*].lateCancellation").value(hasItem(DEFAULT_LATE_CANCELLATION)))
            .andExpect(jsonPath("$.[*].reviewed").value(hasItem(DEFAULT_REVIEWED)));

        // Check, that the count call also returns 1
        restBookingMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultBookingShouldNotBeFound(String filter) throws Exception {
        restBookingMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restBookingMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingBooking() throws Exception {
        // Get the booking
        restBookingMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingBooking() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the booking
        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedBooking are not directly saved in db
        em.detach(updatedBooking);
        updatedBooking
            .reference(UPDATED_REFERENCE)
            .customerLogin(UPDATED_CUSTOMER_LOGIN)
            .customerName(UPDATED_CUSTOMER_NAME)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .serviceRef(UPDATED_SERVICE_REF)
            .serviceName(UPDATED_SERVICE_NAME)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .scheduledDate(UPDATED_SCHEDULED_DATE)
            .scheduledTime(UPDATED_SCHEDULED_TIME)
            .deliveryMode(UPDATED_DELIVERY_MODE)
            .status(UPDATED_STATUS)
            .customerNote(UPDATED_CUSTOMER_NOTE)
            .onBehalfOf(UPDATED_ON_BEHALF_OF)
            .visitAddress(UPDATED_VISIT_ADDRESS)
            .careSummaryShared(UPDATED_CARE_SUMMARY_SHARED)
            .raisedAt(UPDATED_RAISED_AT)
            .respondedAt(UPDATED_RESPONDED_AT)
            .completedAt(UPDATED_COMPLETED_AT)
            .cancelledAt(UPDATED_CANCELLED_AT)
            .cancelledBy(UPDATED_CANCELLED_BY)
            .cancellationReason(UPDATED_CANCELLATION_REASON)
            .lateCancellation(UPDATED_LATE_CANCELLATION)
            .reviewed(UPDATED_REVIEWED);
        BookingDTO bookingDTO = bookingMapper.toDto(updatedBooking);

        restBookingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, bookingDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO))
            )
            .andExpect(status().isOk());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedBookingToMatchAllProperties(updatedBooking);
    }

    @Test
    @Transactional
    void putNonExistingBooking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        booking.setId(longCount.incrementAndGet());

        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBookingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, bookingDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchBooking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        booking.setId(longCount.incrementAndGet());

        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(bookingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamBooking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        booking.setId(longCount.incrementAndGet());

        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateBookingWithPatch() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the booking using partial update
        Booking partialUpdatedBooking = new Booking();
        partialUpdatedBooking.setId(booking.getId());

        partialUpdatedBooking
            .reference(UPDATED_REFERENCE)
            .customerLogin(UPDATED_CUSTOMER_LOGIN)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .scheduledDate(UPDATED_SCHEDULED_DATE)
            .scheduledTime(UPDATED_SCHEDULED_TIME)
            .status(UPDATED_STATUS)
            .onBehalfOf(UPDATED_ON_BEHALF_OF)
            .careSummaryShared(UPDATED_CARE_SUMMARY_SHARED)
            .raisedAt(UPDATED_RAISED_AT)
            .respondedAt(UPDATED_RESPONDED_AT)
            .completedAt(UPDATED_COMPLETED_AT)
            .cancelledBy(UPDATED_CANCELLED_BY)
            .reviewed(UPDATED_REVIEWED);

        restBookingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBooking.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBooking))
            )
            .andExpect(status().isOk());

        // Validate the Booking in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBookingUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedBooking, booking), getPersistedBooking(booking));
    }

    @Test
    @Transactional
    void fullUpdateBookingWithPatch() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the booking using partial update
        Booking partialUpdatedBooking = new Booking();
        partialUpdatedBooking.setId(booking.getId());

        partialUpdatedBooking
            .reference(UPDATED_REFERENCE)
            .customerLogin(UPDATED_CUSTOMER_LOGIN)
            .customerName(UPDATED_CUSTOMER_NAME)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .serviceRef(UPDATED_SERVICE_REF)
            .serviceName(UPDATED_SERVICE_NAME)
            .priceMinor(UPDATED_PRICE_MINOR)
            .currency(UPDATED_CURRENCY)
            .scheduledDate(UPDATED_SCHEDULED_DATE)
            .scheduledTime(UPDATED_SCHEDULED_TIME)
            .deliveryMode(UPDATED_DELIVERY_MODE)
            .status(UPDATED_STATUS)
            .customerNote(UPDATED_CUSTOMER_NOTE)
            .onBehalfOf(UPDATED_ON_BEHALF_OF)
            .visitAddress(UPDATED_VISIT_ADDRESS)
            .careSummaryShared(UPDATED_CARE_SUMMARY_SHARED)
            .raisedAt(UPDATED_RAISED_AT)
            .respondedAt(UPDATED_RESPONDED_AT)
            .completedAt(UPDATED_COMPLETED_AT)
            .cancelledAt(UPDATED_CANCELLED_AT)
            .cancelledBy(UPDATED_CANCELLED_BY)
            .cancellationReason(UPDATED_CANCELLATION_REASON)
            .lateCancellation(UPDATED_LATE_CANCELLATION)
            .reviewed(UPDATED_REVIEWED);

        restBookingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBooking.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBooking))
            )
            .andExpect(status().isOk());

        // Validate the Booking in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBookingUpdatableFieldsEquals(partialUpdatedBooking, getPersistedBooking(partialUpdatedBooking));
    }

    @Test
    @Transactional
    void patchNonExistingBooking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        booking.setId(longCount.incrementAndGet());

        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBookingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, bookingDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(bookingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchBooking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        booking.setId(longCount.incrementAndGet());

        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(bookingDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamBooking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        booking.setId(longCount.incrementAndGet());

        // Create the Booking
        BookingDTO bookingDTO = bookingMapper.toDto(booking);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBookingMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(bookingDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Booking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteBooking() throws Exception {
        // Initialize the database
        insertedBooking = bookingRepository.saveAndFlush(booking);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the booking
        restBookingMockMvc
            .perform(delete(ENTITY_API_URL_ID, booking.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return bookingRepository.count();
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

    protected Booking getPersistedBooking(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    protected void assertPersistedBookingToMatchAllProperties(Booking expectedBooking) {
        assertBookingAllPropertiesEquals(expectedBooking, getPersistedBooking(expectedBooking));
    }

    protected void assertPersistedBookingToMatchUpdatableProperties(Booking expectedBooking) {
        assertBookingAllUpdatablePropertiesEquals(expectedBooking, getPersistedBooking(expectedBooking));
    }
}
