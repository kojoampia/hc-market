package net.jojoaddison.domain;

import static net.jojoaddison.domain.BookingStatusChangeTestSamples.*;
import static net.jojoaddison.domain.BookingTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BookingTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Booking.class);
        Booking booking1 = getBookingSample1();
        Booking booking2 = new Booking();
        assertThat(booking1).isNotEqualTo(booking2);

        booking2.setId(booking1.getId());
        assertThat(booking1).isEqualTo(booking2);

        booking2 = getBookingSample2();
        assertThat(booking1).isNotEqualTo(booking2);
    }

    @Test
    void historyTest() {
        Booking booking = getBookingRandomSampleGenerator();
        BookingStatusChange bookingStatusChangeBack = getBookingStatusChangeRandomSampleGenerator();

        booking.addHistory(bookingStatusChangeBack);
        assertThat(booking.getHistories()).containsOnly(bookingStatusChangeBack);
        assertThat(bookingStatusChangeBack.getBooking()).isEqualTo(booking);

        booking.removeHistory(bookingStatusChangeBack);
        assertThat(booking.getHistories()).doesNotContain(bookingStatusChangeBack);
        assertThat(bookingStatusChangeBack.getBooking()).isNull();

        booking.histories(new HashSet<>(Set.of(bookingStatusChangeBack)));
        assertThat(booking.getHistories()).containsOnly(bookingStatusChangeBack);
        assertThat(bookingStatusChangeBack.getBooking()).isEqualTo(booking);

        booking.setHistories(new HashSet<>());
        assertThat(booking.getHistories()).doesNotContain(bookingStatusChangeBack);
        assertThat(bookingStatusChangeBack.getBooking()).isNull();
    }
}
