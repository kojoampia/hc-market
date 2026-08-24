package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.BrokerageConfigAsserts.*;
import static net.jojoaddison.domain.BrokerageConfigTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrokerageConfigMapperTest {

    private BrokerageConfigMapper brokerageConfigMapper;

    @BeforeEach
    void setUp() {
        brokerageConfigMapper = new BrokerageConfigMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBrokerageConfigSample1();
        var actual = brokerageConfigMapper.toEntity(brokerageConfigMapper.toDto(expected));
        assertBrokerageConfigAllPropertiesEquals(expected, actual);
    }
}
