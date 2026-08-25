package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.DisputeStatusChangeAsserts.*;
import static net.jojoaddison.domain.DisputeStatusChangeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisputeStatusChangeMapperTest {

    private DisputeStatusChangeMapper disputeStatusChangeMapper;

    @BeforeEach
    void setUp() {
        disputeStatusChangeMapper = new DisputeStatusChangeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDisputeStatusChangeSample1();
        var actual = disputeStatusChangeMapper.toEntity(disputeStatusChangeMapper.toDto(expected));
        assertDisputeStatusChangeAllPropertiesEquals(expected, actual);
    }
}
