package net.jojoaddison.domain;

import static net.jojoaddison.domain.AvailabilitySlotTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AvailabilitySlotTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AvailabilitySlot.class);
        AvailabilitySlot availabilitySlot1 = getAvailabilitySlotSample1();
        AvailabilitySlot availabilitySlot2 = new AvailabilitySlot();
        assertThat(availabilitySlot1).isNotEqualTo(availabilitySlot2);

        availabilitySlot2.setId(availabilitySlot1.getId());
        assertThat(availabilitySlot1).isEqualTo(availabilitySlot2);

        availabilitySlot2 = getAvailabilitySlotSample2();
        assertThat(availabilitySlot1).isNotEqualTo(availabilitySlot2);
    }

    @Test
    void professionalTest() {
        AvailabilitySlot availabilitySlot = getAvailabilitySlotRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        availabilitySlot.setProfessional(professionalBack);
        assertThat(availabilitySlot.getProfessional()).isEqualTo(professionalBack);

        availabilitySlot.professional(null);
        assertThat(availabilitySlot.getProfessional()).isNull();
    }
}
