package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BookingDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(BookingDTO.class);
        BookingDTO bookingDTO1 = new BookingDTO();
        bookingDTO1.setId(1L);
        BookingDTO bookingDTO2 = new BookingDTO();
        assertThat(bookingDTO1).isNotEqualTo(bookingDTO2);
        bookingDTO2.setId(bookingDTO1.getId());
        assertThat(bookingDTO1).isEqualTo(bookingDTO2);
        bookingDTO2.setId(2L);
        assertThat(bookingDTO1).isNotEqualTo(bookingDTO2);
        bookingDTO1.setId(null);
        assertThat(bookingDTO1).isNotEqualTo(bookingDTO2);
    }
}
