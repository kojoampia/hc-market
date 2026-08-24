package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.service.dto.BrokerageConfigDTO;
import net.jojoaddison.service.mapper.BrokerageConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.BrokerageConfig}.
 */
@Service
@Transactional
public class BrokerageConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerageConfigService.class);

    private final BrokerageConfigRepository brokerageConfigRepository;

    private final BrokerageConfigMapper brokerageConfigMapper;

    public BrokerageConfigService(BrokerageConfigRepository brokerageConfigRepository, BrokerageConfigMapper brokerageConfigMapper) {
        this.brokerageConfigRepository = brokerageConfigRepository;
        this.brokerageConfigMapper = brokerageConfigMapper;
    }

    /**
     * Save a brokerageConfig.
     *
     * @param brokerageConfigDTO the entity to save.
     * @return the persisted entity.
     */
    public BrokerageConfigDTO save(BrokerageConfigDTO brokerageConfigDTO) {
        LOG.debug("Request to save BrokerageConfig : {}", brokerageConfigDTO);
        BrokerageConfig brokerageConfig = brokerageConfigMapper.toEntity(brokerageConfigDTO);
        brokerageConfig = brokerageConfigRepository.save(brokerageConfig);
        return brokerageConfigMapper.toDto(brokerageConfig);
    }

    /**
     * Update a brokerageConfig.
     *
     * @param brokerageConfigDTO the entity to save.
     * @return the persisted entity.
     */
    public BrokerageConfigDTO update(BrokerageConfigDTO brokerageConfigDTO) {
        LOG.debug("Request to update BrokerageConfig : {}", brokerageConfigDTO);
        BrokerageConfig brokerageConfig = brokerageConfigMapper.toEntity(brokerageConfigDTO);
        brokerageConfig = brokerageConfigRepository.save(brokerageConfig);
        return brokerageConfigMapper.toDto(brokerageConfig);
    }

    /**
     * Partially update a brokerageConfig.
     *
     * @param brokerageConfigDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<BrokerageConfigDTO> partialUpdate(BrokerageConfigDTO brokerageConfigDTO) {
        LOG.debug("Request to partially update BrokerageConfig : {}", brokerageConfigDTO);

        return brokerageConfigRepository
            .findById(brokerageConfigDTO.getId())
            .map(existingBrokerageConfig -> {
                brokerageConfigMapper.partialUpdate(existingBrokerageConfig, brokerageConfigDTO);

                return existingBrokerageConfig;
            })
            .map(brokerageConfigRepository::save)
            .map(brokerageConfigMapper::toDto);
    }

    /**
     * Get all the brokerageConfigs.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<BrokerageConfigDTO> findAll() {
        LOG.debug("Request to get all BrokerageConfigs");
        return brokerageConfigRepository
            .findAll()
            .stream()
            .map(brokerageConfigMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one brokerageConfig by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<BrokerageConfigDTO> findOne(Long id) {
        LOG.debug("Request to get BrokerageConfig : {}", id);
        return brokerageConfigRepository.findById(id).map(brokerageConfigMapper::toDto);
    }

    /**
     * Delete the brokerageConfig by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete BrokerageConfig : {}", id);
        brokerageConfigRepository.deleteById(id);
    }
}
