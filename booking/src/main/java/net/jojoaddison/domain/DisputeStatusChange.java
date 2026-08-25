package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * The same append-only audit BookingStatusChange gives the booking.
 */
@Entity
@Table(name = "dispute_status_change")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DisputeStatusChange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private DisputeStatus fromStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private DisputeStatus toStatus;

    @NotNull
    @Column(name = "actor", nullable = false)
    private String actor;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Size(max = 400)
    @Column(name = "note", length = 400)
    private String note;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "histories" }, allowSetters = true)
    private Dispute dispute;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public DisputeStatusChange id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DisputeStatus getFromStatus() {
        return this.fromStatus;
    }

    public DisputeStatusChange fromStatus(DisputeStatus fromStatus) {
        this.setFromStatus(fromStatus);
        return this;
    }

    public void setFromStatus(DisputeStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public DisputeStatus getToStatus() {
        return this.toStatus;
    }

    public DisputeStatusChange toStatus(DisputeStatus toStatus) {
        this.setToStatus(toStatus);
        return this;
    }

    public void setToStatus(DisputeStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getActor() {
        return this.actor;
    }

    public DisputeStatusChange actor(String actor) {
        this.setActor(actor);
        return this;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Instant getOccurredAt() {
        return this.occurredAt;
    }

    public DisputeStatusChange occurredAt(Instant occurredAt) {
        this.setOccurredAt(occurredAt);
        return this;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getNote() {
        return this.note;
    }

    public DisputeStatusChange note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Dispute getDispute() {
        return this.dispute;
    }

    public void setDispute(Dispute dispute) {
        this.dispute = dispute;
    }

    public DisputeStatusChange dispute(Dispute dispute) {
        this.setDispute(dispute);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisputeStatusChange)) {
            return false;
        }
        return getId() != null && getId().equals(((DisputeStatusChange) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DisputeStatusChange{" +
            "id=" + getId() +
            ", fromStatus='" + getFromStatus() + "'" +
            ", toStatus='" + getToStatus() + "'" +
            ", actor='" + getActor() + "'" +
            ", occurredAt='" + getOccurredAt() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
