package net.jojoaddison.domain;

import static net.jojoaddison.domain.CategoryTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Category.class);
        Category category1 = getCategorySample1();
        Category category2 = new Category();
        assertThat(category1).isNotEqualTo(category2);

        category2.setId(category1.getId());
        assertThat(category1).isEqualTo(category2);

        category2 = getCategorySample2();
        assertThat(category1).isNotEqualTo(category2);
    }

    @Test
    void professionalTest() {
        Category category = getCategoryRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        category.addProfessional(professionalBack);
        assertThat(category.getProfessionals()).containsOnly(professionalBack);
        assertThat(professionalBack.getCategory()).isEqualTo(category);

        category.removeProfessional(professionalBack);
        assertThat(category.getProfessionals()).doesNotContain(professionalBack);
        assertThat(professionalBack.getCategory()).isNull();

        category.professionals(new HashSet<>(Set.of(professionalBack)));
        assertThat(category.getProfessionals()).containsOnly(professionalBack);
        assertThat(professionalBack.getCategory()).isEqualTo(category);

        category.setProfessionals(new HashSet<>());
        assertThat(category.getProfessionals()).doesNotContain(professionalBack);
        assertThat(professionalBack.getCategory()).isNull();
    }
}
