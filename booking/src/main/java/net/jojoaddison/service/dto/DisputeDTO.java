package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;

/**
 * A DTO for the {@link net.jojoaddison.domain.Dispute} entity.
 */
@Schema(
    description = "Keyed by a unique `bookingReference`, so \"one dispute per booking\" is a schema guarantee in the\nsame way \"one review per booking\" is. A second grievance about the same booking belongs on the\nexisting dispute, not beside it.\n\n`dueBy` records the prototype's promise of five working days. It is NOT enforced: there is no\nscheduler anywhere in this estate, so nothing can escalate on expiry. Recorded rather than\ndropped, because the desk can sort by it and because the gap should be visible.\n\n`refundMinor` is what an upheld dispute reverses. It is nullable and never negative here — the\nsign is applied by payout when it writes the compensating entry, so this column reads the same\nway as every other money column in the estate."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DisputeDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String bookingReference;

    @NotNull
    private CancelledBy raisedBy;

    @NotNull
    private String raisedByLogin;

    @NotNull
    private String professionalRef;

    @NotNull
    @Size(max = 1000)
    private String reason;

    @NotNull
    private DisputeStatus status;

    @NotNull
    private Instant raisedAt;

    @NotNull
    private Instant dueBy;

    @Size(max = 1000)
    private String resolution;

    private String resolvedBy;

    private Instant resolvedAt;

    @Min(value = 0L)
    private Long refundMinor;

    @Size(max = 3)
    private String currency;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public CancelledBy getRaisedBy() {
        return raisedBy;
    }

    public void setRaisedBy(CancelledBy raisedBy) {
        this.raisedBy = raisedBy;
    }

    public String getRaisedByLogin() {
        return raisedByLogin;
    }

    public void setRaisedByLogin(String raisedByLogin) {
        this.raisedByLogin = raisedByLogin;
    }

    public String getProfessionalRef() {
        return professionalRef;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getDueBy() {
        return dueBy;
    }

    public void setDueBy(Instant dueBy) {
        this.dueBy = dueBy;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getRefundMinor() {
        return refundMinor;
    }

    public void setRefundMinor(Long refundMinor) {
        this.refundMinor = refundMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisputeDTO)) {
            return false;
        }

        DisputeDTO disputeDTO = (DisputeDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, disputeDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DisputeDTO{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", bookingReference='" + getBookingReference() + "'" +
            ", raisedBy='" + getRaisedBy() + "'" +
            ", raisedByLogin='" + getRaisedByLogin() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", reason='" + getReason() + "'" +
            ", status='" + getStatus() + "'" +
            ", raisedAt='" + getRaisedAt() + "'" +
            ", dueBy='" + getDueBy() + "'" +
            ", resolution='" + getResolution() + "'" +
            ", resolvedBy='" + getResolvedBy() + "'" +
            ", resolvedAt='" + getResolvedAt() + "'" +
            ", refundMinor=" + getRefundMinor() +
            ", currency='" + getCurrency() + "'" +
            "}";
    }
}
