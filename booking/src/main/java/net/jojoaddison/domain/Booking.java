package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Booking.
 */
@Entity
@Table(name = "booking")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Booking implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @NotNull
    @Column(name = "customer_login", nullable = false)
    private String customerLogin;

    @NotNull
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @NotNull
    @Column(name = "professional_ref", nullable = false)
    private String professionalRef;

    @NotNull
    @Column(name = "service_ref", nullable = false)
    private String serviceRef;

    @NotNull
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @NotNull
    @Min(value = 0L)
    @Column(name = "price_minor", nullable = false)
    private Long priceMinor;

    @NotNull
    @Size(max = 3)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @NotNull
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @NotNull
    @Size(max = 5)
    @Column(name = "scheduled_time", length = 5, nullable = false)
    private String scheduledTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false)
    private DeliveryMode deliveryMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Size(max = 1000)
    @Column(name = "customer_note", length = 1000)
    private String customerNote;

    @Size(max = 120)
    @Column(name = "on_behalf_of", length = 120)
    private String onBehalfOf;

    @Size(max = 400)
    @Column(name = "visit_address", length = 400)
    private String visitAddress;

    @NotNull
    @Column(name = "care_summary_shared", nullable = false)
    private Boolean careSummaryShared;

    @NotNull
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelledBy cancelledBy;

    @Size(max = 400)
    @Column(name = "cancellation_reason", length = 400)
    private String cancellationReason;

    @Column(name = "late_cancellation")
    private Boolean lateCancellation;

    @NotNull
    @Column(name = "reviewed", nullable = false)
    private Boolean reviewed;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "booking")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "booking" }, allowSetters = true)
    private Set<BookingStatusChange> histories = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Booking id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public Booking reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCustomerLogin() {
        return this.customerLogin;
    }

    public Booking customerLogin(String customerLogin) {
        this.setCustomerLogin(customerLogin);
        return this;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getCustomerName() {
        return this.customerName;
    }

    public Booking customerName(String customerName) {
        this.setCustomerName(customerName);
        return this;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProfessionalRef() {
        return this.professionalRef;
    }

    public Booking professionalRef(String professionalRef) {
        this.setProfessionalRef(professionalRef);
        return this;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public String getServiceRef() {
        return this.serviceRef;
    }

    public Booking serviceRef(String serviceRef) {
        this.setServiceRef(serviceRef);
        return this;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public Booking serviceName(String serviceName) {
        this.setServiceName(serviceName);
        return this;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Long getPriceMinor() {
        return this.priceMinor;
    }

    public Booking priceMinor(Long priceMinor) {
        this.setPriceMinor(priceMinor);
        return this;
    }

    public void setPriceMinor(Long priceMinor) {
        this.priceMinor = priceMinor;
    }

    public String getCurrency() {
        return this.currency;
    }

    public Booking currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getScheduledDate() {
        return this.scheduledDate;
    }

    public Booking scheduledDate(LocalDate scheduledDate) {
        this.setScheduledDate(scheduledDate);
        return this;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getScheduledTime() {
        return this.scheduledTime;
    }

    public Booking scheduledTime(String scheduledTime) {
        this.setScheduledTime(scheduledTime);
        return this;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public DeliveryMode getDeliveryMode() {
        return this.deliveryMode;
    }

    public Booking deliveryMode(DeliveryMode deliveryMode) {
        this.setDeliveryMode(deliveryMode);
        return this;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public BookingStatus getStatus() {
        return this.status;
    }

    public Booking status(BookingStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getCustomerNote() {
        return this.customerNote;
    }

    public Booking customerNote(String customerNote) {
        this.setCustomerNote(customerNote);
        return this;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public String getOnBehalfOf() {
        return this.onBehalfOf;
    }

    public Booking onBehalfOf(String onBehalfOf) {
        this.setOnBehalfOf(onBehalfOf);
        return this;
    }

    public void setOnBehalfOf(String onBehalfOf) {
        this.onBehalfOf = onBehalfOf;
    }

    public String getVisitAddress() {
        return this.visitAddress;
    }

    public Booking visitAddress(String visitAddress) {
        this.setVisitAddress(visitAddress);
        return this;
    }

    public void setVisitAddress(String visitAddress) {
        this.visitAddress = visitAddress;
    }

    public Boolean getCareSummaryShared() {
        return this.careSummaryShared;
    }

    public Booking careSummaryShared(Boolean careSummaryShared) {
        this.setCareSummaryShared(careSummaryShared);
        return this;
    }

    public void setCareSummaryShared(Boolean careSummaryShared) {
        this.careSummaryShared = careSummaryShared;
    }

    public Instant getRaisedAt() {
        return this.raisedAt;
    }

    public Booking raisedAt(Instant raisedAt) {
        this.setRaisedAt(raisedAt);
        return this;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getRespondedAt() {
        return this.respondedAt;
    }

    public Booking respondedAt(Instant respondedAt) {
        this.setRespondedAt(respondedAt);
        return this;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public Booking completedAt(Instant completedAt) {
        this.setCompletedAt(completedAt);
        return this;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return this.cancelledAt;
    }

    public Booking cancelledAt(Instant cancelledAt) {
        this.setCancelledAt(cancelledAt);
        return this;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public CancelledBy getCancelledBy() {
        return this.cancelledBy;
    }

    public Booking cancelledBy(CancelledBy cancelledBy) {
        this.setCancelledBy(cancelledBy);
        return this;
    }

    public void setCancelledBy(CancelledBy cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public String getCancellationReason() {
        return this.cancellationReason;
    }

    public Booking cancellationReason(String cancellationReason) {
        this.setCancellationReason(cancellationReason);
        return this;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Boolean getLateCancellation() {
        return this.lateCancellation;
    }

    public Booking lateCancellation(Boolean lateCancellation) {
        this.setLateCancellation(lateCancellation);
        return this;
    }

    public void setLateCancellation(Boolean lateCancellation) {
        this.lateCancellation = lateCancellation;
    }

    public Boolean getReviewed() {
        return this.reviewed;
    }

    public Booking reviewed(Boolean reviewed) {
        this.setReviewed(reviewed);
        return this;
    }

    public void setReviewed(Boolean reviewed) {
        this.reviewed = reviewed;
    }

    public Set<BookingStatusChange> getHistories() {
        return this.histories;
    }

    public void setHistories(Set<BookingStatusChange> bookingStatusChanges) {
        if (this.histories != null) {
            this.histories.forEach(i -> i.setBooking(null));
        }
        if (bookingStatusChanges != null) {
            bookingStatusChanges.forEach(i -> i.setBooking(this));
        }
        this.histories = bookingStatusChanges;
    }

    public Booking histories(Set<BookingStatusChange> bookingStatusChanges) {
        this.setHistories(bookingStatusChanges);
        return this;
    }

    public Booking addHistory(BookingStatusChange bookingStatusChange) {
        this.histories.add(bookingStatusChange);
        bookingStatusChange.setBooking(this);
        return this;
    }

    public Booking removeHistory(BookingStatusChange bookingStatusChange) {
        this.histories.remove(bookingStatusChange);
        bookingStatusChange.setBooking(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Booking)) {
            return false;
        }
        return getId() != null && getId().equals(((Booking) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Booking{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", customerLogin='" + getCustomerLogin() + "'" +
            ", customerName='" + getCustomerName() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", serviceRef='" + getServiceRef() + "'" +
            ", serviceName='" + getServiceName() + "'" +
            ", priceMinor=" + getPriceMinor() +
            ", currency='" + getCurrency() + "'" +
            ", scheduledDate='" + getScheduledDate() + "'" +
            ", scheduledTime='" + getScheduledTime() + "'" +
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
