package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A single day that departs from the rules — a holiday, or a day with different hours.
 *
 * Named `AvailabilityOverride`, NOT `AvailabilityException`: a JPA entity whose name ends in
 * `Exception` reads as a `Throwable` everywhere it appears, and this repository has already paid
 * that tax once by renaming `Thread` to `Conversation` in messaging. `closed = true` means no slots
 * that day; `closed = false` with a window means those hours instead of the rules'.
 */
@Entity
@Table(name = "availability_override")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilityOverride implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "override_date", nullable = false)
    private LocalDate overrideDate;

    @NotNull
    @Column(name = "closed", nullable = false)
    private Boolean closed;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Size(max = 200)
    @Column(name = "note", length = 200)
    private String note;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(
        value = { "services", "availabilities", "rules", "overrides", "reviews", "credentials", "highlights", "category" },
        allowSetters = true
    )
    private Professional professional;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public AvailabilityOverride id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getOverrideDate() {
        return this.overrideDate;
    }

    public AvailabilityOverride overrideDate(LocalDate overrideDate) {
        this.setOverrideDate(overrideDate);
        return this;
    }

    public void setOverrideDate(LocalDate overrideDate) {
        this.overrideDate = overrideDate;
    }

    public Boolean getClosed() {
        return this.closed;
    }

    public AvailabilityOverride closed(Boolean closed) {
        this.setClosed(closed);
        return this;
    }

    public void setClosed(Boolean closed) {
        this.closed = closed;
    }

    public LocalTime getStartTime() {
        return this.startTime;
    }

    public AvailabilityOverride startTime(LocalTime startTime) {
        this.setStartTime(startTime);
        return this;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return this.endTime;
    }

    public AvailabilityOverride endTime(LocalTime endTime) {
        this.setEndTime(endTime);
        return this;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getNote() {
        return this.note;
    }

    public AvailabilityOverride note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public AvailabilityOverride professional(Professional professional) {
        this.setProfessional(professional);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AvailabilityOverride)) {
            return false;
        }
        return getId() != null && getId().equals(((AvailabilityOverride) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AvailabilityOverride{" +
            "id=" + getId() +
            ", overrideDate='" + getOverrideDate() + "'" +
            ", closed='" + getClosed() + "'" +
            ", startTime='" + getStartTime() + "'" +
            ", endTime='" + getEndTime() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
