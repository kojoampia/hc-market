package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BrokerageConfigDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(BrokerageConfigDTO.class);
        BrokerageConfigDTO brokerageConfigDTO1 = new BrokerageConfigDTO();
        brokerageConfigDTO1.setId(1L);
        BrokerageConfigDTO brokerageConfigDTO2 = new BrokerageConfigDTO();
        assertThat(brokerageConfigDTO1).isNotEqualTo(brokerageConfigDTO2);
        brokerageConfigDTO2.setId(brokerageConfigDTO1.getId());
        assertThat(brokerageConfigDTO1).isEqualTo(brokerageConfigDTO2);
        brokerageConfigDTO2.setId(2L);
        assertThat(brokerageConfigDTO1).isNotEqualTo(brokerageConfigDTO2);
        brokerageConfigDTO1.setId(null);
        assertThat(brokerageConfigDTO1).isNotEqualTo(brokerageConfigDTO2);
    }
}
