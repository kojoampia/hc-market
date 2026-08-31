package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DeliveryMode;

/**
 * A DTO for the {@link net.jojoaddison.domain.Booking} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BookingDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String customerLogin;

    @NotNull
    private String customerName;

    @NotNull
    private String professionalRef;

    @NotNull
    private String professionalLogin;

    @NotNull
    private String serviceRef;

    @NotNull
    private String serviceName;

    @NotNull
    @Min(value = 0L)
    private Long priceMinor;

    @NotNull
    @Size(max = 3)
    private String currency;

    @NotNull
    private LocalDate scheduledDate;

    @NotNull
    private LocalTime scheduledTime;

    @NotNull
    @Size(max = 64)
    private String zoneId;

    @NotNull
    private DeliveryMode deliveryMode;

    @NotNull
    private BookingStatus status;

    @Size(max = 1000)
    private String customerNote;

    @Size(max = 120)
    private String onBehalfOf;

    @Size(max = 400)
    private String visitAddress;

    @NotNull
    private Boolean careSummaryShared;

    @NotNull
    private Instant raisedAt;

    private Instant respondedAt;

    private Instant completedAt;

    private Instant cancelledAt;

    private CancelledBy cancelledBy;

    @Size(max = 400)
    private String cancellationReason;

    private Boolean lateCancellation;

    @NotNull
    private Boolean reviewed;

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

    public String getCustomerLogin() {
        return customerLogin;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProfessionalRef() {
        return professionalRef;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public String getProfessionalLogin() {
        return professionalLogin;
    }

    public void setProfessionalLogin(String professionalLogin) {
        this.professionalLogin = professionalLogin;
    }

    public String getServiceRef() {
        return serviceRef;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Long getPriceMinor() {
        return priceMinor;
    }

    public void setPriceMinor(Long priceMinor) {
        this.priceMinor = priceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public LocalTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public String getOnBehalfOf() {
        return onBehalfOf;
    }

    public void setOnBehalfOf(String onBehalfOf) {
        this.onBehalfOf = onBehalfOf;
    }

    public String getVisitAddress() {
        return visitAddress;
    }

    public void setVisitAddress(String visitAddress) {
        this.visitAddress = visitAddress;
    }

    public Boolean getCareSummaryShared() {
        return careSummaryShared;
    }

    public void setCareSummaryShared(Boolean careSummaryShared) {
        this.careSummaryShared = careSummaryShared;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public CancelledBy getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(CancelledBy cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Boolean getLateCancellation() {
        return lateCancellation;
    }

    public void setLateCancellation(Boolean lateCancellation) {
        this.lateCancellation = lateCancellation;
    }

    public Boolean getReviewed() {
        return reviewed;
    }

    public void setReviewed(Boolean reviewed) {
        this.reviewed = reviewed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookingDTO)) {
            return false;
        }

        BookingDTO bookingDTO = (BookingDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, bookingDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BookingDTO{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", customerLogin='" + getCustomerLogin() + "'" +
            ", customerName='" + getCustomerName() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", professionalLogin='" + getProfessionalLogin() + "'" +
            ", serviceRef='" + getServiceRef() + "'" +
            ", serviceName='" + getServiceName() + "'" +
            ", priceMinor=" + getPriceMinor() +
            ", currency='" + getCurrency() + "'" +
            ", scheduledDate='" + getScheduledDate() + "'" +
            ", scheduledTime='" + getScheduledTime() + "'" +
            ", zoneId='" + getZoneId() + "'" +
            ", deliveryMode='" + getDeliveryMode() + "'" +
            ", status='" + getStatus() + "'" +
            ", customerNote='" + getCustomerNote() + "'" +
            ", onBehalfOf='" + getOnBehalfOf() + "'" +
            ", visitAddress='" + getVisitAddress() + "'" +
            ", careSummaryShared='" + getCareSummaryShared() + "'" +
            ", raisedAt='" + getRaisedAt() + "'" +
            ", respondedAt='" + getRespondedAt() + "'" +
            ", completedAt='" + getCompletedAt() + "'" +
            ", cancelledAt='" + getCancelledAt() + "'" +
            ", cancelledBy='" + getCancelledBy() + "'" +
            ", cancellationReason='" + getCancellationReason() + "'" +
            ", lateCancellation='" + getLateCancellation() + "'" +
            ", reviewed='" + getReviewed() + "'" +
            "}";
    }
}
