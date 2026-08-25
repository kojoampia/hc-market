package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.DisputeAsserts.*;
import static net.jojoaddison.domain.DisputeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisputeMapperTest {

    private DisputeMapper disputeMapper;

    @BeforeEach
    void setUp() {
        disputeMapper = new DisputeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDisputeSample1();
        var actual = disputeMapper.toEntity(disputeMapper.toDto(expected));
        assertDisputeAllPropertiesEquals(expected, actual);
    }
}
