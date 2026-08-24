package net.jojoaddison.domain;

import static net.jojoaddison.domain.BookingStatusChangeTestSamples.*;
import static net.jojoaddison.domain.BookingTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BookingStatusChangeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(BookingStatusChange.class);
        BookingStatusChange bookingStatusChange1 = getBookingStatusChangeSample1();
        BookingStatusChange bookingStatusChange2 = new BookingStatusChange();
        assertThat(bookingStatusChange1).isNotEqualTo(bookingStatusChange2);

        bookingStatusChange2.setId(bookingStatusChange1.getId());
        assertThat(bookingStatusChange1).isEqualTo(bookingStatusChange2);

        bookingStatusChange2 = getBookingStatusChangeSample2();
        assertThat(bookingStatusChange1).isNotEqualTo(bookingStatusChange2);
    }

    @Test
    void bookingTest() {
        BookingStatusChange bookingStatusChange = getBookingStatusChangeRandomSampleGenerator();
        Booking bookingBack = getBookingRandomSampleGenerator();

        bookingStatusChange.setBooking(bookingBack);
        assertThat(bookingStatusChange.getBooking()).isEqualTo(bookingBack);

        bookingStatusChange.booking(null);
        assertThat(bookingStatusChange.getBooking()).isNull();
    }
}
