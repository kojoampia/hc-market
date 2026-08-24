package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.LedgerAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.Payout;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.service.dto.LedgerDTO;
import net.jojoaddison.service.mapper.LedgerMapper;
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
 * Integration tests for the {@link LedgerResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class LedgerResourceIT {

    private static final String DEFAULT_BOOKING_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_BOOKING_REFERENCE = "BBBBBBBBBB";

    private static final String DEFAULT_PROFESSIONAL_REF = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_REF = "BBBBBBBBBB";

    private static final String DEFAULT_PROFESSIONAL_LOGIN = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_LOGIN = "BBBBBBBBBB";

    private static final Long DEFAULT_GROSS_MINOR = 1L;
    private static final Long UPDATED_GROSS_MINOR = 2L;
    private static final Long SMALLER_GROSS_MINOR = 1L - 1L;

    private static final Long DEFAULT_COMMISSION_MINOR = 1L;
    private static final Long UPDATED_COMMISSION_MINOR = 2L;
    private static final Long SMALLER_COMMISSION_MINOR = 1L - 1L;

    private static final Long DEFAULT_NET_MINOR = 1L;
    private static final Long UPDATED_NET_MINOR = 2L;
    private static final Long SMALLER_NET_MINOR = 1L - 1L;

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final DeliveryMode DEFAULT_DELIVERY_MODE = DeliveryMode.IN_PERSON;
    private static final DeliveryMode UPDATED_DELIVERY_MODE = DeliveryMode.ONLINE;

    private static final String DEFAULT_SERVICE_REF = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_REF = "BBBBBBBBBB";

    private static final String DEFAULT_SERVICE_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_NAME = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_EARNED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_EARNED_ON = LocalDate.parse("2023-12-04");
    private static final LocalDate SMALLER_EARNED_ON = LocalDate.ofEpochDay(-1L);

    private static final String ENTITY_API_URL = "/api/ledgers";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private LedgerMapper ledgerMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restLedgerMockMvc;

    private Ledger ledger;

    private Ledger insertedLedger;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Ledger createEntity() {
        return new Ledger()
            .bookingReference(DEFAULT_BOOKING_REFERENCE)
            .professionalRef(DEFAULT_PROFESSIONAL_REF)
            .professionalLogin(DEFAULT_PROFESSIONAL_LOGIN)
            .grossMinor(DEFAULT_GROSS_MINOR)
            .commissionMinor(DEFAULT_COMMISSION_MINOR)
            .netMinor(DEFAULT_NET_MINOR)
            .currency(DEFAULT_CURRENCY)
            .deliveryMode(DEFAULT_DELIVERY_MODE)
            .serviceRef(DEFAULT_SERVICE_REF)
            .serviceName(DEFAULT_SERVICE_NAME)
            .earnedOn(DEFAULT_EARNED_ON);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Ledger createUpdatedEntity() {
        return new Ledger()
            .bookingReference(UPDATED_BOOKING_REFERENCE)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .professionalLogin(UPDATED_PROFESSIONAL_LOGIN)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .deliveryMode(UPDATED_DELIVERY_MODE)
            .serviceRef(UPDATED_SERVICE_REF)
            .serviceName(UPDATED_SERVICE_NAME)
            .earnedOn(UPDATED_EARNED_ON);
    }

    @BeforeEach
    void initTest() {
        ledger = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedLedger != null) {
            ledgerRepository.delete(insertedLedger);
            insertedLedger = null;
        }
    }

    @Test
    @Transactional
    void createLedger() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);
        var returnedLedgerDTO = om.readValue(
            restLedgerMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            LedgerDTO.class
        );

        // Validate the Ledger in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedLedger = ledgerMapper.toEntity(returnedLedgerDTO);
        assertLedgerUpdatableFieldsEquals(returnedLedger, getPersistedLedger(returnedLedger));

        insertedLedger = returnedLedger;
    }

    @Test
    @Transactional
    void createLedgerWithExistingId() throws Exception {
        // Create the Ledger with an existing ID
        ledger.setId(1L);
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkBookingReferenceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setBookingReference(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkProfessionalRefIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setProfessionalRef(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkProfessionalLoginIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setProfessionalLogin(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkGrossMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setGrossMinor(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCommissionMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setCommissionMinor(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkNetMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setNetMinor(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setCurrency(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDeliveryModeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setDeliveryMode(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEarnedOnIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        ledger.setEarnedOn(null);

        // Create the Ledger, which fails.
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        restLedgerMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllLedgers() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList
        restLedgerMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ledger.getId().intValue())))
            .andExpect(jsonPath("$.[*].bookingReference").value(hasItem(DEFAULT_BOOKING_REFERENCE)))
            .andExpect(jsonPath("$.[*].professionalRef").value(hasItem(DEFAULT_PROFESSIONAL_REF)))
            .andExpect(jsonPath("$.[*].professionalLogin").value(hasItem(DEFAULT_PROFESSIONAL_LOGIN)))
            .andExpect(jsonPath("$.[*].grossMinor").value(hasItem(DEFAULT_GROSS_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].commissionMinor").value(hasItem(DEFAULT_COMMISSION_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].netMinor").value(hasItem(DEFAULT_NET_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].deliveryMode").value(hasItem(DEFAULT_DELIVERY_MODE.toString())))
            .andExpect(jsonPath("$.[*].serviceRef").value(hasItem(DEFAULT_SERVICE_REF)))
            .andExpect(jsonPath("$.[*].serviceName").value(hasItem(DEFAULT_SERVICE_NAME)))
            .andExpect(jsonPath("$.[*].earnedOn").value(hasItem(DEFAULT_EARNED_ON.toString())));
    }

    @Test
    @Transactional
    void getLedger() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get the ledger
        restLedgerMockMvc
            .perform(get(ENTITY_API_URL_ID, ledger.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(ledger.getId().intValue()))
            .andExpect(jsonPath("$.bookingReference").value(DEFAULT_BOOKING_REFERENCE))
            .andExpect(jsonPath("$.professionalRef").value(DEFAULT_PROFESSIONAL_REF))
            .andExpect(jsonPath("$.professionalLogin").value(DEFAULT_PROFESSIONAL_LOGIN))
            .andExpect(jsonPath("$.grossMinor").value(DEFAULT_GROSS_MINOR.intValue()))
            .andExpect(jsonPath("$.commissionMinor").value(DEFAULT_COMMISSION_MINOR.intValue()))
            .andExpect(jsonPath("$.netMinor").value(DEFAULT_NET_MINOR.intValue()))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.deliveryMode").value(DEFAULT_DELIVERY_MODE.toString()))
            .andExpect(jsonPath("$.serviceRef").value(DEFAULT_SERVICE_REF))
            .andExpect(jsonPath("$.serviceName").value(DEFAULT_SERVICE_NAME))
            .andExpect(jsonPath("$.earnedOn").value(DEFAULT_EARNED_ON.toString()));
    }

    @Test
    @Transactional
    void getLedgersByIdFiltering() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        Long id = ledger.getId();

        defaultLedgerFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultLedgerFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultLedgerFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllLedgersByBookingReferenceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where bookingReference equals to
        defaultLedgerFiltering(
            "bookingReference.equals=" + DEFAULT_BOOKING_REFERENCE,
            "bookingReference.equals=" + UPDATED_BOOKING_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllLedgersByBookingReferenceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where bookingReference in
        defaultLedgerFiltering(
            "bookingReference.in=" + DEFAULT_BOOKING_REFERENCE + "," + UPDATED_BOOKING_REFERENCE,
            "bookingReference.in=" + UPDATED_BOOKING_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllLedgersByBookingReferenceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where bookingReference is not null
        defaultLedgerFiltering("bookingReference.specified=true", "bookingReference.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByBookingReferenceContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where bookingReference contains
        defaultLedgerFiltering(
            "bookingReference.contains=" + DEFAULT_BOOKING_REFERENCE,
            "bookingReference.contains=" + UPDATED_BOOKING_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllLedgersByBookingReferenceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where bookingReference does not contain
        defaultLedgerFiltering(
            "bookingReference.doesNotContain=" + UPDATED_BOOKING_REFERENCE,
            "bookingReference.doesNotContain=" + DEFAULT_BOOKING_REFERENCE
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalRefIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalRef equals to
        defaultLedgerFiltering("professionalRef.equals=" + DEFAULT_PROFESSIONAL_REF, "professionalRef.equals=" + UPDATED_PROFESSIONAL_REF);
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalRefIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalRef in
        defaultLedgerFiltering(
            "professionalRef.in=" + DEFAULT_PROFESSIONAL_REF + "," + UPDATED_PROFESSIONAL_REF,
            "professionalRef.in=" + UPDATED_PROFESSIONAL_REF
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalRefIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalRef is not null
        defaultLedgerFiltering("professionalRef.specified=true", "professionalRef.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalRefContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalRef contains
        defaultLedgerFiltering(
            "professionalRef.contains=" + DEFAULT_PROFESSIONAL_REF,
            "professionalRef.contains=" + UPDATED_PROFESSIONAL_REF
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalRefNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalRef does not contain
        defaultLedgerFiltering(
            "professionalRef.doesNotContain=" + UPDATED_PROFESSIONAL_REF,
            "professionalRef.doesNotContain=" + DEFAULT_PROFESSIONAL_REF
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalLoginIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalLogin equals to
        defaultLedgerFiltering(
            "professionalLogin.equals=" + DEFAULT_PROFESSIONAL_LOGIN,
            "professionalLogin.equals=" + UPDATED_PROFESSIONAL_LOGIN
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalLoginIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalLogin in
        defaultLedgerFiltering(
            "professionalLogin.in=" + DEFAULT_PROFESSIONAL_LOGIN + "," + UPDATED_PROFESSIONAL_LOGIN,
            "professionalLogin.in=" + UPDATED_PROFESSIONAL_LOGIN
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalLoginIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalLogin is not null
        defaultLedgerFiltering("professionalLogin.specified=true", "professionalLogin.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalLoginContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalLogin contains
        defaultLedgerFiltering(
            "professionalLogin.contains=" + DEFAULT_PROFESSIONAL_LOGIN,
            "professionalLogin.contains=" + UPDATED_PROFESSIONAL_LOGIN
        );
    }

    @Test
    @Transactional
    void getAllLedgersByProfessionalLoginNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where professionalLogin does not contain
        defaultLedgerFiltering(
            "professionalLogin.doesNotContain=" + UPDATED_PROFESSIONAL_LOGIN,
            "professionalLogin.doesNotContain=" + DEFAULT_PROFESSIONAL_LOGIN
        );
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor equals to
        defaultLedgerFiltering("grossMinor.equals=" + DEFAULT_GROSS_MINOR, "grossMinor.equals=" + UPDATED_GROSS_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor in
        defaultLedgerFiltering("grossMinor.in=" + DEFAULT_GROSS_MINOR + "," + UPDATED_GROSS_MINOR, "grossMinor.in=" + UPDATED_GROSS_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor is not null
        defaultLedgerFiltering("grossMinor.specified=true", "grossMinor.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor is greater than or equal to
        defaultLedgerFiltering(
            "grossMinor.greaterThanOrEqual=" + DEFAULT_GROSS_MINOR,
            "grossMinor.greaterThanOrEqual=" + UPDATED_GROSS_MINOR
        );
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor is less than or equal to
        defaultLedgerFiltering("grossMinor.lessThanOrEqual=" + DEFAULT_GROSS_MINOR, "grossMinor.lessThanOrEqual=" + SMALLER_GROSS_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor is less than
        defaultLedgerFiltering("grossMinor.lessThan=" + UPDATED_GROSS_MINOR, "grossMinor.lessThan=" + DEFAULT_GROSS_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByGrossMinorIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where grossMinor is greater than
        defaultLedgerFiltering("grossMinor.greaterThan=" + SMALLER_GROSS_MINOR, "grossMinor.greaterThan=" + DEFAULT_GROSS_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor equals to
        defaultLedgerFiltering("commissionMinor.equals=" + DEFAULT_COMMISSION_MINOR, "commissionMinor.equals=" + UPDATED_COMMISSION_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor in
        defaultLedgerFiltering(
            "commissionMinor.in=" + DEFAULT_COMMISSION_MINOR + "," + UPDATED_COMMISSION_MINOR,
            "commissionMinor.in=" + UPDATED_COMMISSION_MINOR
        );
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor is not null
        defaultLedgerFiltering("commissionMinor.specified=true", "commissionMinor.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor is greater than or equal to
        defaultLedgerFiltering(
            "commissionMinor.greaterThanOrEqual=" + DEFAULT_COMMISSION_MINOR,
            "commissionMinor.greaterThanOrEqual=" + UPDATED_COMMISSION_MINOR
        );
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor is less than or equal to
        defaultLedgerFiltering(
            "commissionMinor.lessThanOrEqual=" + DEFAULT_COMMISSION_MINOR,
            "commissionMinor.lessThanOrEqual=" + SMALLER_COMMISSION_MINOR
        );
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor is less than
        defaultLedgerFiltering(
            "commissionMinor.lessThan=" + UPDATED_COMMISSION_MINOR,
            "commissionMinor.lessThan=" + DEFAULT_COMMISSION_MINOR
        );
    }

    @Test
    @Transactional
    void getAllLedgersByCommissionMinorIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where commissionMinor is greater than
        defaultLedgerFiltering(
            "commissionMinor.greaterThan=" + SMALLER_COMMISSION_MINOR,
            "commissionMinor.greaterThan=" + DEFAULT_COMMISSION_MINOR
        );
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor equals to
        defaultLedgerFiltering("netMinor.equals=" + DEFAULT_NET_MINOR, "netMinor.equals=" + UPDATED_NET_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor in
        defaultLedgerFiltering("netMinor.in=" + DEFAULT_NET_MINOR + "," + UPDATED_NET_MINOR, "netMinor.in=" + UPDATED_NET_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor is not null
        defaultLedgerFiltering("netMinor.specified=true", "netMinor.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor is greater than or equal to
        defaultLedgerFiltering("netMinor.greaterThanOrEqual=" + DEFAULT_NET_MINOR, "netMinor.greaterThanOrEqual=" + UPDATED_NET_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor is less than or equal to
        defaultLedgerFiltering("netMinor.lessThanOrEqual=" + DEFAULT_NET_MINOR, "netMinor.lessThanOrEqual=" + SMALLER_NET_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor is less than
        defaultLedgerFiltering("netMinor.lessThan=" + UPDATED_NET_MINOR, "netMinor.lessThan=" + DEFAULT_NET_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByNetMinorIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where netMinor is greater than
        defaultLedgerFiltering("netMinor.greaterThan=" + SMALLER_NET_MINOR, "netMinor.greaterThan=" + DEFAULT_NET_MINOR);
    }

    @Test
    @Transactional
    void getAllLedgersByCurrencyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where currency equals to
        defaultLedgerFiltering("currency.equals=" + DEFAULT_CURRENCY, "currency.equals=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllLedgersByCurrencyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where currency in
        defaultLedgerFiltering("currency.in=" + DEFAULT_CURRENCY + "," + UPDATED_CURRENCY, "currency.in=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllLedgersByCurrencyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where currency is not null
        defaultLedgerFiltering("currency.specified=true", "currency.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByCurrencyContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where currency contains
        defaultLedgerFiltering("currency.contains=" + DEFAULT_CURRENCY, "currency.contains=" + UPDATED_CURRENCY);
    }

    @Test
    @Transactional
    void getAllLedgersByCurrencyNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where currency does not contain
        defaultLedgerFiltering("currency.doesNotContain=" + UPDATED_CURRENCY, "currency.doesNotContain=" + DEFAULT_CURRENCY);
    }

    @Test
    @Transactional
    void getAllLedgersByDeliveryModeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where deliveryMode equals to
        defaultLedgerFiltering("deliveryMode.equals=" + DEFAULT_DELIVERY_MODE, "deliveryMode.equals=" + UPDATED_DELIVERY_MODE);
    }

    @Test
    @Transactional
    void getAllLedgersByDeliveryModeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where deliveryMode in
        defaultLedgerFiltering(
            "deliveryMode.in=" + DEFAULT_DELIVERY_MODE + "," + UPDATED_DELIVERY_MODE,
            "deliveryMode.in=" + UPDATED_DELIVERY_MODE
        );
    }

    @Test
    @Transactional
    void getAllLedgersByDeliveryModeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where deliveryMode is not null
        defaultLedgerFiltering("deliveryMode.specified=true", "deliveryMode.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByServiceRefIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceRef equals to
        defaultLedgerFiltering("serviceRef.equals=" + DEFAULT_SERVICE_REF, "serviceRef.equals=" + UPDATED_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllLedgersByServiceRefIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceRef in
        defaultLedgerFiltering("serviceRef.in=" + DEFAULT_SERVICE_REF + "," + UPDATED_SERVICE_REF, "serviceRef.in=" + UPDATED_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllLedgersByServiceRefIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceRef is not null
        defaultLedgerFiltering("serviceRef.specified=true", "serviceRef.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByServiceRefContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceRef contains
        defaultLedgerFiltering("serviceRef.contains=" + DEFAULT_SERVICE_REF, "serviceRef.contains=" + UPDATED_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllLedgersByServiceRefNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceRef does not contain
        defaultLedgerFiltering("serviceRef.doesNotContain=" + UPDATED_SERVICE_REF, "serviceRef.doesNotContain=" + DEFAULT_SERVICE_REF);
    }

    @Test
    @Transactional
    void getAllLedgersByServiceNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceName equals to
        defaultLedgerFiltering("serviceName.equals=" + DEFAULT_SERVICE_NAME, "serviceName.equals=" + UPDATED_SERVICE_NAME);
    }

    @Test
    @Transactional
    void getAllLedgersByServiceNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceName in
        defaultLedgerFiltering(
            "serviceName.in=" + DEFAULT_SERVICE_NAME + "," + UPDATED_SERVICE_NAME,
            "serviceName.in=" + UPDATED_SERVICE_NAME
        );
    }

    @Test
    @Transactional
    void getAllLedgersByServiceNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceName is not null
        defaultLedgerFiltering("serviceName.specified=true", "serviceName.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByServiceNameContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceName contains
        defaultLedgerFiltering("serviceName.contains=" + DEFAULT_SERVICE_NAME, "serviceName.contains=" + UPDATED_SERVICE_NAME);
    }

    @Test
    @Transactional
    void getAllLedgersByServiceNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where serviceName does not contain
        defaultLedgerFiltering("serviceName.doesNotContain=" + UPDATED_SERVICE_NAME, "serviceName.doesNotContain=" + DEFAULT_SERVICE_NAME);
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn equals to
        defaultLedgerFiltering("earnedOn.equals=" + DEFAULT_EARNED_ON, "earnedOn.equals=" + UPDATED_EARNED_ON);
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn in
        defaultLedgerFiltering("earnedOn.in=" + DEFAULT_EARNED_ON + "," + UPDATED_EARNED_ON, "earnedOn.in=" + UPDATED_EARNED_ON);
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn is not null
        defaultLedgerFiltering("earnedOn.specified=true", "earnedOn.specified=false");
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn is greater than or equal to
        defaultLedgerFiltering("earnedOn.greaterThanOrEqual=" + DEFAULT_EARNED_ON, "earnedOn.greaterThanOrEqual=" + UPDATED_EARNED_ON);
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn is less than or equal to
        defaultLedgerFiltering("earnedOn.lessThanOrEqual=" + DEFAULT_EARNED_ON, "earnedOn.lessThanOrEqual=" + SMALLER_EARNED_ON);
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn is less than
        defaultLedgerFiltering("earnedOn.lessThan=" + UPDATED_EARNED_ON, "earnedOn.lessThan=" + DEFAULT_EARNED_ON);
    }

    @Test
    @Transactional
    void getAllLedgersByEarnedOnIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        // Get all the ledgerList where earnedOn is greater than
        defaultLedgerFiltering("earnedOn.greaterThan=" + SMALLER_EARNED_ON, "earnedOn.greaterThan=" + DEFAULT_EARNED_ON);
    }

    @Test
    @Transactional
    void getAllLedgersByPayoutIsEqualToSomething() throws Exception {
        Payout payout;
        if (TestUtil.findAll(em, Payout.class).isEmpty()) {
            ledgerRepository.saveAndFlush(ledger);
            payout = PayoutResourceIT.createEntity();
        } else {
            payout = TestUtil.findAll(em, Payout.class).get(0);
        }
        em.persist(payout);
        em.flush();
        ledger.setPayout(payout);
        ledgerRepository.saveAndFlush(ledger);
        Long payoutId = payout.getId();
        // Get all the ledgerList where payout equals to payoutId
        defaultLedgerShouldBeFound("payoutId.equals=" + payoutId);

        // Get all the ledgerList where payout equals to (payoutId + 1)
        defaultLedgerShouldNotBeFound("payoutId.equals=" + (payoutId + 1));
    }

    private void defaultLedgerFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultLedgerShouldBeFound(shouldBeFound);
        defaultLedgerShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultLedgerShouldBeFound(String filter) throws Exception {
        restLedgerMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ledger.getId().intValue())))
            .andExpect(jsonPath("$.[*].bookingReference").value(hasItem(DEFAULT_BOOKING_REFERENCE)))
            .andExpect(jsonPath("$.[*].professionalRef").value(hasItem(DEFAULT_PROFESSIONAL_REF)))
            .andExpect(jsonPath("$.[*].professionalLogin").value(hasItem(DEFAULT_PROFESSIONAL_LOGIN)))
            .andExpect(jsonPath("$.[*].grossMinor").value(hasItem(DEFAULT_GROSS_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].commissionMinor").value(hasItem(DEFAULT_COMMISSION_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].netMinor").value(hasItem(DEFAULT_NET_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].deliveryMode").value(hasItem(DEFAULT_DELIVERY_MODE.toString())))
            .andExpect(jsonPath("$.[*].serviceRef").value(hasItem(DEFAULT_SERVICE_REF)))
            .andExpect(jsonPath("$.[*].serviceName").value(hasItem(DEFAULT_SERVICE_NAME)))
            .andExpect(jsonPath("$.[*].earnedOn").value(hasItem(DEFAULT_EARNED_ON.toString())));

        // Check, that the count call also returns 1
        restLedgerMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultLedgerShouldNotBeFound(String filter) throws Exception {
        restLedgerMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restLedgerMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingLedger() throws Exception {
        // Get the ledger
        restLedgerMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingLedger() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ledger
        Ledger updatedLedger = ledgerRepository.findById(ledger.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedLedger are not directly saved in db
        em.detach(updatedLedger);
        updatedLedger
            .bookingReference(UPDATED_BOOKING_REFERENCE)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .professionalLogin(UPDATED_PROFESSIONAL_LOGIN)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .deliveryMode(UPDATED_DELIVERY_MODE)
            .serviceRef(UPDATED_SERVICE_REF)
            .serviceName(UPDATED_SERVICE_NAME)
            .earnedOn(UPDATED_EARNED_ON);
        LedgerDTO ledgerDTO = ledgerMapper.toDto(updatedLedger);

        restLedgerMockMvc
            .perform(
                put(ENTITY_API_URL_ID, ledgerDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO))
            )
            .andExpect(status().isOk());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedLedgerToMatchAllProperties(updatedLedger);
    }

    @Test
    @Transactional
    void putNonExistingLedger() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledger.setId(longCount.incrementAndGet());

        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restLedgerMockMvc
            .perform(
                put(ENTITY_API_URL_ID, ledgerDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchLedger() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledger.setId(longCount.incrementAndGet());

        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ledgerDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamLedger() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledger.setId(longCount.incrementAndGet());

        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateLedgerWithPatch() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ledger using partial update
        Ledger partialUpdatedLedger = new Ledger();
        partialUpdatedLedger.setId(ledger.getId());

        partialUpdatedLedger
            .bookingReference(UPDATED_BOOKING_REFERENCE)
            .professionalLogin(UPDATED_PROFESSIONAL_LOGIN)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .serviceRef(UPDATED_SERVICE_REF)
            .earnedOn(UPDATED_EARNED_ON);

        restLedgerMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedLedger.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedLedger))
            )
            .andExpect(status().isOk());

        // Validate the Ledger in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLedgerUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedLedger, ledger), getPersistedLedger(ledger));
    }

    @Test
    @Transactional
    void fullUpdateLedgerWithPatch() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ledger using partial update
        Ledger partialUpdatedLedger = new Ledger();
        partialUpdatedLedger.setId(ledger.getId());

        partialUpdatedLedger
            .bookingReference(UPDATED_BOOKING_REFERENCE)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .professionalLogin(UPDATED_PROFESSIONAL_LOGIN)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .deliveryMode(UPDATED_DELIVERY_MODE)
            .serviceRef(UPDATED_SERVICE_REF)
            .serviceName(UPDATED_SERVICE_NAME)
            .earnedOn(UPDATED_EARNED_ON);

        restLedgerMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedLedger.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedLedger))
            )
            .andExpect(status().isOk());

        // Validate the Ledger in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLedgerUpdatableFieldsEquals(partialUpdatedLedger, getPersistedLedger(partialUpdatedLedger));
    }

    @Test
    @Transactional
    void patchNonExistingLedger() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledger.setId(longCount.incrementAndGet());

        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restLedgerMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, ledgerDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ledgerDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchLedger() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledger.setId(longCount.incrementAndGet());

        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ledgerDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamLedger() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ledger.setId(longCount.incrementAndGet());

        // Create the Ledger
        LedgerDTO ledgerDTO = ledgerMapper.toDto(ledger);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restLedgerMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(ledgerDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Ledger in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteLedger() throws Exception {
        // Initialize the database
        insertedLedger = ledgerRepository.saveAndFlush(ledger);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the ledger
        restLedgerMockMvc
            .perform(delete(ENTITY_API_URL_ID, ledger.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return ledgerRepository.count();
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

    protected Ledger getPersistedLedger(Ledger ledger) {
        return ledgerRepository.findById(ledger.getId()).orElseThrow();
    }

    protected void assertPersistedLedgerToMatchAllProperties(Ledger expectedLedger) {
        assertLedgerAllPropertiesEquals(expectedLedger, getPersistedLedger(expectedLedger));
    }

    protected void assertPersistedLedgerToMatchUpdatableProperties(Ledger expectedLedger) {
        assertLedgerAllUpdatablePropertiesEquals(expectedLedger, getPersistedLedger(expectedLedger));
    }
}
