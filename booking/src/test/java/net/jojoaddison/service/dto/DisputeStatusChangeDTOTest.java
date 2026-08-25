package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DisputeStatusChangeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DisputeStatusChangeDTO.class);
        DisputeStatusChangeDTO disputeStatusChangeDTO1 = new DisputeStatusChangeDTO();
        disputeStatusChangeDTO1.setId(1L);
        DisputeStatusChangeDTO disputeStatusChangeDTO2 = new DisputeStatusChangeDTO();
        assertThat(disputeStatusChangeDTO1).isNotEqualTo(disputeStatusChangeDTO2);
        disputeStatusChangeDTO2.setId(disputeStatusChangeDTO1.getId());
        assertThat(disputeStatusChangeDTO1).isEqualTo(disputeStatusChangeDTO2);
        disputeStatusChangeDTO2.setId(2L);
        assertThat(disputeStatusChangeDTO1).isNotEqualTo(disputeStatusChangeDTO2);
        disputeStatusChangeDTO1.setId(null);
        assertThat(disputeStatusChangeDTO1).isNotEqualTo(disputeStatusChangeDTO2);
    }
}
