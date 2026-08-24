package net.jojoaddison.service.mapper;

import static net.jojoaddison.domain.BookingAsserts.*;
import static net.jojoaddison.domain.BookingTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingMapperTest {

    private BookingMapper bookingMapper;

    @BeforeEach
    void setUp() {
        bookingMapper = new BookingMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBookingSample1();
        var actual = bookingMapper.toEntity(bookingMapper.toDto(expected));
        assertBookingAllPropertiesEquals(expected, actual);
    }
}
