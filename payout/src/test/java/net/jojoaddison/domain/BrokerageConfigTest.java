package net.jojoaddison.domain;

import static net.jojoaddison.domain.BrokerageConfigTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class BrokerageConfigTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(BrokerageConfig.class);
        BrokerageConfig brokerageConfig1 = getBrokerageConfigSample1();
        BrokerageConfig brokerageConfig2 = new BrokerageConfig();
        assertThat(brokerageConfig1).isNotEqualTo(brokerageConfig2);

        brokerageConfig2.setId(brokerageConfig1.getId());
        assertThat(brokerageConfig1).isEqualTo(brokerageConfig2);

        brokerageConfig2 = getBrokerageConfigSample2();
        assertThat(brokerageConfig1).isNotEqualTo(brokerageConfig2);
    }
}
