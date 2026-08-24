package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.BrokerageConfigAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static net.jojoaddison.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.service.dto.BrokerageConfigDTO;
import net.jojoaddison.service.mapper.BrokerageConfigMapper;
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
 * Integration tests for the {@link BrokerageConfigResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class BrokerageConfigResourceIT {

    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal(0);
    private static final BigDecimal UPDATED_COMMISSION_RATE = new BigDecimal(1);

    private static final Integer DEFAULT_PAYOUT_LAG_DAYS = 1;
    private static final Integer UPDATED_PAYOUT_LAG_DAYS = 2;

    private static final Integer DEFAULT_FREE_CANCELLATION_HOURS = 1;
    private static final Integer UPDATED_FREE_CANCELLATION_HOURS = 2;

    private static final BigDecimal DEFAULT_LATE_CANCELLATION_PCT = new BigDecimal(1);
    private static final BigDecimal UPDATED_LATE_CANCELLATION_PCT = new BigDecimal(2);

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final Instant DEFAULT_EFFECTIVE_FROM = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_EFFECTIVE_FROM = Instant.ofEpochMilli(1701659855169L);

    private static final String ENTITY_API_URL = "/api/brokerage-configs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BrokerageConfigRepository brokerageConfigRepository;

    @Autowired
    private BrokerageConfigMapper brokerageConfigMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restBrokerageConfigMockMvc;

    private BrokerageConfig brokerageConfig;

    private BrokerageConfig insertedBrokerageConfig;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static BrokerageConfig createEntity() {
        return new BrokerageConfig()
            .commissionRate(DEFAULT_COMMISSION_RATE)
            .payoutLagDays(DEFAULT_PAYOUT_LAG_DAYS)
            .freeCancellationHours(DEFAULT_FREE_CANCELLATION_HOURS)
            .lateCancellationPct(DEFAULT_LATE_CANCELLATION_PCT)
            .currency(DEFAULT_CURRENCY)
            .effectiveFrom(DEFAULT_EFFECTIVE_FROM);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static BrokerageConfig createUpdatedEntity() {
        return new BrokerageConfig()
            .commissionRate(UPDATED_COMMISSION_RATE)
            .payoutLagDays(UPDATED_PAYOUT_LAG_DAYS)
            .freeCancellationHours(UPDATED_FREE_CANCELLATION_HOURS)
            .lateCancellationPct(UPDATED_LATE_CANCELLATION_PCT)
            .currency(UPDATED_CURRENCY)
            .effectiveFrom(UPDATED_EFFECTIVE_FROM);
    }

    @BeforeEach
    void initTest() {
        brokerageConfig = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedBrokerageConfig != null) {
            brokerageConfigRepository.delete(insertedBrokerageConfig);
            insertedBrokerageConfig = null;
        }
    }

    @Test
    @Transactional
    void createBrokerageConfig() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);
        var returnedBrokerageConfigDTO = om.readValue(
            restBrokerageConfigMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BrokerageConfigDTO.class
        );

        // Validate the BrokerageConfig in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedBrokerageConfig = brokerageConfigMapper.toEntity(returnedBrokerageConfigDTO);
        assertBrokerageConfigUpdatableFieldsEquals(returnedBrokerageConfig, getPersistedBrokerageConfig(returnedBrokerageConfig));

        insertedBrokerageConfig = returnedBrokerageConfig;
    }

    @Test
    @Transactional
    void createBrokerageConfigWithExistingId() throws Exception {
        // Create the BrokerageConfig with an existing ID
        brokerageConfig.setId(1L);
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkCommissionRateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        brokerageConfig.setCommissionRate(null);

        // Create the BrokerageConfig, which fails.
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPayoutLagDaysIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        brokerageConfig.setPayoutLagDays(null);

        // Create the BrokerageConfig, which fails.
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFreeCancellationHoursIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        brokerageConfig.setFreeCancellationHours(null);

        // Create the BrokerageConfig, which fails.
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkLateCancellationPctIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        brokerageConfig.setLateCancellationPct(null);

        // Create the BrokerageConfig, which fails.
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        brokerageConfig.setCurrency(null);

        // Create the BrokerageConfig, which fails.
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEffectiveFromIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        brokerageConfig.setEffectiveFrom(null);

        // Create the BrokerageConfig, which fails.
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        restBrokerageConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllBrokerageConfigs() throws Exception {
        // Initialize the database
        insertedBrokerageConfig = brokerageConfigRepository.saveAndFlush(brokerageConfig);

        // Get all the brokerageConfigList
        restBrokerageConfigMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(brokerageConfig.getId().intValue())))
            .andExpect(jsonPath("$.[*].commissionRate").value(hasItem(sameNumber(DEFAULT_COMMISSION_RATE))))
            .andExpect(jsonPath("$.[*].payoutLagDays").value(hasItem(DEFAULT_PAYOUT_LAG_DAYS)))
            .andExpect(jsonPath("$.[*].freeCancellationHours").value(hasItem(DEFAULT_FREE_CANCELLATION_HOURS)))
            .andExpect(jsonPath("$.[*].lateCancellationPct").value(hasItem(sameNumber(DEFAULT_LATE_CANCELLATION_PCT))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].effectiveFrom").value(hasItem(DEFAULT_EFFECTIVE_FROM.toString())));
    }

    @Test
    @Transactional
    void getBrokerageConfig() throws Exception {
        // Initialize the database
        insertedBrokerageConfig = brokerageConfigRepository.saveAndFlush(brokerageConfig);

        // Get the brokerageConfig
        restBrokerageConfigMockMvc
            .perform(get(ENTITY_API_URL_ID, brokerageConfig.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(brokerageConfig.getId().intValue()))
            .andExpect(jsonPath("$.commissionRate").value(sameNumber(DEFAULT_COMMISSION_RATE)))
            .andExpect(jsonPath("$.payoutLagDays").value(DEFAULT_PAYOUT_LAG_DAYS))
            .andExpect(jsonPath("$.freeCancellationHours").value(DEFAULT_FREE_CANCELLATION_HOURS))
            .andExpect(jsonPath("$.lateCancellationPct").value(sameNumber(DEFAULT_LATE_CANCELLATION_PCT)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.effectiveFrom").value(DEFAULT_EFFECTIVE_FROM.toString()));
    }

    @Test
    @Transactional
    void getNonExistingBrokerageConfig() throws Exception {
        // Get the brokerageConfig
        restBrokerageConfigMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingBrokerageConfig() throws Exception {
        // Initialize the database
        insertedBrokerageConfig = brokerageConfigRepository.saveAndFlush(brokerageConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the brokerageConfig
        BrokerageConfig updatedBrokerageConfig = brokerageConfigRepository.findById(brokerageConfig.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedBrokerageConfig are not directly saved in db
        em.detach(updatedBrokerageConfig);
        updatedBrokerageConfig
            .commissionRate(UPDATED_COMMISSION_RATE)
            .payoutLagDays(UPDATED_PAYOUT_LAG_DAYS)
            .freeCancellationHours(UPDATED_FREE_CANCELLATION_HOURS)
            .lateCancellationPct(UPDATED_LATE_CANCELLATION_PCT)
            .currency(UPDATED_CURRENCY)
            .effectiveFrom(UPDATED_EFFECTIVE_FROM);
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(updatedBrokerageConfig);

        restBrokerageConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, brokerageConfigDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(brokerageConfigDTO))
            )
            .andExpect(status().isOk());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedBrokerageConfigToMatchAllProperties(updatedBrokerageConfig);
    }

    @Test
    @Transactional
    void putNonExistingBrokerageConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        brokerageConfig.setId(longCount.incrementAndGet());

        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBrokerageConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, brokerageConfigDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(brokerageConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchBrokerageConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        brokerageConfig.setId(longCount.incrementAndGet());

        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBrokerageConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(brokerageConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamBrokerageConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        brokerageConfig.setId(longCount.incrementAndGet());

        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBrokerageConfigMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateBrokerageConfigWithPatch() throws Exception {
        // Initialize the database
        insertedBrokerageConfig = brokerageConfigRepository.saveAndFlush(brokerageConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the brokerageConfig using partial update
        BrokerageConfig partialUpdatedBrokerageConfig = new BrokerageConfig();
        partialUpdatedBrokerageConfig.setId(brokerageConfig.getId());

        partialUpdatedBrokerageConfig
            .commissionRate(UPDATED_COMMISSION_RATE)
            .freeCancellationHours(UPDATED_FREE_CANCELLATION_HOURS)
            .currency(UPDATED_CURRENCY)
            .effectiveFrom(UPDATED_EFFECTIVE_FROM);

        restBrokerageConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBrokerageConfig.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBrokerageConfig))
            )
            .andExpect(status().isOk());

        // Validate the BrokerageConfig in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBrokerageConfigUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedBrokerageConfig, brokerageConfig),
            getPersistedBrokerageConfig(brokerageConfig)
        );
    }

    @Test
    @Transactional
    void fullUpdateBrokerageConfigWithPatch() throws Exception {
        // Initialize the database
        insertedBrokerageConfig = brokerageConfigRepository.saveAndFlush(brokerageConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the brokerageConfig using partial update
        BrokerageConfig partialUpdatedBrokerageConfig = new BrokerageConfig();
        partialUpdatedBrokerageConfig.setId(brokerageConfig.getId());

        partialUpdatedBrokerageConfig
            .commissionRate(UPDATED_COMMISSION_RATE)
            .payoutLagDays(UPDATED_PAYOUT_LAG_DAYS)
            .freeCancellationHours(UPDATED_FREE_CANCELLATION_HOURS)
            .lateCancellationPct(UPDATED_LATE_CANCELLATION_PCT)
            .currency(UPDATED_CURRENCY)
            .effectiveFrom(UPDATED_EFFECTIVE_FROM);

        restBrokerageConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBrokerageConfig.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBrokerageConfig))
            )
            .andExpect(status().isOk());

        // Validate the BrokerageConfig in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBrokerageConfigUpdatableFieldsEquals(
            partialUpdatedBrokerageConfig,
            getPersistedBrokerageConfig(partialUpdatedBrokerageConfig)
        );
    }

    @Test
    @Transactional
    void patchNonExistingBrokerageConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        brokerageConfig.setId(longCount.incrementAndGet());

        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBrokerageConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, brokerageConfigDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(brokerageConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchBrokerageConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        brokerageConfig.setId(longCount.incrementAndGet());

        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBrokerageConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(brokerageConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamBrokerageConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        brokerageConfig.setId(longCount.incrementAndGet());

        // Create the BrokerageConfig
        BrokerageConfigDTO brokerageConfigDTO = brokerageConfigMapper.toDto(brokerageConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBrokerageConfigMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(brokerageConfigDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the BrokerageConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteBrokerageConfig() throws Exception {
        // Initialize the database
        insertedBrokerageConfig = brokerageConfigRepository.saveAndFlush(brokerageConfig);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the brokerageConfig
        restBrokerageConfigMockMvc
            .perform(delete(ENTITY_API_URL_ID, brokerageConfig.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return brokerageConfigRepository.count();
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

    protected BrokerageConfig getPersistedBrokerageConfig(BrokerageConfig brokerageConfig) {
        return brokerageConfigRepository.findById(brokerageConfig.getId()).orElseThrow();
    }

    protected void assertPersistedBrokerageConfigToMatchAllProperties(BrokerageConfig expectedBrokerageConfig) {
        assertBrokerageConfigAllPropertiesEquals(expectedBrokerageConfig, getPersistedBrokerageConfig(expectedBrokerageConfig));
    }

    protected void assertPersistedBrokerageConfigToMatchUpdatableProperties(BrokerageConfig expectedBrokerageConfig) {
        assertBrokerageConfigAllUpdatablePropertiesEquals(expectedBrokerageConfig, getPersistedBrokerageConfig(expectedBrokerageConfig));
    }
}
