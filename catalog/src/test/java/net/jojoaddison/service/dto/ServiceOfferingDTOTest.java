package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ServiceOfferingDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ServiceOfferingDTO.class);
        ServiceOfferingDTO serviceOfferingDTO1 = new ServiceOfferingDTO();
        serviceOfferingDTO1.setId(1L);
        ServiceOfferingDTO serviceOfferingDTO2 = new ServiceOfferingDTO();
        assertThat(serviceOfferingDTO1).isNotEqualTo(serviceOfferingDTO2);
        serviceOfferingDTO2.setId(serviceOfferingDTO1.getId());
        assertThat(serviceOfferingDTO1).isEqualTo(serviceOfferingDTO2);
        serviceOfferingDTO2.setId(2L);
        assertThat(serviceOfferingDTO1).isNotEqualTo(serviceOfferingDTO2);
        serviceOfferingDTO1.setId(null);
        assertThat(serviceOfferingDTO1).isNotEqualTo(serviceOfferingDTO2);
    }
}
