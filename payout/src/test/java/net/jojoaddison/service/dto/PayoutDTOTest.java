package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PayoutDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PayoutDTO.class);
        PayoutDTO payoutDTO1 = new PayoutDTO();
        payoutDTO1.setId(1L);
        PayoutDTO payoutDTO2 = new PayoutDTO();
        assertThat(payoutDTO1).isNotEqualTo(payoutDTO2);
        payoutDTO2.setId(payoutDTO1.getId());
        assertThat(payoutDTO1).isEqualTo(payoutDTO2);
        payoutDTO2.setId(2L);
        assertThat(payoutDTO1).isNotEqualTo(payoutDTO2);
        payoutDTO1.setId(null);
        assertThat(payoutDTO1).isNotEqualTo(payoutDTO2);
    }
}
