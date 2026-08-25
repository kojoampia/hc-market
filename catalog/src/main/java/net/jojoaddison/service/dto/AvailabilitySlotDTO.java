package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.AvailabilitySlot} entity.
 */
@Schema(
    description = "The bookable unit, and it stays a materialised ROW rather than something computed at read time\n(decisions.md D20). `taken` needs a row to lock: two customers booking the same 07:00 must collide\non a unique constraint over (professional, date, time), and with availability computed on demand\nthere is nothing to contend on and the double booking is silent. The same reasoning as the unique\n(customer_login, professional_ref) on Favourite.\n\n`slotTime` was `String maxlength(5)`. It sorted correctly only by the accident of zero-padded\n24-hour text and accepted \"7:00\" and \"25:99\" — see decisions.md D20 and D26."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilitySlotDTO implements Serializable {

    private Long id;

    @NotNull
    private LocalDate slotDate;

    @NotNull
    private LocalTime slotTime;

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

    public LocalTime getSlotTime() {
        return slotTime;
    }

    public void setSlotTime(LocalTime slotTime) {
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
