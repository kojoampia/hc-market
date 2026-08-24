package net.jojoaddison.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class LedgerCriteriaTest {

    @Test
    void newLedgerCriteriaHasAllFiltersNullTest() {
        var ledgerCriteria = new LedgerCriteria();
        assertThat(ledgerCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void ledgerCriteriaFluentMethodsCreatesFiltersTest() {
        var ledgerCriteria = new LedgerCriteria();

        setAllFilters(ledgerCriteria);

        assertThat(ledgerCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void ledgerCriteriaCopyCreatesNullFilterTest() {
        var ledgerCriteria = new LedgerCriteria();
        var copy = ledgerCriteria.copy();

        assertThat(ledgerCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(ledgerCriteria)
        );
    }

    @Test
    void ledgerCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var ledgerCriteria = new LedgerCriteria();
        setAllFilters(ledgerCriteria);

        var copy = ledgerCriteria.copy();

        assertThat(ledgerCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(ledgerCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var ledgerCriteria = new LedgerCriteria();

        assertThat(ledgerCriteria).hasToString("LedgerCriteria{}");
    }

    private static void setAllFilters(LedgerCriteria ledgerCriteria) {
        ledgerCriteria.id();
        ledgerCriteria.bookingReference();
        ledgerCriteria.professionalRef();
        ledgerCriteria.professionalLogin();
        ledgerCriteria.grossMinor();
        ledgerCriteria.commissionMinor();
        ledgerCriteria.netMinor();
        ledgerCriteria.currency();
        ledgerCriteria.deliveryMode();
        ledgerCriteria.serviceRef();
        ledgerCriteria.serviceName();
        ledgerCriteria.earnedOn();
        ledgerCriteria.payoutId();
        ledgerCriteria.distinct();
    }

    private static Condition<LedgerCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getBookingReference()) &&
                condition.apply(criteria.getProfessionalRef()) &&
                condition.apply(criteria.getProfessionalLogin()) &&
                condition.apply(criteria.getGrossMinor()) &&
                condition.apply(criteria.getCommissionMinor()) &&
                condition.apply(criteria.getNetMinor()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getDeliveryMode()) &&
                condition.apply(criteria.getServiceRef()) &&
                condition.apply(criteria.getServiceName()) &&
                condition.apply(criteria.getEarnedOn()) &&
                condition.apply(criteria.getPayoutId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<LedgerCriteria> copyFiltersAre(LedgerCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getBookingReference(), copy.getBookingReference()) &&
                condition.apply(criteria.getProfessionalRef(), copy.getProfessionalRef()) &&
                condition.apply(criteria.getProfessionalLogin(), copy.getProfessionalLogin()) &&
                condition.apply(criteria.getGrossMinor(), copy.getGrossMinor()) &&
                condition.apply(criteria.getCommissionMinor(), copy.getCommissionMinor()) &&
                condition.apply(criteria.getNetMinor(), copy.getNetMinor()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getDeliveryMode(), copy.getDeliveryMode()) &&
                condition.apply(criteria.getServiceRef(), copy.getServiceRef()) &&
                condition.apply(criteria.getServiceName(), copy.getServiceName()) &&
                condition.apply(criteria.getEarnedOn(), copy.getEarnedOn()) &&
                condition.apply(criteria.getPayoutId(), copy.getPayoutId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
