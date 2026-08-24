package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.AvailabilitySlot} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilitySlotDTO implements Serializable {

    private Long id;

    @NotNull
    private LocalDate slotDate;

    @NotNull
    @Size(max = 5)
    private String slotTime;

    @NotNull
    private Boolean taken;

    @NotNull
    private ProfessionalDTO professional;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public String getSlotTime() {
        return slotTime;
    }

    public void setSlotTime(String slotTime) {
        this.slotTime = slotTime;
    }

    public Boolean getTaken() {
        return taken;
    }

    public void setTaken(Boolean taken) {
        this.taken = taken;
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
        if (!(o instanceof AvailabilitySlotDTO)) {
            return false;
        }

        AvailabilitySlotDTO availabilitySlotDTO = (AvailabilitySlotDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, availabilitySlotDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AvailabilitySlotDTO{" +
            "id=" + getId() +
            ", slotDate='" + getSlotDate() + "'" +
            ", slotTime='" + getSlotTime() + "'" +
            ", taken='" + getTaken() + "'" +
            ", professional=" + getProfessional() +
            "}";
    }
}
