package net.jojoaddison.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link net.jojoaddison.domain.Ledger} entity. This class is used
 * in {@link net.jojoaddison.web.rest.LedgerResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /ledgers?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LedgerCriteria implements Serializable, Criteria {

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

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter bookingReference;

    private StringFilter professionalRef;

    private StringFilter professionalLogin;

    private LongFilter grossMinor;

    private LongFilter commissionMinor;

    private LongFilter netMinor;

    private StringFilter currency;

    private DeliveryModeFilter deliveryMode;

    private StringFilter serviceRef;

    private StringFilter serviceName;

    private LocalDateFilter earnedOn;

    private LongFilter payoutId;

    private Boolean distinct;

    public LedgerCriteria() {}

    public LedgerCriteria(LedgerCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.bookingReference = other.optionalBookingReference().map(StringFilter::copy).orElse(null);
        this.professionalRef = other.optionalProfessionalRef().map(StringFilter::copy).orElse(null);
        this.professionalLogin = other.optionalProfessionalLogin().map(StringFilter::copy).orElse(null);
        this.grossMinor = other.optionalGrossMinor().map(LongFilter::copy).orElse(null);
        this.commissionMinor = other.optionalCommissionMinor().map(LongFilter::copy).orElse(null);
        this.netMinor = other.optionalNetMinor().map(LongFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(StringFilter::copy).orElse(null);
        this.deliveryMode = other.optionalDeliveryMode().map(DeliveryModeFilter::copy).orElse(null);
        this.serviceRef = other.optionalServiceRef().map(StringFilter::copy).orElse(null);
        this.serviceName = other.optionalServiceName().map(StringFilter::copy).orElse(null);
        this.earnedOn = other.optionalEarnedOn().map(LocalDateFilter::copy).orElse(null);
        this.payoutId = other.optionalPayoutId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public LedgerCriteria copy() {
        return new LedgerCriteria(this);
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

    public StringFilter getBookingReference() {
        return bookingReference;
    }

    public Optional<StringFilter> optionalBookingReference() {
        return Optional.ofNullable(bookingReference);
    }

    public StringFilter bookingReference() {
        if (bookingReference == null) {
            setBookingReference(new StringFilter());
        }
        return bookingReference;
    }

    public void setBookingReference(StringFilter bookingReference) {
        this.bookingReference = bookingReference;
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

    public LongFilter getGrossMinor() {
        return grossMinor;
    }

    public Optional<LongFilter> optionalGrossMinor() {
        return Optional.ofNullable(grossMinor);
    }

    public LongFilter grossMinor() {
        if (grossMinor == null) {
            setGrossMinor(new LongFilter());
        }
        return grossMinor;
    }

    public void setGrossMinor(LongFilter grossMinor) {
        this.grossMinor = grossMinor;
    }

    public LongFilter getCommissionMinor() {
        return commissionMinor;
    }

    public Optional<LongFilter> optionalCommissionMinor() {
        return Optional.ofNullable(commissionMinor);
    }

    public LongFilter commissionMinor() {
        if (commissionMinor == null) {
            setCommissionMinor(new LongFilter());
        }
        return commissionMinor;
    }

    public void setCommissionMinor(LongFilter commissionMinor) {
        this.commissionMinor = commissionMinor;
    }

    public LongFilter getNetMinor() {
        return netMinor;
    }

    public Optional<LongFilter> optionalNetMinor() {
        return Optional.ofNullable(netMinor);
    }

    public LongFilter netMinor() {
        if (netMinor == null) {
            setNetMinor(new LongFilter());
        }
        return netMinor;
    }

    public void setNetMinor(LongFilter netMinor) {
        this.netMinor = netMinor;
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

    public LocalDateFilter getEarnedOn() {
        return earnedOn;
    }

    public Optional<LocalDateFilter> optionalEarnedOn() {
        return Optional.ofNullable(earnedOn);
    }

    public LocalDateFilter earnedOn() {
        if (earnedOn == null) {
            setEarnedOn(new LocalDateFilter());
        }
        return earnedOn;
    }

    public void setEarnedOn(LocalDateFilter earnedOn) {
        this.earnedOn = earnedOn;
    }

    public LongFilter getPayoutId() {
        return payoutId;
    }

    public Optional<LongFilter> optionalPayoutId() {
        return Optional.ofNullable(payoutId);
    }

    public LongFilter payoutId() {
        if (payoutId == null) {
            setPayoutId(new LongFilter());
        }
        return payoutId;
    }

    public void setPayoutId(LongFilter payoutId) {
        this.payoutId = payoutId;
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
        final LedgerCriteria that = (LedgerCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(bookingReference, that.bookingReference) &&
            Objects.equals(professionalRef, that.professionalRef) &&
            Objects.equals(professionalLogin, that.professionalLogin) &&
            Objects.equals(grossMinor, that.grossMinor) &&
            Objects.equals(commissionMinor, that.commissionMinor) &&
            Objects.equals(netMinor, that.netMinor) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(deliveryMode, that.deliveryMode) &&
            Objects.equals(serviceRef, that.serviceRef) &&
            Objects.equals(serviceName, that.serviceName) &&
            Objects.equals(earnedOn, that.earnedOn) &&
            Objects.equals(payoutId, that.payoutId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            bookingReference,
            professionalRef,
            professionalLogin,
            grossMinor,
            commissionMinor,
            netMinor,
            currency,
            deliveryMode,
            serviceRef,
            serviceName,
            earnedOn,
            payoutId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LedgerCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalBookingReference().map(f -> "bookingReference=" + f + ", ").orElse("") +
            optionalProfessionalRef().map(f -> "professionalRef=" + f + ", ").orElse("") +
            optionalProfessionalLogin().map(f -> "professionalLogin=" + f + ", ").orElse("") +
            optionalGrossMinor().map(f -> "grossMinor=" + f + ", ").orElse("") +
            optionalCommissionMinor().map(f -> "commissionMinor=" + f + ", ").orElse("") +
            optionalNetMinor().map(f -> "netMinor=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalDeliveryMode().map(f -> "deliveryMode=" + f + ", ").orElse("") +
            optionalServiceRef().map(f -> "serviceRef=" + f + ", ").orElse("") +
            optionalServiceName().map(f -> "serviceName=" + f + ", ").orElse("") +
            optionalEarnedOn().map(f -> "earnedOn=" + f + ", ").orElse("") +
            optionalPayoutId().map(f -> "payoutId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
