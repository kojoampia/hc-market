package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AvailabilityOverrideDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(AvailabilityOverrideDTO.class);
        AvailabilityOverrideDTO availabilityOverrideDTO1 = new AvailabilityOverrideDTO();
        availabilityOverrideDTO1.setId(1L);
        AvailabilityOverrideDTO availabilityOverrideDTO2 = new AvailabilityOverrideDTO();
        assertThat(availabilityOverrideDTO1).isNotEqualTo(availabilityOverrideDTO2);
        availabilityOverrideDTO2.setId(availabilityOverrideDTO1.getId());
        assertThat(availabilityOverrideDTO1).isEqualTo(availabilityOverrideDTO2);
        availabilityOverrideDTO2.setId(2L);
        assertThat(availabilityOverrideDTO1).isNotEqualTo(availabilityOverrideDTO2);
        availabilityOverrideDTO1.setId(null);
        assertThat(availabilityOverrideDTO1).isNotEqualTo(availabilityOverrideDTO2);
    }
}
