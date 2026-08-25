package net.jojoaddison.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link net.jojoaddison.domain.Booking} entity. This class is used
 * in {@link net.jojoaddison.web.rest.BookingResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /bookings?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BookingCriteria implements Serializable, Criteria {

    /**
     * Class for filtering LocalTime
     */
    public static class LocalTimeFilter extends RangeFilter<LocalTime> {

        public LocalTimeFilter() {}

        public LocalTimeFilter(LocalTimeFilter filter) {
            super(filter);
        }

        @Override
        public LocalTimeFilter copy() {
            return new LocalTimeFilter(this);
        }
    }

    /**
     * Class for filtering DeliveryMode
     */
    public static class DeliveryModeFilter extends Filter<DeliveryMode> {

        public DeliveryModeFilter() {}

        public DeliveryModeFilter(DeliveryModeFilter filter) {
            super(filter);
        }

        @Override
        public DeliveryModeFilter copy() {
            return new DeliveryModeFilter(this);
        }
    }

    /**
     * Class for filtering BookingStatus
     */
    public static class BookingStatusFilter extends Filter<BookingStatus> {

        public BookingStatusFilter() {}

        public BookingStatusFilter(BookingStatusFilter filter) {
            super(filter);
        }

        @Override
        public BookingStatusFilter copy() {
            return new BookingStatusFilter(this);
        }
    }

    /**
     * Class for filtering CancelledBy
     */
    public static class CancelledByFilter extends Filter<CancelledBy> {

        public CancelledByFilter() {}

        public CancelledByFilter(CancelledByFilter filter) {
            super(filter);
        }

        @Override
        public CancelledByFilter copy() {
            return new CancelledByFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter reference;

    private StringFilter customerLogin;

    private StringFilter customerName;

    private StringFilter professionalRef;

    private StringFilter professionalLogin;

    private StringFilter serviceRef;

    private StringFilter serviceName;

    private LongFilter priceMinor;

    private StringFilter currency;

    private LocalDateFilter scheduledDate;

    private LocalTimeFilter scheduledTime;

    private DeliveryModeFilter deliveryMode;

    private BookingStatusFilter status;

    private StringFilter customerNote;

    private StringFilter onBehalfOf;

    private StringFilter visitAddress;

    private BooleanFilter careSummaryShared;

    private InstantFilter raisedAt;

    private InstantFilter respondedAt;

    private InstantFilter completedAt;

    private InstantFilter cancelledAt;

    private CancelledByFilter cancelledBy;

    private StringFilter cancellationReason;

    private BooleanFilter lateCancellation;

    private BooleanFilter reviewed;

    private LongFilter historyId;

    private Boolean distinct;

    public BookingCriteria() {}

    public BookingCriteria(BookingCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.reference = other.optionalReference().map(StringFilter::copy).orElse(null);
        this.customerLogin = other.optionalCustomerLogin().map(StringFilter::copy).orElse(null);
        this.customerName = other.optionalCustomerName().map(StringFilter::copy).orElse(null);
        this.professionalRef = other.optionalProfessionalRef().map(StringFilter::copy).orElse(null);
        this.professionalLogin = other.optionalProfessionalLogin().map(StringFilter::copy).orElse(null);
        this.serviceRef = other.optionalServiceRef().map(StringFilter::copy).orElse(null);
        this.serviceName = other.optionalServiceName().map(StringFilter::copy).orElse(null);
        this.priceMinor = other.optionalPriceMinor().map(LongFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(StringFilter::copy).orElse(null);
        this.scheduledDate = other.optionalScheduledDate().map(LocalDateFilter::copy).orElse(null);
        this.scheduledTime = other.optionalScheduledTime().map(LocalTimeFilter::copy).orElse(null);
        this.deliveryMode = other.optionalDeliveryMode().map(DeliveryModeFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(BookingStatusFilter::copy).orElse(null);
        this.customerNote = other.optionalCustomerNote().map(StringFilter::copy).orElse(null);
        this.onBehalfOf = other.optionalOnBehalfOf().map(StringFilter::copy).orElse(null);
        this.visitAddress = other.optionalVisitAddress().map(StringFilter::copy).orElse(null);
        this.careSummaryShared = other.optionalCareSummaryShared().map(BooleanFilter::copy).orElse(null);
        this.raisedAt = other.optionalRaisedAt().map(InstantFilter::copy).orElse(null);
        this.respondedAt = other.optionalRespondedAt().map(InstantFilter::copy).orElse(null);
        this.completedAt = other.optionalCompletedAt().map(InstantFilter::copy).orElse(null);
        this.cancelledAt = other.optionalCancelledAt().map(InstantFilter::copy).orElse(null);
        this.cancelledBy = other.optionalCancelledBy().map(CancelledByFilter::copy).orElse(null);
        this.cancellationReason = other.optionalCancellationReason().map(StringFilter::copy).orElse(null);
        this.lateCancellation = other.optionalLateCancellation().map(BooleanFilter::copy).orElse(null);
        this.reviewed = other.optionalReviewed().map(BooleanFilter::copy).orElse(null);
        this.historyId = other.optionalHistoryId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public BookingCriteria copy() {
        return new BookingCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getReference() {
        return reference;
    }

    public Optional<StringFilter> optionalReference() {
        return Optional.ofNullable(reference);
    }

    public StringFilter reference() {
        if (reference == null) {
            setReference(new StringFilter());
        }
        return reference;
    }

    public void setReference(StringFilter reference) {
        this.reference = reference;
    }

    public StringFilter getCustomerLogin() {
        return customerLogin;
    }

    public Optional<StringFilter> optionalCustomerLogin() {
        return Optional.ofNullable(customerLogin);
    }

    public StringFilter customerLogin() {
        if (customerLogin == null) {
            setCustomerLogin(new StringFilter());
        }
        return customerLogin;
    }

    public void setCustomerLogin(StringFilter customerLogin) {
        this.customerLogin = customerLogin;
    }

    public StringFilter getCustomerName() {
        return customerName;
    }

    public Optional<StringFilter> optionalCustomerName() {
        return Optional.ofNullable(customerName);
    }

    public StringFilter customerName() {
        if (customerName == null) {
            setCustomerName(new StringFilter());
        }
        return customerName;
    }

    public void setCustomerName(StringFilter customerName) {
        this.customerName = customerName;
    }

    public StringFilter getProfessionalRef() {
        return professionalRef;
    }

    public Optional<StringFilter> optionalProfessionalRef() {
        return Optional.ofNullable(professionalRef);
    }

    public StringFilter professionalRef() {
        if (professionalRef == null) {
            setProfessionalRef(new StringFilter());
        }
        return professionalRef;
    }

    public void setProfessionalRef(StringFilter professionalRef) {
        this.professionalRef = professionalRef;
    }

    public StringFilter getProfessionalLogin() {
        return professionalLogin;
    }

    public Optional<StringFilter> optionalProfessionalLogin() {
        return Optional.ofNullable(professionalLogin);
    }

    public StringFilter professionalLogin() {
        if (professionalLogin == null) {
            setProfessionalLogin(new StringFilter());
        }
        return professionalLogin;
    }

    public void setProfessionalLogin(StringFilter professionalLogin) {
        this.professionalLogin = professionalLogin;
    }

    public StringFilter getServiceRef() {
        return serviceRef;
    }

    public Optional<StringFilter> optionalServiceRef() {
        return Optional.ofNullable(serviceRef);
    }

    public StringFilter serviceRef() {
        if (serviceRef == null) {
            setServiceRef(new StringFilter());
        }
        return serviceRef;
    }

    public void setServiceRef(StringFilter serviceRef) {
        this.serviceRef = serviceRef;
    }

    public StringFilter getServiceName() {
        return serviceName;
    }

    public Optional<StringFilter> optionalServiceName() {
        return Optional.ofNullable(serviceName);
    }

    public StringFilter serviceName() {
        if (serviceName == null) {
            setServiceName(new StringFilter());
        }
        return serviceName;
    }

    public void setServiceName(StringFilter serviceName) {
        this.serviceName = serviceName;
    }

    public LongFilter getPriceMinor() {
        return priceMinor;
    }

    public Optional<LongFilter> optionalPriceMinor() {
        return Optional.ofNullable(priceMinor);
    }

    public LongFilter priceMinor() {
        if (priceMinor == null) {
            setPriceMinor(new LongFilter());
        }
        return priceMinor;
    }

    public void setPriceMinor(LongFilter priceMinor) {
        this.priceMinor = priceMinor;
    }

    public StringFilter getCurrency() {
        return currency;
    }

    public Optional<StringFilter> optionalCurrency() {
        return Optional.ofNullable(currency);
    }

    public StringFilter currency() {
        if (currency == null) {
            setCurrency(new StringFilter());
        }
        return currency;
    }

    public void setCurrency(StringFilter currency) {
        this.currency = currency;
    }

    public LocalDateFilter getScheduledDate() {
        return scheduledDate;
    }

    public Optional<LocalDateFilter> optionalScheduledDate() {
        return Optional.ofNullable(scheduledDate);
    }

    public LocalDateFilter scheduledDate() {
        if (scheduledDate == null) {
            setScheduledDate(new LocalDateFilter());
        }
        return scheduledDate;
    }

    public void setScheduledDate(LocalDateFilter scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public LocalTimeFilter getScheduledTime() {
        return scheduledTime;
    }

    public Optional<LocalTimeFilter> optionalScheduledTime() {
        return Optional.ofNullable(scheduledTime);
    }

    public LocalTimeFilter scheduledTime() {
        if (scheduledTime == null) {
            setScheduledTime(new LocalTimeFilter());
        }
        return scheduledTime;
    }

    public void setScheduledTime(LocalTimeFilter scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public DeliveryModeFilter getDeliveryMode() {
        return deliveryMode;
    }

    public Optional<DeliveryModeFilter> optionalDeliveryMode() {
        return Optional.ofNullable(deliveryMode);
    }

    public DeliveryModeFilter deliveryMode() {
        if (deliveryMode == null) {
            setDeliveryMode(new DeliveryModeFilter());
        }
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryModeFilter deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public BookingStatusFilter getStatus() {
        return status;
    }

    public Optional<BookingStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public BookingStatusFilter status() {
        if (status == null) {
            setStatus(new BookingStatusFilter());
        }
        return status;
    }

    public void setStatus(BookingStatusFilter status) {
        this.status = status;
    }

    public StringFilter getCustomerNote() {
        return customerNote;
    }

    public Optional<StringFilter> optionalCustomerNote() {
        return Optional.ofNullable(customerNote);
    }

    public StringFilter customerNote() {
        if (customerNote == null) {
            setCustomerNote(new StringFilter());
        }
        return customerNote;
    }

    public void setCustomerNote(StringFilter customerNote) {
        this.customerNote = customerNote;
    }

    public StringFilter getOnBehalfOf() {
        return onBehalfOf;
    }

    public Optional<StringFilter> optionalOnBehalfOf() {
        return Optional.ofNullable(onBehalfOf);
    }

    public StringFilter onBehalfOf() {
        if (onBehalfOf == null) {
            setOnBehalfOf(new StringFilter());
        }
        return onBehalfOf;
    }

    public void setOnBehalfOf(StringFilter onBehalfOf) {
        this.onBehalfOf = onBehalfOf;
    }

    public StringFilter getVisitAddress() {
        return visitAddress;
    }

    public Optional<StringFilter> optionalVisitAddress() {
        return Optional.ofNullable(visitAddress);
    }

    public StringFilter visitAddress() {
        if (visitAddress == null) {
            setVisitAddress(new StringFilter());
        }
        return visitAddress;
    }

    public void setVisitAddress(StringFilter visitAddress) {
        this.visitAddress = visitAddress;
    }

    public BooleanFilter getCareSummaryShared() {
        return careSummaryShared;
    }

    public Optional<BooleanFilter> optionalCareSummaryShared() {
        return Optional.ofNullable(careSummaryShared);
    }

    public BooleanFilter careSummaryShared() {
        if (careSummaryShared == null) {
            setCareSummaryShared(new BooleanFilter());
        }
        return careSummaryShared;
    }

    public void setCareSummaryShared(BooleanFilter careSummaryShared) {
        this.careSummaryShared = careSummaryShared;
    }

    public InstantFilter getRaisedAt() {
        return raisedAt;
    }

    public Optional<InstantFilter> optionalRaisedAt() {
        return Optional.ofNullable(raisedAt);
    }

    public InstantFilter raisedAt() {
        if (raisedAt == null) {
            setRaisedAt(new InstantFilter());
        }
        return raisedAt;
    }

    public void setRaisedAt(InstantFilter raisedAt) {
        this.raisedAt = raisedAt;
    }

    public InstantFilter getRespondedAt() {
        return respondedAt;
    }

    public Optional<InstantFilter> optionalRespondedAt() {
        return Optional.ofNullable(respondedAt);
    }

    public InstantFilter respondedAt() {
        if (respondedAt == null) {
            setRespondedAt(new InstantFilter());
        }
        return respondedAt;
    }

    public void setRespondedAt(InstantFilter respondedAt) {
        this.respondedAt = respondedAt;
    }

    public InstantFilter getCompletedAt() {
        return completedAt;
    }

    public Optional<InstantFilter> optionalCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public InstantFilter completedAt() {
        if (completedAt == null) {
            setCompletedAt(new InstantFilter());
        }
        return completedAt;
    }

    public void setCompletedAt(InstantFilter completedAt) {
        this.completedAt = completedAt;
    }

    public InstantFilter getCancelledAt() {
        return cancelledAt;
    }

    public Optional<InstantFilter> optionalCancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public InstantFilter cancelledAt() {
        if (cancelledAt == null) {
            setCancelledAt(new InstantFilter());
        }
        return cancelledAt;
    }

    public void setCancelledAt(InstantFilter cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public CancelledByFilter getCancelledBy() {
        return cancelledBy;
    }

    public Optional<CancelledByFilter> optionalCancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    public CancelledByFilter cancelledBy() {
        if (cancelledBy == null) {
            setCancelledBy(new CancelledByFilter());
        }
        return cancelledBy;
    }

    public void setCancelledBy(CancelledByFilter cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public StringFilter getCancellationReason() {
        return cancellationReason;
    }

    public Optional<StringFilter> optionalCancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    public StringFilter cancellationReason() {
        if (cancellationReason == null) {
            setCancellationReason(new StringFilter());
        }
        return cancellationReason;
    }

    public void setCancellationReason(StringFilter cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public BooleanFilter getLateCancellation() {
        return lateCancellation;
    }

    public Optional<BooleanFilter> optionalLateCancellation() {
        return Optional.ofNullable(lateCancellation);
    }

    public BooleanFilter lateCancellation() {
        if (lateCancellation == null) {
            setLateCancellation(new BooleanFilter());
        }
        return lateCancellation;
    }

    public void setLateCancellation(BooleanFilter lateCancellation) {
        this.lateCancellation = lateCancellation;
    }

    public BooleanFilter getReviewed() {
        return reviewed;
    }

    public Optional<BooleanFilter> optionalReviewed() {
        return Optional.ofNullable(reviewed);
    }

    public BooleanFilter reviewed() {
        if (reviewed == null) {
            setReviewed(new BooleanFilter());
        }
        return reviewed;
    }

    public void setReviewed(BooleanFilter reviewed) {
        this.reviewed = reviewed;
    }

    public LongFilter getHistoryId() {
        return historyId;
    }

    public Optional<LongFilter> optionalHistoryId() {
        return Optional.ofNullable(historyId);
    }

    public LongFilter historyId() {
        if (historyId == null) {
            setHistoryId(new LongFilter());
        }
        return historyId;
    }

    public void setHistoryId(LongFilter historyId) {
        this.historyId = historyId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BookingCriteria that = (BookingCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(reference, that.reference) &&
            Objects.equals(customerLogin, that.customerLogin) &&
            Objects.equals(customerName, that.customerName) &&
            Objects.equals(professionalRef, that.professionalRef) &&
            Objects.equals(professionalLogin, that.professionalLogin) &&
            Objects.equals(serviceRef, that.serviceRef) &&
            Objects.equals(serviceName, that.serviceName) &&
            Objects.equals(priceMinor, that.priceMinor) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(scheduledDate, that.scheduledDate) &&
            Objects.equals(scheduledTime, that.scheduledTime) &&
            Objects.equals(deliveryMode, that.deliveryMode) &&
            Objects.equals(status, that.status) &&
            Objects.equals(customerNote, that.customerNote) &&
            Objects.equals(onBehalfOf, that.onBehalfOf) &&
            Objects.equals(visitAddress, that.visitAddress) &&
            Objects.equals(careSummaryShared, that.careSummaryShared) &&
            Objects.equals(raisedAt, that.raisedAt) &&
            Objects.equals(respondedAt, that.respondedAt) &&
            Objects.equals(completedAt, that.completedAt) &&
            Objects.equals(cancelledAt, that.cancelledAt) &&
            Objects.equals(cancelledBy, that.cancelledBy) &&
            Objects.equals(cancellationReason, that.cancellationReason) &&
            Objects.equals(lateCancellation, that.lateCancellation) &&
            Objects.equals(reviewed, that.reviewed) &&
            Objects.equals(historyId, that.historyId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            reference,
            customerLogin,
            customerName,
            professionalRef,
            professionalLogin,
            serviceRef,
            serviceName,
            priceMinor,
            currency,
            scheduledDate,
            scheduledTime,
            deliveryMode,
            status,
            customerNote,
            onBehalfOf,
            visitAddress,
            careSummaryShared,
            raisedAt,
            respondedAt,
            completedAt,
            cancelledAt,
            cancelledBy,
            cancellationReason,
            lateCancellation,
            reviewed,
            historyId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BookingCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalReference().map(f -> "reference=" + f + ", ").orElse("") +
            optionalCustomerLogin().map(f -> "customerLogin=" + f + ", ").orElse("") +
            optionalCustomerName().map(f -> "customerName=" + f + ", ").orElse("") +
            optionalProfessionalRef().map(f -> "professionalRef=" + f + ", ").orElse("") +
            optionalProfessionalLogin().map(f -> "professionalLogin=" + f + ", ").orElse("") +
            optionalServiceRef().map(f -> "serviceRef=" + f + ", ").orElse("") +
            optionalServiceName().map(f -> "serviceName=" + f + ", ").orElse("") +
            optionalPriceMinor().map(f -> "priceMinor=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalScheduledDate().map(f -> "scheduledDate=" + f + ", ").orElse("") +
            optionalScheduledTime().map(f -> "scheduledTime=" + f + ", ").orElse("") +
            optionalDeliveryMode().map(f -> "deliveryMode=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalCustomerNote().map(f -> "customerNote=" + f + ", ").orElse("") +
            optionalOnBehalfOf().map(f -> "onBehalfOf=" + f + ", ").orElse("") +
            optionalVisitAddress().map(f -> "visitAddress=" + f + ", ").orElse("") +
            optionalCareSummaryShared().map(f -> "careSummaryShared=" + f + ", ").orElse("") +
            optionalRaisedAt().map(f -> "raisedAt=" + f + ", ").orElse("") +
            optionalRespondedAt().map(f -> "respondedAt=" + f + ", ").orElse("") +
            optionalCompletedAt().map(f -> "completedAt=" + f + ", ").orElse("") +
            optionalCancelledAt().map(f -> "cancelledAt=" + f + ", ").orElse("") +
            optionalCancelledBy().map(f -> "cancelledBy=" + f + ", ").orElse("") +
            optionalCancellationReason().map(f -> "cancellationReason=" + f + ", ").orElse("") +
            optionalLateCancellation().map(f -> "lateCancellation=" + f + ", ").orElse("") +
            optionalReviewed().map(f -> "reviewed=" + f + ", ").orElse("") +
            optionalHistoryId().map(f -> "historyId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
