package net.jojoaddison.domain;

import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.VerificationReviewTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class VerificationReviewTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(VerificationReview.class);
        VerificationReview verificationReview1 = getVerificationReviewSample1();
        VerificationReview verificationReview2 = new VerificationReview();
        assertThat(verificationReview1).isNotEqualTo(verificationReview2);

        verificationReview2.setId(verificationReview1.getId());
        assertThat(verificationReview1).isEqualTo(verificationReview2);

        verificationReview2 = getVerificationReviewSample2();
        assertThat(verificationReview1).isNotEqualTo(verificationReview2);
    }

    @Test
    void professionalTest() {
        VerificationReview verificationReview = getVerificationReviewRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        verificationReview.setProfessional(professionalBack);
        assertThat(verificationReview.getProfessional()).isEqualTo(professionalBack);

        verificationReview.professional(null);
        assertThat(verificationReview.getProfessional()).isNull();
    }
}
