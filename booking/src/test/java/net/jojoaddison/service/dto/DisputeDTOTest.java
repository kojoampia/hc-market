package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DisputeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DisputeDTO.class);
        DisputeDTO disputeDTO1 = new DisputeDTO();
        disputeDTO1.setId(1L);
        DisputeDTO disputeDTO2 = new DisputeDTO();
        assertThat(disputeDTO1).isNotEqualTo(disputeDTO2);
        disputeDTO2.setId(disputeDTO1.getId());
        assertThat(disputeDTO1).isEqualTo(disputeDTO2);
        disputeDTO2.setId(2L);
        assertThat(disputeDTO1).isNotEqualTo(disputeDTO2);
        disputeDTO1.setId(null);
        assertThat(disputeDTO1).isNotEqualTo(disputeDTO2);
    }
}
