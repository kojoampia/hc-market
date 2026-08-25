package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Keyed by a unique `bookingReference`, so \"one dispute per booking\" is a schema guarantee in the
 * same way \"one review per booking\" is. A second grievance about the same booking belongs on the
 * existing dispute, not beside it.
 *
 * `dueBy` records the prototype's promise of five working days. It is NOT enforced: there is no
 * scheduler anywhere in this estate, so nothing can escalate on expiry. Recorded rather than
 * dropped, because the desk can sort by it and because the gap should be visible.
 *
 * `refundMinor` is what an upheld dispute reverses. It is nullable and never negative here — the
 * sign is applied by payout when it writes the compensating entry, so this column reads the same
 * way as every other money column in the estate.
 */
@Entity
@Table(name = "dispute")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Dispute implements Serializable {

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
    @Column(name = "booking_reference", nullable = false, unique = true)
    private String bookingReference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "raised_by", nullable = false)
    private CancelledBy raisedBy;

    @NotNull
    @Column(name = "raised_by_login", nullable = false)
    private String raisedByLogin;

    @NotNull
    @Column(name = "professional_ref", nullable = false)
    private String professionalRef;

    @NotNull
    @Size(max = 1000)
    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status;

    @NotNull
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @NotNull
    @Column(name = "due_by", nullable = false)
    private Instant dueBy;

    @Size(max = 1000)
    @Column(name = "resolution", length = 1000)
    private String resolution;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Min(value = 0L)
    @Column(name = "refund_minor")
    private Long refundMinor;

    @Size(max = 3)
    @Column(name = "currency", length = 3)
    private String currency;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "dispute")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "dispute" }, allowSetters = true)
    private Set<DisputeStatusChange> histories = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Dispute id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public Dispute reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getBookingReference() {
        return this.bookingReference;
    }

    public Dispute bookingReference(String bookingReference) {
        this.setBookingReference(bookingReference);
        return this;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public CancelledBy getRaisedBy() {
        return this.raisedBy;
    }

    public Dispute raisedBy(CancelledBy raisedBy) {
        this.setRaisedBy(raisedBy);
        return this;
    }

    public void setRaisedBy(CancelledBy raisedBy) {
        this.raisedBy = raisedBy;
    }

    public String getRaisedByLogin() {
        return this.raisedByLogin;
    }

    public Dispute raisedByLogin(String raisedByLogin) {
        this.setRaisedByLogin(raisedByLogin);
        return this;
    }

    public void setRaisedByLogin(String raisedByLogin) {
        this.raisedByLogin = raisedByLogin;
    }

    public String getProfessionalRef() {
        return this.professionalRef;
    }

    public Dispute professionalRef(String professionalRef) {
        this.setProfessionalRef(professionalRef);
        return this;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public String getReason() {
        return this.reason;
    }

    public Dispute reason(String reason) {
        this.setReason(reason);
        return this;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public DisputeStatus getStatus() {
        return this.status;
    }

    public Dispute status(DisputeStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public Instant getRaisedAt() {
        return this.raisedAt;
    }

    public Dispute raisedAt(Instant raisedAt) {
        this.setRaisedAt(raisedAt);
        return this;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getDueBy() {
        return this.dueBy;
    }

    public Dispute dueBy(Instant dueBy) {
        this.setDueBy(dueBy);
        return this;
    }

    public void setDueBy(Instant dueBy) {
        this.dueBy = dueBy;
    }

    public String getResolution() {
        return this.resolution;
    }

    public Dispute resolution(String resolution) {
        this.setResolution(resolution);
        return this;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getResolvedBy() {
        return this.resolvedBy;
    }

    public Dispute resolvedBy(String resolvedBy) {
        this.setResolvedBy(resolvedBy);
        return this;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return this.resolvedAt;
    }

    public Dispute resolvedAt(Instant resolvedAt) {
        this.setResolvedAt(resolvedAt);
        return this;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getRefundMinor() {
        return this.refundMinor;
    }

    public Dispute refundMinor(Long refundMinor) {
        this.setRefundMinor(refundMinor);
        return this;
    }

    public void setRefundMinor(Long refundMinor) {
        this.refundMinor = refundMinor;
    }

    public String getCurrency() {
        return this.currency;
    }

    public Dispute currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Set<DisputeStatusChange> getHistories() {
        return this.histories;
    }

    public void setHistories(Set<DisputeStatusChange> disputeStatusChanges) {
        if (this.histories != null) {
            this.histories.forEach(i -> i.setDispute(null));
        }
        if (disputeStatusChanges != null) {
            disputeStatusChanges.forEach(i -> i.setDispute(this));
        }
        this.histories = disputeStatusChanges;
    }

    public Dispute histories(Set<DisputeStatusChange> disputeStatusChanges) {
        this.setHistories(disputeStatusChanges);
        return this;
    }

    public Dispute addHistory(DisputeStatusChange disputeStatusChange) {
        this.histories.add(disputeStatusChange);
        disputeStatusChange.setDispute(this);
        return this;
    }

    public Dispute removeHistory(DisputeStatusChange disputeStatusChange) {
        this.histories.remove(disputeStatusChange);
        disputeStatusChange.setDispute(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Dispute)) {
            return false;
        }
        return getId() != null && getId().equals(((Dispute) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Dispute{" +
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
