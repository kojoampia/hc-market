package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import net.jojoaddison.domain.enumeration.DisputeStatus;

/**
 * A DTO for the {@link net.jojoaddison.domain.DisputeStatusChange} entity.
 */
@Schema(description = "The same append-only audit BookingStatusChange gives the booking.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DisputeStatusChangeDTO implements Serializable {

    private Long id;

    private DisputeStatus fromStatus;

    @NotNull
    private DisputeStatus toStatus;

    @NotNull
    private String actor;

    @NotNull
    private Instant occurredAt;

    @Size(max = 400)
    private String note;

    @NotNull
    private DisputeDTO dispute;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DisputeStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(DisputeStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public DisputeStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(DisputeStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public DisputeDTO getDispute() {
        return dispute;
    }

    public void setDispute(DisputeDTO dispute) {
        this.dispute = dispute;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisputeStatusChangeDTO)) {
            return false;
        }

        DisputeStatusChangeDTO disputeStatusChangeDTO = (DisputeStatusChangeDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, disputeStatusChangeDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DisputeStatusChangeDTO{" +
            "id=" + getId() +
            ", fromStatus='" + getFromStatus() + "'" +
            ", toStatus='" + getToStatus() + "'" +
            ", actor='" + getActor() + "'" +
            ", occurredAt='" + getOccurredAt() + "'" +
            ", note='" + getNote() + "'" +
            ", dispute=" + getDispute() +
            "}";
    }
}
