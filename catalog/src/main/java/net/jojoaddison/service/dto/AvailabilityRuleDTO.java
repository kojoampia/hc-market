package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.Weekday;

/**
 * A DTO for the {@link net.jojoaddison.domain.AvailabilityRule} entity.
 */
@Schema(
    description = "How a professional actually thinks about availability: \"Tuesdays and Thursdays, 07:00 to 11:00,\nhourly\". Slots are generated from these forward a fixed horizon; the rule is what a professional\nedits, the slot is what a customer books.\n\n`weekday` names match `java.time.DayOfWeek` exactly so the mapping is `DayOfWeek.valueOf(name())`\nrather than a lookup table that can drift."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilityRuleDTO implements Serializable {

    private Long id;

    @NotNull
    private Weekday weekday;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    @Min(value = 5)
    @Max(value = 480)
    private Integer slotMinutes;

    @NotNull
    private LocalDate validFrom;

    private LocalDate validUntil;

    @NotNull
    private Boolean active;

    @NotNull
    private ProfessionalDTO professional;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Weekday getWeekday() {
        return weekday;
    }

    public void setWeekday(Weekday weekday) {
        this.weekday = weekday;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getSlotMinutes() {
        return slotMinutes;
    }

    public void setSlotMinutes(Integer slotMinutes) {
        this.slotMinutes = slotMinutes;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public ProfessionalDTO getProfessional() {
        return professional;
    }

    public void setProfessional(ProfessionalDTO professional) {
        this.professional = professional;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AvailabilityRuleDTO)) {
            return false;
        }

        AvailabilityRuleDTO availabilityRuleDTO = (AvailabilityRuleDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, availabilityRuleDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AvailabilityRuleDTO{" +
            "id=" + getId() +
            ", weekday='" + getWeekday() + "'" +
            ", startTime='" + getStartTime() + "'" +
            ", endTime='" + getEndTime() + "'" +
            ", slotMinutes=" + getSlotMinutes() +
            ", validFrom='" + getValidFrom() + "'" +
            ", validUntil='" + getValidUntil() + "'" +
            ", active='" + getActive() + "'" +
            ", professional=" + getProfessional() +
            "}";
    }
}
