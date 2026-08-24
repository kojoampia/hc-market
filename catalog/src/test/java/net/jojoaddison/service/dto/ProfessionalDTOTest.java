package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ProfessionalDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ProfessionalDTO.class);
        ProfessionalDTO professionalDTO1 = new ProfessionalDTO();
        professionalDTO1.setId(1L);
        ProfessionalDTO professionalDTO2 = new ProfessionalDTO();
        assertThat(professionalDTO1).isNotEqualTo(professionalDTO2);
        professionalDTO2.setId(professionalDTO1.getId());
        assertThat(professionalDTO1).isEqualTo(professionalDTO2);
        professionalDTO2.setId(2L);
        assertThat(professionalDTO1).isNotEqualTo(professionalDTO2);
        professionalDTO1.setId(null);
        assertThat(professionalDTO1).isNotEqualTo(professionalDTO2);
    }
}
