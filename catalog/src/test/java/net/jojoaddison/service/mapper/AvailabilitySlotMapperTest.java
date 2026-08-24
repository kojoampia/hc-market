package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.AvailabilitySlotAsserts.*;
import static net.jojoaddison.domain.AvailabilitySlotTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilitySlotMapperTest {

    private AvailabilitySlotMapper availabilitySlotMapper;

    @BeforeEach
    void setUp() {
        availabilitySlotMapper = new AvailabilitySlotMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getAvailabilitySlotSample1();
        var actual = availabilitySlotMapper.toEntity(availabilitySlotMapper.toDto(expected));
        assertAvailabilitySlotAllPropertiesEquals(expected, actual);
    }
}
