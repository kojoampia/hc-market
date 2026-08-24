package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.ServiceOfferingAsserts.*;
import static net.jojoaddison.domain.ServiceOfferingTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceOfferingMapperTest {

    private ServiceOfferingMapper serviceOfferingMapper;

    @BeforeEach
    void setUp() {
        serviceOfferingMapper = new ServiceOfferingMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getServiceOfferingSample1();
        var actual = serviceOfferingMapper.toEntity(serviceOfferingMapper.toDto(expected));
        assertServiceOfferingAllPropertiesEquals(expected, actual);
    }
}
