package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.BookingStatusChangeAsserts.*;
import static net.jojoaddison.domain.BookingStatusChangeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingStatusChangeMapperTest {

    private BookingStatusChangeMapper bookingStatusChangeMapper;

    @BeforeEach
    void setUp() {
        bookingStatusChangeMapper = new BookingStatusChangeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBookingStatusChangeSample1();
        var actual = bookingStatusChangeMapper.toEntity(bookingStatusChangeMapper.toDto(expected));
        assertBookingStatusChangeAllPropertiesEquals(expected, actual);
    }
}
