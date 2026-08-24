package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BookingStatusChangeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(BookingStatusChangeDTO.class);
        BookingStatusChangeDTO bookingStatusChangeDTO1 = new BookingStatusChangeDTO();
        bookingStatusChangeDTO1.setId(1L);
        BookingStatusChangeDTO bookingStatusChangeDTO2 = new BookingStatusChangeDTO();
        assertThat(bookingStatusChangeDTO1).isNotEqualTo(bookingStatusChangeDTO2);
        bookingStatusChangeDTO2.setId(bookingStatusChangeDTO1.getId());
        assertThat(bookingStatusChangeDTO1).isEqualTo(bookingStatusChangeDTO2);
        bookingStatusChangeDTO2.setId(2L);
        assertThat(bookingStatusChangeDTO1).isNotEqualTo(bookingStatusChangeDTO2);
        bookingStatusChangeDTO1.setId(null);
        assertThat(bookingStatusChangeDTO1).isNotEqualTo(bookingStatusChangeDTO2);
    }
}
