package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class LedgerDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(LedgerDTO.class);
        LedgerDTO ledgerDTO1 = new LedgerDTO();
        ledgerDTO1.setId(1L);
        LedgerDTO ledgerDTO2 = new LedgerDTO();
        assertThat(ledgerDTO1).isNotEqualTo(ledgerDTO2);
        ledgerDTO2.setId(ledgerDTO1.getId());
        assertThat(ledgerDTO1).isEqualTo(ledgerDTO2);
        ledgerDTO2.setId(2L);
        assertThat(ledgerDTO1).isNotEqualTo(ledgerDTO2);
        ledgerDTO1.setId(null);
        assertThat(ledgerDTO1).isNotEqualTo(ledgerDTO2);
    }
}
