package net.jojoaddison.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link net.jojoaddison.domain.Dispute} entity. This class is used
 * in {@link net.jojoaddison.web.rest.DisputeResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /disputes?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DisputeCriteria implements Serializable, Criteria {

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

    /**
     * Class for filtering DisputeStatus
     */
    public static class DisputeStatusFilter extends Filter<DisputeStatus> {

        public DisputeStatusFilter() {}

        public DisputeStatusFilter(DisputeStatusFilter filter) {
            super(filter);
        }

        @Override
        public DisputeStatusFilter copy() {
            return new DisputeStatusFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter reference;

    private StringFilter bookingReference;

    private CancelledByFilter raisedBy;

    private StringFilter raisedByLogin;

    private StringFilter professionalRef;

    private StringFilter reason;

    private DisputeStatusFilter status;

    private InstantFilter raisedAt;

    private InstantFilter dueBy;

    private StringFilter resolution;

    private StringFilter resolvedBy;

    private InstantFilter resolvedAt;

    private LongFilter refundMinor;

    private StringFilter currency;

    private LongFilter historyId;

    private Boolean distinct;

    public DisputeCriteria() {}

    public DisputeCriteria(DisputeCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.reference = other.optionalReference().map(StringFilter::copy).orElse(null);
        this.bookingReference = other.optionalBookingReference().map(StringFilter::copy).orElse(null);
        this.raisedBy = other.optionalRaisedBy().map(CancelledByFilter::copy).orElse(null);
        this.raisedByLogin = other.optionalRaisedByLogin().map(StringFilter::copy).orElse(null);
        this.professionalRef = other.optionalProfessionalRef().map(StringFilter::copy).orElse(null);
        this.reason = other.optionalReason().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(DisputeStatusFilter::copy).orElse(null);
        this.raisedAt = other.optionalRaisedAt().map(InstantFilter::copy).orElse(null);
        this.dueBy = other.optionalDueBy().map(InstantFilter::copy).orElse(null);
        this.resolution = other.optionalResolution().map(StringFilter::copy).orElse(null);
        this.resolvedBy = other.optionalResolvedBy().map(StringFilter::copy).orElse(null);
        this.resolvedAt = other.optionalResolvedAt().map(InstantFilter::copy).orElse(null);
        this.refundMinor = other.optionalRefundMinor().map(LongFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(StringFilter::copy).orElse(null);
        this.historyId = other.optionalHistoryId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public DisputeCriteria copy() {
        return new DisputeCriteria(this);
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

    public CancelledByFilter getRaisedBy() {
        return raisedBy;
    }

    public Optional<CancelledByFilter> optionalRaisedBy() {
        return Optional.ofNullable(raisedBy);
    }

    public CancelledByFilter raisedBy() {
        if (raisedBy == null) {
            setRaisedBy(new CancelledByFilter());
        }
        return raisedBy;
    }

    public void setRaisedBy(CancelledByFilter raisedBy) {
        this.raisedBy = raisedBy;
    }

    public StringFilter getRaisedByLogin() {
        return raisedByLogin;
    }

    public Optional<StringFilter> optionalRaisedByLogin() {
        return Optional.ofNullable(raisedByLogin);
    }

    public StringFilter raisedByLogin() {
        if (raisedByLogin == null) {
            setRaisedByLogin(new StringFilter());
        }
        return raisedByLogin;
    }

    public void setRaisedByLogin(StringFilter raisedByLogin) {
        this.raisedByLogin = raisedByLogin;
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

    public StringFilter getReason() {
        return reason;
    }

    public Optional<StringFilter> optionalReason() {
        return Optional.ofNullable(reason);
    }

    public StringFilter reason() {
        if (reason == null) {
            setReason(new StringFilter());
        }
        return reason;
    }

    public void setReason(StringFilter reason) {
        this.reason = reason;
    }

    public DisputeStatusFilter getStatus() {
        return status;
    }

    public Optional<DisputeStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public DisputeStatusFilter status() {
        if (status == null) {
            setStatus(new DisputeStatusFilter());
        }
        return status;
    }

    public void setStatus(DisputeStatusFilter status) {
        this.status = status;
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

    public InstantFilter getDueBy() {
        return dueBy;
    }

    public Optional<InstantFilter> optionalDueBy() {
        return Optional.ofNullable(dueBy);
    }

    public InstantFilter dueBy() {
        if (dueBy == null) {
            setDueBy(new InstantFilter());
        }
        return dueBy;
    }

    public void setDueBy(InstantFilter dueBy) {
        this.dueBy = dueBy;
    }

    public StringFilter getResolution() {
        return resolution;
    }

    public Optional<StringFilter> optionalResolution() {
        return Optional.ofNullable(resolution);
    }

    public StringFilter resolution() {
        if (resolution == null) {
            setResolution(new StringFilter());
        }
        return resolution;
    }

    public void setResolution(StringFilter resolution) {
        this.resolution = resolution;
    }

    public StringFilter getResolvedBy() {
        return resolvedBy;
    }

    public Optional<StringFilter> optionalResolvedBy() {
        return Optional.ofNullable(resolvedBy);
    }

    public StringFilter resolvedBy() {
        if (resolvedBy == null) {
            setResolvedBy(new StringFilter());
        }
        return resolvedBy;
    }

    public void setResolvedBy(StringFilter resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public InstantFilter getResolvedAt() {
        return resolvedAt;
    }

    public Optional<InstantFilter> optionalResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public InstantFilter resolvedAt() {
        if (resolvedAt == null) {
            setResolvedAt(new InstantFilter());
        }
        return resolvedAt;
    }

    public void setResolvedAt(InstantFilter resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LongFilter getRefundMinor() {
        return refundMinor;
    }

    public Optional<LongFilter> optionalRefundMinor() {
        return Optional.ofNullable(refundMinor);
    }

    public LongFilter refundMinor() {
        if (refundMinor == null) {
            setRefundMinor(new LongFilter());
        }
        return refundMinor;
    }

    public void setRefundMinor(LongFilter refundMinor) {
        this.refundMinor = refundMinor;
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
        final DisputeCriteria that = (DisputeCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(reference, that.reference) &&
            Objects.equals(bookingReference, that.bookingReference) &&
            Objects.equals(raisedBy, that.raisedBy) &&
            Objects.equals(raisedByLogin, that.raisedByLogin) &&
            Objects.equals(professionalRef, that.professionalRef) &&
            Objects.equals(reason, that.reason) &&
            Objects.equals(status, that.status) &&
            Objects.equals(raisedAt, that.raisedAt) &&
            Objects.equals(dueBy, that.dueBy) &&
            Objects.equals(resolution, that.resolution) &&
            Objects.equals(resolvedBy, that.resolvedBy) &&
            Objects.equals(resolvedAt, that.resolvedAt) &&
            Objects.equals(refundMinor, that.refundMinor) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(historyId, that.historyId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            reference,
            bookingReference,
            raisedBy,
            raisedByLogin,
            professionalRef,
            reason,
            status,
            raisedAt,
            dueBy,
            resolution,
            resolvedBy,
            resolvedAt,
            refundMinor,
            currency,
            historyId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DisputeCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalReference().map(f -> "reference=" + f + ", ").orElse("") +
            optionalBookingReference().map(f -> "bookingReference=" + f + ", ").orElse("") +
            optionalRaisedBy().map(f -> "raisedBy=" + f + ", ").orElse("") +
            optionalRaisedByLogin().map(f -> "raisedByLogin=" + f + ", ").orElse("") +
            optionalProfessionalRef().map(f -> "professionalRef=" + f + ", ").orElse("") +
            optionalReason().map(f -> "reason=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalRaisedAt().map(f -> "raisedAt=" + f + ", ").orElse("") +
            optionalDueBy().map(f -> "dueBy=" + f + ", ").orElse("") +
            optionalResolution().map(f -> "resolution=" + f + ", ").orElse("") +
            optionalResolvedBy().map(f -> "resolvedBy=" + f + ", ").orElse("") +
            optionalResolvedAt().map(f -> "resolvedAt=" + f + ", ").orElse("") +
            optionalRefundMinor().map(f -> "refundMinor=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalHistoryId().map(f -> "historyId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
