package net.jojoaddison.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class BookingCriteriaTest {

    @Test
    void newBookingCriteriaHasAllFiltersNullTest() {
        var bookingCriteria = new BookingCriteria();
        assertThat(bookingCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void bookingCriteriaFluentMethodsCreatesFiltersTest() {
        var bookingCriteria = new BookingCriteria();

        setAllFilters(bookingCriteria);

        assertThat(bookingCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void bookingCriteriaCopyCreatesNullFilterTest() {
        var bookingCriteria = new BookingCriteria();
        var copy = bookingCriteria.copy();

        assertThat(bookingCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(bookingCriteria)
        );
    }

    @Test
    void bookingCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var bookingCriteria = new BookingCriteria();
        setAllFilters(bookingCriteria);

        var copy = bookingCriteria.copy();

        assertThat(bookingCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(copyFiltersAre(copy, (a, b) -> a == null || a instanceof Boolean ? a == b : a != b && a.equals(b))),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(bookingCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var bookingCriteria = new BookingCriteria();

        assertThat(bookingCriteria).hasToString("BookingCriteria{}");
    }

    private static void setAllFilters(BookingCriteria bookingCriteria) {
        bookingCriteria.id();
        bookingCriteria.reference();
        bookingCriteria.customerLogin();
        bookingCriteria.customerName();
        bookingCriteria.professionalRef();
        bookingCriteria.professionalLogin();
        bookingCriteria.serviceRef();
        bookingCriteria.serviceName();
        bookingCriteria.priceMinor();
        bookingCriteria.currency();
        bookingCriteria.scheduledDate();
        bookingCriteria.scheduledTime();
        bookingCriteria.deliveryMode();
        bookingCriteria.status();
        bookingCriteria.customerNote();
        bookingCriteria.onBehalfOf();
        bookingCriteria.visitAddress();
        bookingCriteria.careSummaryShared();
        bookingCriteria.raisedAt();
        bookingCriteria.respondedAt();
        bookingCriteria.completedAt();
        bookingCriteria.cancelledAt();
        bookingCriteria.cancelledBy();
        bookingCriteria.cancellationReason();
        bookingCriteria.lateCancellation();
        bookingCriteria.reviewed();
        bookingCriteria.historyId();
        bookingCriteria.distinct();
    }

    private static Condition<BookingCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getReference()) &&
                condition.apply(criteria.getCustomerLogin()) &&
                condition.apply(criteria.getCustomerName()) &&
                condition.apply(criteria.getProfessionalRef()) &&
                condition.apply(criteria.getProfessionalLogin()) &&
                condition.apply(criteria.getServiceRef()) &&
                condition.apply(criteria.getServiceName()) &&
                condition.apply(criteria.getPriceMinor()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getScheduledDate()) &&
                condition.apply(criteria.getScheduledTime()) &&
                condition.apply(criteria.getDeliveryMode()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getCustomerNote()) &&
                condition.apply(criteria.getOnBehalfOf()) &&
                condition.apply(criteria.getVisitAddress()) &&
                condition.apply(criteria.getCareSummaryShared()) &&
                condition.apply(criteria.getRaisedAt()) &&
                condition.apply(criteria.getRespondedAt()) &&
                condition.apply(criteria.getCompletedAt()) &&
                condition.apply(criteria.getCancelledAt()) &&
                condition.apply(criteria.getCancelledBy()) &&
                condition.apply(criteria.getCancellationReason()) &&
                condition.apply(criteria.getLateCancellation()) &&
                condition.apply(criteria.getReviewed()) &&
                condition.apply(criteria.getHistoryId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<BookingCriteria> copyFiltersAre(BookingCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getReference(), copy.getReference()) &&
                condition.apply(criteria.getCustomerLogin(), copy.getCustomerLogin()) &&
                condition.apply(criteria.getCustomerName(), copy.getCustomerName()) &&
                condition.apply(criteria.getProfessionalRef(), copy.getProfessionalRef()) &&
                condition.apply(criteria.getProfessionalLogin(), copy.getProfessionalLogin()) &&
                condition.apply(criteria.getServiceRef(), copy.getServiceRef()) &&
                condition.apply(criteria.getServiceName(), copy.getServiceName()) &&
                condition.apply(criteria.getPriceMinor(), copy.getPriceMinor()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getScheduledDate(), copy.getScheduledDate()) &&
                condition.apply(criteria.getScheduledTime(), copy.getScheduledTime()) &&
                condition.apply(criteria.getDeliveryMode(), copy.getDeliveryMode()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getCustomerNote(), copy.getCustomerNote()) &&
                condition.apply(criteria.getOnBehalfOf(), copy.getOnBehalfOf()) &&
                condition.apply(criteria.getVisitAddress(), copy.getVisitAddress()) &&
                condition.apply(criteria.getCareSummaryShared(), copy.getCareSummaryShared()) &&
                condition.apply(criteria.getRaisedAt(), copy.getRaisedAt()) &&
                condition.apply(criteria.getRespondedAt(), copy.getRespondedAt()) &&
                condition.apply(criteria.getCompletedAt(), copy.getCompletedAt()) &&
                condition.apply(criteria.getCancelledAt(), copy.getCancelledAt()) &&
                condition.apply(criteria.getCancelledBy(), copy.getCancelledBy()) &&
                condition.apply(criteria.getCancellationReason(), copy.getCancellationReason()) &&
                condition.apply(criteria.getLateCancellation(), copy.getLateCancellation()) &&
                condition.apply(criteria.getReviewed(), copy.getReviewed()) &&
                condition.apply(criteria.getHistoryId(), copy.getHistoryId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
