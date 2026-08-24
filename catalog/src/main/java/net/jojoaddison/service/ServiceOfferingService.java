package net.jojoaddison.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.repository.ServiceOfferingRepository;
import net.jojoaddison.service.dto.ServiceOfferingDTO;
import net.jojoaddison.service.mapper.ServiceOfferingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.ServiceOffering}.
 */
@Service
@Transactional
public class ServiceOfferingService {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceOfferingService.class);

    private final ServiceOfferingRepository serviceOfferingRepository;

    private final ServiceOfferingMapper serviceOfferingMapper;

    public ServiceOfferingService(ServiceOfferingRepository serviceOfferingRepository, ServiceOfferingMapper serviceOfferingMapper) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.serviceOfferingMapper = serviceOfferingMapper;
    }

    /**
     * Save a serviceOffering.
     *
     * @param serviceOfferingDTO the entity to save.
     * @return the persisted entity.
     */
    public ServiceOfferingDTO save(ServiceOfferingDTO serviceOfferingDTO) {
        LOG.debug("Request to save ServiceOffering : {}", serviceOfferingDTO);
        ServiceOffering serviceOffering = serviceOfferingMapper.toEntity(serviceOfferingDTO);
        serviceOffering = serviceOfferingRepository.save(serviceOffering);
        return serviceOfferingMapper.toDto(serviceOffering);
    }

    /**
     * Update a serviceOffering.
     *
     * @param serviceOfferingDTO the entity to save.
     * @return the persisted entity.
     */
    public ServiceOfferingDTO update(ServiceOfferingDTO serviceOfferingDTO) {
        LOG.debug("Request to update ServiceOffering : {}", serviceOfferingDTO);
        ServiceOffering serviceOffering = serviceOfferingMapper.toEntity(serviceOfferingDTO);
        serviceOffering = serviceOfferingRepository.save(serviceOffering);
        return serviceOfferingMapper.toDto(serviceOffering);
    }

    /**
     * Partially update a serviceOffering.
     *
     * @param serviceOfferingDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ServiceOfferingDTO> partialUpdate(ServiceOfferingDTO serviceOfferingDTO) {
        LOG.debug("Request to partially update ServiceOffering : {}", serviceOfferingDTO);

        return serviceOfferingRepository
            .findById(serviceOfferingDTO.getId())
            .map(existingServiceOffering -> {
                serviceOfferingMapper.partialUpdate(existingServiceOffering, serviceOfferingDTO);

                return existingServiceOffering;
            })
            .map(serviceOfferingRepository::save)
            .map(serviceOfferingMapper::toDto);
    }

    /**
     * Get all the serviceOfferings.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<ServiceOfferingDTO> findAll() {
        LOG.debug("Request to get all ServiceOfferings");
        return serviceOfferingRepository
            .findAll()
            .stream()
            .map(serviceOfferingMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one serviceOffering by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ServiceOfferingDTO> findOne(Long id) {
        LOG.debug("Request to get ServiceOffering : {}", id);
        return serviceOfferingRepository.findById(id).map(serviceOfferingMapper::toDto);
    }

    /**
     * Delete the serviceOffering by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ServiceOffering : {}", id);
        serviceOfferingRepository.deleteById(id);
    }
}
