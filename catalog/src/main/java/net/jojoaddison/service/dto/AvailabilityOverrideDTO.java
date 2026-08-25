package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.AvailabilityOverride} entity.
 */
@Schema(
    description = "A single day that departs from the rules — a holiday, or a day with different hours.\n\nNamed `AvailabilityOverride`, NOT `AvailabilityException`: a JPA entity whose name ends in\n`Exception` reads as a `Throwable` everywhere it appears, and this repository has already paid\nthat tax once by renaming `Thread` to `Conversation` in messaging. `closed = true` means no slots\nthat day; `closed = false` with a window means those hours instead of the rules'."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilityOverrideDTO implements Serializable {

    private Long id;

    @NotNull
    private LocalDate overrideDate;

    @NotNull
    private Boolean closed;

    private LocalTime startTime;

    private LocalTime endTime;

    @Size(max = 200)
    private String note;

    @NotNull
    private ProfessionalDTO professional;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getOverrideDate() {
        return overrideDate;
    }

    public void setOverrideDate(LocalDate overrideDate) {
        this.overrideDate = overrideDate;
    }

    public Boolean getClosed() {
        return closed;
    }

    public void setClosed(Boolean closed) {
        this.closed = closed;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
        if (!(o instanceof AvailabilityOverrideDTO)) {
            return false;
        }

        AvailabilityOverrideDTO availabilityOverrideDTO = (AvailabilityOverrideDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, availabilityOverrideDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AvailabilityOverrideDTO{" +
            "id=" + getId() +
            ", overrideDate='" + getOverrideDate() + "'" +
            ", closed='" + getClosed() + "'" +
            ", startTime='" + getStartTime() + "'" +
            ", endTime='" + getEndTime() + "'" +
            ", note='" + getNote() + "'" +
            ", professional=" + getProfessional() +
            "}";
    }
}
