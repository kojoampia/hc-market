package net.jojoaddison.domain;

import static net.jojoaddison.domain.AvailabilitySlotTestSamples.*;
import static net.jojoaddison.domain.CategoryTestSamples.*;
import static net.jojoaddison.domain.CredentialTestSamples.*;
import static net.jojoaddison.domain.HighlightTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.ReviewTestSamples.*;
import static net.jojoaddison.domain.ServiceOfferingTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ProfessionalTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Professional.class);
        Professional professional1 = getProfessionalSample1();
        Professional professional2 = new Professional();
        assertThat(professional1).isNotEqualTo(professional2);

        professional2.setId(professional1.getId());
        assertThat(professional1).isEqualTo(professional2);

        professional2 = getProfessionalSample2();
        assertThat(professional1).isNotEqualTo(professional2);
    }

    @Test
    void serviceTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        ServiceOffering serviceOfferingBack = getServiceOfferingRandomSampleGenerator();

        professional.addService(serviceOfferingBack);
        assertThat(professional.getServices()).containsOnly(serviceOfferingBack);
        assertThat(serviceOfferingBack.getProfessional()).isEqualTo(professional);

        professional.removeService(serviceOfferingBack);
        assertThat(professional.getServices()).doesNotContain(serviceOfferingBack);
        assertThat(serviceOfferingBack.getProfessional()).isNull();

        professional.services(new HashSet<>(Set.of(serviceOfferingBack)));
        assertThat(professional.getServices()).containsOnly(serviceOfferingBack);
        assertThat(serviceOfferingBack.getProfessional()).isEqualTo(professional);

        professional.setServices(new HashSet<>());
        assertThat(professional.getServices()).doesNotContain(serviceOfferingBack);
        assertThat(serviceOfferingBack.getProfessional()).isNull();
    }

    @Test
    void availabilityTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        AvailabilitySlot availabilitySlotBack = getAvailabilitySlotRandomSampleGenerator();

        professional.addAvailability(availabilitySlotBack);
        assertThat(professional.getAvailabilities()).containsOnly(availabilitySlotBack);
        assertThat(availabilitySlotBack.getProfessional()).isEqualTo(professional);

        professional.removeAvailability(availabilitySlotBack);
        assertThat(professional.getAvailabilities()).doesNotContain(availabilitySlotBack);
        assertThat(availabilitySlotBack.getProfessional()).isNull();

        professional.availabilities(new HashSet<>(Set.of(availabilitySlotBack)));
        assertThat(professional.getAvailabilities()).containsOnly(availabilitySlotBack);
        assertThat(availabilitySlotBack.getProfessional()).isEqualTo(professional);

        professional.setAvailabilities(new HashSet<>());
        assertThat(professional.getAvailabilities()).doesNotContain(availabilitySlotBack);
        assertThat(availabilitySlotBack.getProfessional()).isNull();
    }

    @Test
    void reviewTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Review reviewBack = getReviewRandomSampleGenerator();

        professional.addReview(reviewBack);
        assertThat(professional.getReviews()).containsOnly(reviewBack);
        assertThat(reviewBack.getProfessional()).isEqualTo(professional);

        professional.removeReview(reviewBack);
        assertThat(professional.getReviews()).doesNotContain(reviewBack);
        assertThat(reviewBack.getProfessional()).isNull();

        professional.reviews(new HashSet<>(Set.of(reviewBack)));
        assertThat(professional.getReviews()).containsOnly(reviewBack);
        assertThat(reviewBack.getProfessional()).isEqualTo(professional);

        professional.setReviews(new HashSet<>());
        assertThat(professional.getReviews()).doesNotContain(reviewBack);
        assertThat(reviewBack.getProfessional()).isNull();
    }

    @Test
    void credentialTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Credential credentialBack = getCredentialRandomSampleGenerator();

        professional.addCredential(credentialBack);
        assertThat(professional.getCredentials()).containsOnly(credentialBack);
        assertThat(credentialBack.getProfessional()).isEqualTo(professional);

        professional.removeCredential(credentialBack);
        assertThat(professional.getCredentials()).doesNotContain(credentialBack);
        assertThat(credentialBack.getProfessional()).isNull();

        professional.credentials(new HashSet<>(Set.of(credentialBack)));
        assertThat(professional.getCredentials()).containsOnly(credentialBack);
        assertThat(credentialBack.getProfessional()).isEqualTo(professional);

        professional.setCredentials(new HashSet<>());
        assertThat(professional.getCredentials()).doesNotContain(credentialBack);
        assertThat(credentialBack.getProfessional()).isNull();
    }

    @Test
    void highlightTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Highlight highlightBack = getHighlightRandomSampleGenerator();

        professional.addHighlight(highlightBack);
        assertThat(professional.getHighlights()).containsOnly(highlightBack);
        assertThat(highlightBack.getProfessional()).isEqualTo(professional);

        professional.removeHighlight(highlightBack);
        assertThat(professional.getHighlights()).doesNotContain(highlightBack);
        assertThat(highlightBack.getProfessional()).isNull();

        professional.highlights(new HashSet<>(Set.of(highlightBack)));
        assertThat(professional.getHighlights()).containsOnly(highlightBack);
        assertThat(highlightBack.getProfessional()).isEqualTo(professional);

        professional.setHighlights(new HashSet<>());
        assertThat(professional.getHighlights()).doesNotContain(highlightBack);
        assertThat(highlightBack.getProfessional()).isNull();
    }

    @Test
    void categoryTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        professional.setCategory(categoryBack);
        assertThat(professional.getCategory()).isEqualTo(categoryBack);

        professional.category(null);
        assertThat(professional.getCategory()).isNull();
    }
}
