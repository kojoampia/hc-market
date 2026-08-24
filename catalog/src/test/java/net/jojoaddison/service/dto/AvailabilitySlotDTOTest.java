package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AvailabilitySlotDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(AvailabilitySlotDTO.class);
        AvailabilitySlotDTO availabilitySlotDTO1 = new AvailabilitySlotDTO();
        availabilitySlotDTO1.setId(1L);
        AvailabilitySlotDTO availabilitySlotDTO2 = new AvailabilitySlotDTO();
        assertThat(availabilitySlotDTO1).isNotEqualTo(availabilitySlotDTO2);
        availabilitySlotDTO2.setId(availabilitySlotDTO1.getId());
        assertThat(availabilitySlotDTO1).isEqualTo(availabilitySlotDTO2);
        availabilitySlotDTO2.setId(2L);
        assertThat(availabilitySlotDTO1).isNotEqualTo(availabilitySlotDTO2);
        availabilitySlotDTO1.setId(null);
        assertThat(availabilitySlotDTO1).isNotEqualTo(availabilitySlotDTO2);
    }
}
