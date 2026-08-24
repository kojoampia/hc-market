package net.jojoaddison.domain;

import static net.jojoaddison.domain.HighlightTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HighlightTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Highlight.class);
        Highlight highlight1 = getHighlightSample1();
        Highlight highlight2 = new Highlight();
        assertThat(highlight1).isNotEqualTo(highlight2);

        highlight2.setId(highlight1.getId());
        assertThat(highlight1).isEqualTo(highlight2);

        highlight2 = getHighlightSample2();
        assertThat(highlight1).isNotEqualTo(highlight2);
    }

    @Test
    void professionalTest() {
        Highlight highlight = getHighlightRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        highlight.setProfessional(professionalBack);
        assertThat(highlight.getProfessional()).isEqualTo(professionalBack);

        highlight.professional(null);
        assertThat(highlight.getProfessional()).isNull();
    }
}
