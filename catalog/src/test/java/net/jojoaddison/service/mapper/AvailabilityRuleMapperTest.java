package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.AvailabilityRuleAsserts.*;
import static net.jojoaddison.domain.AvailabilityRuleTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityRuleMapperTest {

    private AvailabilityRuleMapper availabilityRuleMapper;

    @BeforeEach
    void setUp() {
        availabilityRuleMapper = new AvailabilityRuleMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getAvailabilityRuleSample1();
        var actual = availabilityRuleMapper.toEntity(availabilityRuleMapper.toDto(expected));
        assertAvailabilityRuleAllPropertiesEquals(expected, actual);
    }
}
