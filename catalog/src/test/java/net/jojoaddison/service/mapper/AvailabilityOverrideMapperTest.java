package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.AvailabilityOverrideAsserts.*;
import static net.jojoaddison.domain.AvailabilityOverrideTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityOverrideMapperTest {

    private AvailabilityOverrideMapper availabilityOverrideMapper;

    @BeforeEach
    void setUp() {
        availabilityOverrideMapper = new AvailabilityOverrideMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getAvailabilityOverrideSample1();
        var actual = availabilityOverrideMapper.toEntity(availabilityOverrideMapper.toDto(expected));
        assertAvailabilityOverrideAllPropertiesEquals(expected, actual);
    }
}
