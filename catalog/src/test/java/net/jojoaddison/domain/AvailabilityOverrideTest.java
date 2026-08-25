package net.jojoaddison.domain;

import static net.jojoaddison.domain.AvailabilityOverrideTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AvailabilityOverrideTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AvailabilityOverride.class);
        AvailabilityOverride availabilityOverride1 = getAvailabilityOverrideSample1();
        AvailabilityOverride availabilityOverride2 = new AvailabilityOverride();
        assertThat(availabilityOverride1).isNotEqualTo(availabilityOverride2);

        availabilityOverride2.setId(availabilityOverride1.getId());
        assertThat(availabilityOverride1).isEqualTo(availabilityOverride2);

        availabilityOverride2 = getAvailabilityOverrideSample2();
        assertThat(availabilityOverride1).isNotEqualTo(availabilityOverride2);
    }

    @Test
    void professionalTest() {
        AvailabilityOverride availabilityOverride = getAvailabilityOverrideRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        availabilityOverride.setProfessional(professionalBack);
        assertThat(availabilityOverride.getProfessional()).isEqualTo(professionalBack);

        availabilityOverride.professional(null);
        assertThat(availabilityOverride.getProfessional()).isNull();
    }
}
