package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AvailabilityRuleDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(AvailabilityRuleDTO.class);
        AvailabilityRuleDTO availabilityRuleDTO1 = new AvailabilityRuleDTO();
        availabilityRuleDTO1.setId(1L);
        AvailabilityRuleDTO availabilityRuleDTO2 = new AvailabilityRuleDTO();
        assertThat(availabilityRuleDTO1).isNotEqualTo(availabilityRuleDTO2);
        availabilityRuleDTO2.setId(availabilityRuleDTO1.getId());
        assertThat(availabilityRuleDTO1).isEqualTo(availabilityRuleDTO2);
        availabilityRuleDTO2.setId(2L);
        assertThat(availabilityRuleDTO1).isNotEqualTo(availabilityRuleDTO2);
        availabilityRuleDTO1.setId(null);
        assertThat(availabilityRuleDTO1).isNotEqualTo(availabilityRuleDTO2);
    }
}
