package net.jojoaddison.domain;

import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.ServiceOfferingTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ServiceOfferingTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ServiceOffering.class);
        ServiceOffering serviceOffering1 = getServiceOfferingSample1();
        ServiceOffering serviceOffering2 = new ServiceOffering();
        assertThat(serviceOffering1).isNotEqualTo(serviceOffering2);

        serviceOffering2.setId(serviceOffering1.getId());
        assertThat(serviceOffering1).isEqualTo(serviceOffering2);

        serviceOffering2 = getServiceOfferingSample2();
        assertThat(serviceOffering1).isNotEqualTo(serviceOffering2);
    }

    @Test
    void professionalTest() {
        ServiceOffering serviceOffering = getServiceOfferingRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        serviceOffering.setProfessional(professionalBack);
        assertThat(serviceOffering.getProfessional()).isEqualTo(professionalBack);

        serviceOffering.professional(null);
        assertThat(serviceOffering.getProfessional()).isNull();
    }
}
