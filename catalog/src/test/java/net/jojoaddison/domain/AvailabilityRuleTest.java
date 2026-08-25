package net.jojoaddison.domain;

import static net.jojoaddison.domain.AvailabilityRuleTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AvailabilityRuleTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AvailabilityRule.class);
        AvailabilityRule availabilityRule1 = getAvailabilityRuleSample1();
        AvailabilityRule availabilityRule2 = new AvailabilityRule();
        assertThat(availabilityRule1).isNotEqualTo(availabilityRule2);

        availabilityRule2.setId(availabilityRule1.getId());
        assertThat(availabilityRule1).isEqualTo(availabilityRule2);

        availabilityRule2 = getAvailabilityRuleSample2();
        assertThat(availabilityRule1).isNotEqualTo(availabilityRule2);
    }

    @Test
    void professionalTest() {
        AvailabilityRule availabilityRule = getAvailabilityRuleRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        availabilityRule.setProfessional(professionalBack);
        assertThat(availabilityRule.getProfessional()).isEqualTo(professionalBack);

        availabilityRule.professional(null);
        assertThat(availabilityRule.getProfessional()).isNull();
    }
}
