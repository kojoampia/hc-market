package net.jojoaddison.domain;

import static net.jojoaddison.domain.CredentialTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CredentialTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Credential.class);
        Credential credential1 = getCredentialSample1();
        Credential credential2 = new Credential();
        assertThat(credential1).isNotEqualTo(credential2);

        credential2.setId(credential1.getId());
        assertThat(credential1).isEqualTo(credential2);

        credential2 = getCredentialSample2();
        assertThat(credential1).isNotEqualTo(credential2);
    }

    @Test
    void professionalTest() {
        Credential credential = getCredentialRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        credential.setProfessional(professionalBack);
        assertThat(credential.getProfessional()).isEqualTo(professionalBack);

        credential.professional(null);
        assertThat(credential.getProfessional()).isNull();
    }
}
