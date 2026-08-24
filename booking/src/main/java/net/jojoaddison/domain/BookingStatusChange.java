package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.BookingStatus;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * The append-only audit the prototype had no room for.
 */
@Entity
@Table(name = "booking_status_change")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BookingStatusChange implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private BookingStatus fromStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private BookingStatus toStatus;

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
    private Booking booking;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public BookingStatusChange id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BookingStatus getFromStatus() {
        return this.fromStatus;
    }

    public BookingStatusChange fromStatus(BookingStatus fromStatus) {
        this.setFromStatus(fromStatus);
        return this;
    }

    public void setFromStatus(BookingStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public BookingStatus getToStatus() {
        return this.toStatus;
    }

    public BookingStatusChange toStatus(BookingStatus toStatus) {
        this.setToStatus(toStatus);
        return this;
    }

    public void setToStatus(BookingStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getActor() {
        return this.actor;
    }

    public BookingStatusChange actor(String actor) {
        this.setActor(actor);
        return this;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Instant getOccurredAt() {
        return this.occurredAt;
    }

    public BookingStatusChange occurredAt(Instant occurredAt) {
        this.setOccurredAt(occurredAt);
        return this;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getNote() {
        return this.note;
    }

    public BookingStatusChange note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Booking getBooking() {
        return this.booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public BookingStatusChange booking(Booking booking) {
        this.setBooking(booking);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookingStatusChange)) {
            return false;
        }
        return getId() != null && getId().equals(((BookingStatusChange) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BookingStatusChange{" +
            "id=" + getId() +
            ", fromStatus='" + getFromStatus() + "'" +
            ", toStatus='" + getToStatus() + "'" +
            ", actor='" + getActor() + "'" +
            ", occurredAt='" + getOccurredAt() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
