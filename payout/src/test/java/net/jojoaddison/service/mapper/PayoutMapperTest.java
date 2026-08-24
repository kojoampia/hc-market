package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.PayoutAsserts.*;
import static net.jojoaddison.domain.PayoutTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayoutMapperTest {

    private PayoutMapper payoutMapper;

    @BeforeEach
    void setUp() {
        payoutMapper = new PayoutMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPayoutSample1();
        var actual = payoutMapper.toEntity(payoutMapper.toDto(expected));
        assertPayoutAllPropertiesEquals(expected, actual);
    }
}
