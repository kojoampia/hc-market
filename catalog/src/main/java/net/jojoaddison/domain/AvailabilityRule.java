package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import net.jojoaddison.domain.enumeration.Weekday;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * How a professional actually thinks about availability: \"Tuesdays and Thursdays, 07:00 to 11:00,
 * hourly\". Slots are generated from these forward a fixed horizon; the rule is what a professional
 * edits, the slot is what a customer books.
 *
 * `weekday` names match `java.time.DayOfWeek` exactly so the mapping is `DayOfWeek.valueOf(name())`
 * rather than a lookup table that can drift.
 */
@Entity
@Table(name = "availability_rule")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilityRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false)
    private Weekday weekday;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @NotNull
    @Min(value = 5)
    @Max(value = 480)
    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    @NotNull
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(
        value = {
            "services",
            "availabilities",
            "rules",
            "overrides",
            "reviews",
            "credentials",
            "highlights",
            "verificationReviews",
            "category",
        },
        allowSetters = true
    )
    private Professional professional;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public AvailabilityRule id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Weekday getWeekday() {
        return this.weekday;
    }

    public AvailabilityRule weekday(Weekday weekday) {
        this.setWeekday(weekday);
        return this;
    }

    public void setWeekday(Weekday weekday) {
        this.weekday = weekday;
    }

    public LocalTime getStartTime() {
        return this.startTime;
    }

    public AvailabilityRule startTime(LocalTime startTime) {
        this.setStartTime(startTime);
        return this;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return this.endTime;
    }

    public AvailabilityRule endTime(LocalTime endTime) {
        this.setEndTime(endTime);
        return this;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getSlotMinutes() {
        return this.slotMinutes;
    }

    public AvailabilityRule slotMinutes(Integer slotMinutes) {
        this.setSlotMinutes(slotMinutes);
        return this;
    }

    public void setSlotMinutes(Integer slotMinutes) {
        this.slotMinutes = slotMinutes;
    }

    public LocalDate getValidFrom() {
        return this.validFrom;
    }

    public AvailabilityRule validFrom(LocalDate validFrom) {
        this.setValidFrom(validFrom);
        return this;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return this.validUntil;
    }

    public AvailabilityRule validUntil(LocalDate validUntil) {
        this.setValidUntil(validUntil);
        return this;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public Boolean getActive() {
        return this.active;
    }

    public AvailabilityRule active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public AvailabilityRule professional(Professional professional) {
        this.setProfessional(professional);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AvailabilityRule)) {
            return false;
        }
        return getId() != null && getId().equals(((AvailabilityRule) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AvailabilityRule{" +
            "id=" + getId() +
            ", weekday='" + getWeekday() + "'" +
            ", startTime='" + getStartTime() + "'" +
            ", endTime='" + getEndTime() + "'" +
            ", slotMinutes=" + getSlotMinutes() +
            ", validFrom='" + getValidFrom() + "'" +
            ", validUntil='" + getValidUntil() + "'" +
            ", active='" + getActive() + "'" +
            "}";
    }
}
