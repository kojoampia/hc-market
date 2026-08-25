package net.jojoaddison.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class DisputeCriteriaTest {

    @Test
    void newDisputeCriteriaHasAllFiltersNullTest() {
        var disputeCriteria = new DisputeCriteria();
        assertThat(disputeCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void disputeCriteriaFluentMethodsCreatesFiltersTest() {
        var disputeCriteria = new DisputeCriteria();

        setAllFilters(disputeCriteria);

        assertThat(disputeCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void disputeCriteriaCopyCreatesNullFilterTest() {
        var disputeCriteria = new DisputeCriteria();
        var copy = disputeCriteria.copy();

        assertThat(disputeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(disputeCriteria)
        );
    }

    @Test
    void disputeCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var disputeCriteria = new DisputeCriteria();
        setAllFilters(disputeCriteria);

        var copy = disputeCriteria.copy();

        assertThat(disputeCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(disputeCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var disputeCriteria = new DisputeCriteria();

        assertThat(disputeCriteria).hasToString("DisputeCriteria{}");
    }

    private static void setAllFilters(DisputeCriteria disputeCriteria) {
        disputeCriteria.id();
        disputeCriteria.reference();
        disputeCriteria.bookingReference();
        disputeCriteria.raisedBy();
        disputeCriteria.raisedByLogin();
        disputeCriteria.professionalRef();
        disputeCriteria.reason();
        disputeCriteria.status();
        disputeCriteria.raisedAt();
        disputeCriteria.dueBy();
        disputeCriteria.resolution();
        disputeCriteria.resolvedBy();
        disputeCriteria.resolvedAt();
        disputeCriteria.refundMinor();
        disputeCriteria.currency();
        disputeCriteria.historyId();
        disputeCriteria.distinct();
    }

    private static Condition<DisputeCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getReference()) &&
                condition.apply(criteria.getBookingReference()) &&
                condition.apply(criteria.getRaisedBy()) &&
                condition.apply(criteria.getRaisedByLogin()) &&
                condition.apply(criteria.getProfessionalRef()) &&
                condition.apply(criteria.getReason()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getRaisedAt()) &&
                condition.apply(criteria.getDueBy()) &&
                condition.apply(criteria.getResolution()) &&
                condition.apply(criteria.getResolvedBy()) &&
                condition.apply(criteria.getResolvedAt()) &&
                condition.apply(criteria.getRefundMinor()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getHistoryId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<DisputeCriteria> copyFiltersAre(DisputeCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getReference(), copy.getReference()) &&
                condition.apply(criteria.getBookingReference(), copy.getBookingReference()) &&
                condition.apply(criteria.getRaisedBy(), copy.getRaisedBy()) &&
                condition.apply(criteria.getRaisedByLogin(), copy.getRaisedByLogin()) &&
                condition.apply(criteria.getProfessionalRef(), copy.getProfessionalRef()) &&
                condition.apply(criteria.getReason(), copy.getReason()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getRaisedAt(), copy.getRaisedAt()) &&
                condition.apply(criteria.getDueBy(), copy.getDueBy()) &&
                condition.apply(criteria.getResolution(), copy.getResolution()) &&
                condition.apply(criteria.getResolvedBy(), copy.getResolvedBy()) &&
                condition.apply(criteria.getResolvedAt(), copy.getResolvedAt()) &&
                condition.apply(criteria.getRefundMinor(), copy.getRefundMinor()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getHistoryId(), copy.getHistoryId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
