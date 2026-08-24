package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PayoutAsserts.*;
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
import net.jojoaddison.domain.Payout;
import net.jojoaddison.domain.enumeration.PayoutStatus;
import net.jojoaddison.repository.PayoutRepository;
import net.jojoaddison.service.dto.PayoutDTO;
import net.jojoaddison.service.mapper.PayoutMapper;
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
 * Integration tests for the {@link PayoutResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class PayoutResourceIT {

    private static final String DEFAULT_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_REFERENCE = "BBBBBBBBBB";

    private static final String DEFAULT_PROFESSIONAL_REF = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_REF = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_PERIOD_START = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_PERIOD_START = LocalDate.parse("2023-12-04");

    private static final LocalDate DEFAULT_PERIOD_END = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_PERIOD_END = LocalDate.parse("2023-12-04");

    private static final Long DEFAULT_GROSS_MINOR = 1L;
    private static final Long UPDATED_GROSS_MINOR = 2L;

    private static final Long DEFAULT_COMMISSION_MINOR = 1L;
    private static final Long UPDATED_COMMISSION_MINOR = 2L;

    private static final Long DEFAULT_NET_MINOR = 1L;
    private static final Long UPDATED_NET_MINOR = 2L;

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final PayoutStatus DEFAULT_STATUS = PayoutStatus.OPEN;
    private static final PayoutStatus UPDATED_STATUS = PayoutStatus.IN_PROGRESS;

    private static final LocalDate DEFAULT_SETTLED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_SETTLED_ON = LocalDate.parse("2023-12-04");

    private static final String DEFAULT_BANK_REFERENCE = "AAAAAAAAAA";
    private static final String UPDATED_BANK_REFERENCE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/payouts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private PayoutMapper payoutMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restPayoutMockMvc;

    private Payout payout;

    private Payout insertedPayout;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Payout createEntity() {
        return new Payout()
            .reference(DEFAULT_REFERENCE)
            .professionalRef(DEFAULT_PROFESSIONAL_REF)
            .periodStart(DEFAULT_PERIOD_START)
            .periodEnd(DEFAULT_PERIOD_END)
            .grossMinor(DEFAULT_GROSS_MINOR)
            .commissionMinor(DEFAULT_COMMISSION_MINOR)
            .netMinor(DEFAULT_NET_MINOR)
            .currency(DEFAULT_CURRENCY)
            .status(DEFAULT_STATUS)
            .settledOn(DEFAULT_SETTLED_ON)
            .bankReference(DEFAULT_BANK_REFERENCE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Payout createUpdatedEntity() {
        return new Payout()
            .reference(UPDATED_REFERENCE)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .periodStart(UPDATED_PERIOD_START)
            .periodEnd(UPDATED_PERIOD_END)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .settledOn(UPDATED_SETTLED_ON)
            .bankReference(UPDATED_BANK_REFERENCE);
    }

    @BeforeEach
    void initTest() {
        payout = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPayout != null) {
            payoutRepository.delete(insertedPayout);
            insertedPayout = null;
        }
    }

    @Test
    @Transactional
    void createPayout() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);
        var returnedPayoutDTO = om.readValue(
            restPayoutMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            PayoutDTO.class
        );

        // Validate the Payout in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPayout = payoutMapper.toEntity(returnedPayoutDTO);
        assertPayoutUpdatableFieldsEquals(returnedPayout, getPersistedPayout(returnedPayout));

        insertedPayout = returnedPayout;
    }

    @Test
    @Transactional
    void createPayoutWithExistingId() throws Exception {
        // Create the Payout with an existing ID
        payout.setId(1L);
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkReferenceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setReference(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkProfessionalRefIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setProfessionalRef(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPeriodStartIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setPeriodStart(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPeriodEndIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setPeriodEnd(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkGrossMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setGrossMinor(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCommissionMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setCommissionMinor(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkNetMinorIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setNetMinor(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setCurrency(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        payout.setStatus(null);

        // Create the Payout, which fails.
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        restPayoutMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllPayouts() throws Exception {
        // Initialize the database
        insertedPayout = payoutRepository.saveAndFlush(payout);

        // Get all the payoutList
        restPayoutMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(payout.getId().intValue())))
            .andExpect(jsonPath("$.[*].reference").value(hasItem(DEFAULT_REFERENCE)))
            .andExpect(jsonPath("$.[*].professionalRef").value(hasItem(DEFAULT_PROFESSIONAL_REF)))
            .andExpect(jsonPath("$.[*].periodStart").value(hasItem(DEFAULT_PERIOD_START.toString())))
            .andExpect(jsonPath("$.[*].periodEnd").value(hasItem(DEFAULT_PERIOD_END.toString())))
            .andExpect(jsonPath("$.[*].grossMinor").value(hasItem(DEFAULT_GROSS_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].commissionMinor").value(hasItem(DEFAULT_COMMISSION_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].netMinor").value(hasItem(DEFAULT_NET_MINOR.intValue())))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].settledOn").value(hasItem(DEFAULT_SETTLED_ON.toString())))
            .andExpect(jsonPath("$.[*].bankReference").value(hasItem(DEFAULT_BANK_REFERENCE)));
    }

    @Test
    @Transactional
    void getPayout() throws Exception {
        // Initialize the database
        insertedPayout = payoutRepository.saveAndFlush(payout);

        // Get the payout
        restPayoutMockMvc
            .perform(get(ENTITY_API_URL_ID, payout.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(payout.getId().intValue()))
            .andExpect(jsonPath("$.reference").value(DEFAULT_REFERENCE))
            .andExpect(jsonPath("$.professionalRef").value(DEFAULT_PROFESSIONAL_REF))
            .andExpect(jsonPath("$.periodStart").value(DEFAULT_PERIOD_START.toString()))
            .andExpect(jsonPath("$.periodEnd").value(DEFAULT_PERIOD_END.toString()))
            .andExpect(jsonPath("$.grossMinor").value(DEFAULT_GROSS_MINOR.intValue()))
            .andExpect(jsonPath("$.commissionMinor").value(DEFAULT_COMMISSION_MINOR.intValue()))
            .andExpect(jsonPath("$.netMinor").value(DEFAULT_NET_MINOR.intValue()))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.settledOn").value(DEFAULT_SETTLED_ON.toString()))
            .andExpect(jsonPath("$.bankReference").value(DEFAULT_BANK_REFERENCE));
    }

    @Test
    @Transactional
    void getNonExistingPayout() throws Exception {
        // Get the payout
        restPayoutMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingPayout() throws Exception {
        // Initialize the database
        insertedPayout = payoutRepository.saveAndFlush(payout);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the payout
        Payout updatedPayout = payoutRepository.findById(payout.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedPayout are not directly saved in db
        em.detach(updatedPayout);
        updatedPayout
            .reference(UPDATED_REFERENCE)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .periodStart(UPDATED_PERIOD_START)
            .periodEnd(UPDATED_PERIOD_END)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .settledOn(UPDATED_SETTLED_ON)
            .bankReference(UPDATED_BANK_REFERENCE);
        PayoutDTO payoutDTO = payoutMapper.toDto(updatedPayout);

        restPayoutMockMvc
            .perform(
                put(ENTITY_API_URL_ID, payoutDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO))
            )
            .andExpect(status().isOk());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPayoutToMatchAllProperties(updatedPayout);
    }

    @Test
    @Transactional
    void putNonExistingPayout() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        payout.setId(longCount.incrementAndGet());

        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPayoutMockMvc
            .perform(
                put(ENTITY_API_URL_ID, payoutDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchPayout() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        payout.setId(longCount.incrementAndGet());

        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPayoutMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(payoutDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamPayout() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        payout.setId(longCount.incrementAndGet());

        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPayoutMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdatePayoutWithPatch() throws Exception {
        // Initialize the database
        insertedPayout = payoutRepository.saveAndFlush(payout);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the payout using partial update
        Payout partialUpdatedPayout = new Payout();
        partialUpdatedPayout.setId(payout.getId());

        partialUpdatedPayout
            .reference(UPDATED_REFERENCE)
            .periodStart(UPDATED_PERIOD_START)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .settledOn(UPDATED_SETTLED_ON);

        restPayoutMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPayout.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPayout))
            )
            .andExpect(status().isOk());

        // Validate the Payout in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPayoutUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedPayout, payout), getPersistedPayout(payout));
    }

    @Test
    @Transactional
    void fullUpdatePayoutWithPatch() throws Exception {
        // Initialize the database
        insertedPayout = payoutRepository.saveAndFlush(payout);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the payout using partial update
        Payout partialUpdatedPayout = new Payout();
        partialUpdatedPayout.setId(payout.getId());

        partialUpdatedPayout
            .reference(UPDATED_REFERENCE)
            .professionalRef(UPDATED_PROFESSIONAL_REF)
            .periodStart(UPDATED_PERIOD_START)
            .periodEnd(UPDATED_PERIOD_END)
            .grossMinor(UPDATED_GROSS_MINOR)
            .commissionMinor(UPDATED_COMMISSION_MINOR)
            .netMinor(UPDATED_NET_MINOR)
            .currency(UPDATED_CURRENCY)
            .status(UPDATED_STATUS)
            .settledOn(UPDATED_SETTLED_ON)
            .bankReference(UPDATED_BANK_REFERENCE);

        restPayoutMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPayout.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPayout))
            )
            .andExpect(status().isOk());

        // Validate the Payout in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPayoutUpdatableFieldsEquals(partialUpdatedPayout, getPersistedPayout(partialUpdatedPayout));
    }

    @Test
    @Transactional
    void patchNonExistingPayout() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        payout.setId(longCount.incrementAndGet());

        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPayoutMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, payoutDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(payoutDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchPayout() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        payout.setId(longCount.incrementAndGet());

        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPayoutMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(payoutDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamPayout() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        payout.setId(longCount.incrementAndGet());

        // Create the Payout
        PayoutDTO payoutDTO = payoutMapper.toDto(payout);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPayoutMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(payoutDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Payout in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deletePayout() throws Exception {
        // Initialize the database
        insertedPayout = payoutRepository.saveAndFlush(payout);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the payout
        restPayoutMockMvc
            .perform(delete(ENTITY_API_URL_ID, payout.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return payoutRepository.count();
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

    protected Payout getPersistedPayout(Payout payout) {
        return payoutRepository.findById(payout.getId()).orElseThrow();
    }

    protected void assertPersistedPayoutToMatchAllProperties(Payout expectedPayout) {
        assertPayoutAllPropertiesEquals(expectedPayout, getPersistedPayout(expectedPayout));
    }

    protected void assertPersistedPayoutToMatchUpdatableProperties(Payout expectedPayout) {
        assertPayoutAllUpdatablePropertiesEquals(expectedPayout, getPersistedPayout(expectedPayout));
    }
}
