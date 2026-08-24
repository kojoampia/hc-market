package net.jojoaddison.domain;

import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.ReviewTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ReviewTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Review.class);
        Review review1 = getReviewSample1();
        Review review2 = new Review();
        assertThat(review1).isNotEqualTo(review2);

        review2.setId(review1.getId());
        assertThat(review1).isEqualTo(review2);

        review2 = getReviewSample2();
        assertThat(review1).isNotEqualTo(review2);
    }

    @Test
    void professionalTest() {
        Review review = getReviewRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        review.setProfessional(professionalBack);
        assertThat(review.getProfessional()).isEqualTo(professionalBack);

        review.professional(null);
        assertThat(review.getProfessional()).isNull();
    }
}
