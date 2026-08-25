package net.jojoaddison.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class ProfessionalCriteriaTest {

    @Test
    void newProfessionalCriteriaHasAllFiltersNullTest() {
        var professionalCriteria = new ProfessionalCriteria();
        assertThat(professionalCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void professionalCriteriaFluentMethodsCreatesFiltersTest() {
        var professionalCriteria = new ProfessionalCriteria();

        setAllFilters(professionalCriteria);

        assertThat(professionalCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void professionalCriteriaCopyCreatesNullFilterTest() {
        var professionalCriteria = new ProfessionalCriteria();
        var copy = professionalCriteria.copy();

        assertThat(professionalCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(professionalCriteria)
        );
    }

    @Test
    void professionalCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var professionalCriteria = new ProfessionalCriteria();
        setAllFilters(professionalCriteria);

        var copy = professionalCriteria.copy();

        assertThat(professionalCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(professionalCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var professionalCriteria = new ProfessionalCriteria();

        assertThat(professionalCriteria).hasToString("ProfessionalCriteria{}");
    }

    private static void setAllFilters(ProfessionalCriteria professionalCriteria) {
        professionalCriteria.id();
        professionalCriteria.reference();
        professionalCriteria.userLogin();
        professionalCriteria.displayName();
        professionalCriteria.initials();
        professionalCriteria.headline();
        professionalCriteria.speciality();
        professionalCriteria.city();
        professionalCriteria.countryCode();
        professionalCriteria.yearsPractising();
        professionalCriteria.verification();
        professionalCriteria.insured();
        professionalCriteria.policeClearance();
        professionalCriteria.responseMinutes();
        professionalCriteria.rebookRatePct();
        professionalCriteria.languages();
        professionalCriteria.deliveryModes();
        professionalCriteria.avatarGradientFrom();
        professionalCriteria.avatarGradientTo();
        professionalCriteria.publishedAt();
        professionalCriteria.serviceId();
        professionalCriteria.availabilityId();
        professionalCriteria.ruleId();
        professionalCriteria.overrideId();
        professionalCriteria.reviewId();
        professionalCriteria.credentialId();
        professionalCriteria.highlightId();
        professionalCriteria.categoryId();
        professionalCriteria.distinct();
    }

    private static Condition<ProfessionalCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getReference()) &&
                condition.apply(criteria.getUserLogin()) &&
                condition.apply(criteria.getDisplayName()) &&
                condition.apply(criteria.getInitials()) &&
                condition.apply(criteria.getHeadline()) &&
                condition.apply(criteria.getSpeciality()) &&
                condition.apply(criteria.getCity()) &&
                condition.apply(criteria.getCountryCode()) &&
                condition.apply(criteria.getYearsPractising()) &&
                condition.apply(criteria.getVerification()) &&
                condition.apply(criteria.getInsured()) &&
                condition.apply(criteria.getPoliceClearance()) &&
                condition.apply(criteria.getResponseMinutes()) &&
                condition.apply(criteria.getRebookRatePct()) &&
                condition.apply(criteria.getLanguages()) &&
                condition.apply(criteria.getDeliveryModes()) &&
                condition.apply(criteria.getAvatarGradientFrom()) &&
                condition.apply(criteria.getAvatarGradientTo()) &&
                condition.apply(criteria.getPublishedAt()) &&
                condition.apply(criteria.getServiceId()) &&
                condition.apply(criteria.getAvailabilityId()) &&
                condition.apply(criteria.getRuleId()) &&
                condition.apply(criteria.getOverrideId()) &&
                condition.apply(criteria.getReviewId()) &&
                condition.apply(criteria.getCredentialId()) &&
                condition.apply(criteria.getHighlightId()) &&
                condition.apply(criteria.getCategoryId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<ProfessionalCriteria> copyFiltersAre(
        ProfessionalCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getReference(), copy.getReference()) &&
                condition.apply(criteria.getUserLogin(), copy.getUserLogin()) &&
                condition.apply(criteria.getDisplayName(), copy.getDisplayName()) &&
                condition.apply(criteria.getInitials(), copy.getInitials()) &&
                condition.apply(criteria.getHeadline(), copy.getHeadline()) &&
                condition.apply(criteria.getSpeciality(), copy.getSpeciality()) &&
                condition.apply(criteria.getCity(), copy.getCity()) &&
                condition.apply(criteria.getCountryCode(), copy.getCountryCode()) &&
                condition.apply(criteria.getYearsPractising(), copy.getYearsPractising()) &&
                condition.apply(criteria.getVerification(), copy.getVerification()) &&
                condition.apply(criteria.getInsured(), copy.getInsured()) &&
                condition.apply(criteria.getPoliceClearance(), copy.getPoliceClearance()) &&
                condition.apply(criteria.getResponseMinutes(), copy.getResponseMinutes()) &&
                condition.apply(criteria.getRebookRatePct(), copy.getRebookRatePct()) &&
                condition.apply(criteria.getLanguages(), copy.getLanguages()) &&
                condition.apply(criteria.getDeliveryModes(), copy.getDeliveryModes()) &&
                condition.apply(criteria.getAvatarGradientFrom(), copy.getAvatarGradientFrom()) &&
                condition.apply(criteria.getAvatarGradientTo(), copy.getAvatarGradientTo()) &&
                condition.apply(criteria.getPublishedAt(), copy.getPublishedAt()) &&
                condition.apply(criteria.getServiceId(), copy.getServiceId()) &&
                condition.apply(criteria.getAvailabilityId(), copy.getAvailabilityId()) &&
                condition.apply(criteria.getRuleId(), copy.getRuleId()) &&
                condition.apply(criteria.getOverrideId(), copy.getOverrideId()) &&
                condition.apply(criteria.getReviewId(), copy.getReviewId()) &&
                condition.apply(criteria.getCredentialId(), copy.getCredentialId()) &&
                condition.apply(criteria.getHighlightId(), copy.getHighlightId()) &&
                condition.apply(criteria.getCategoryId(), copy.getCategoryId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
