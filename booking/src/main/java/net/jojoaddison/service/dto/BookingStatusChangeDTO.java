package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.BookingStatus;

/**
 * A DTO for the {@link net.jojoaddison.domain.BookingStatusChange} entity.
 */
@Schema(description = "The append-only audit the prototype had no room for.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BookingStatusChangeDTO implements Serializable {

    private Long id;

    private BookingStatus fromStatus;

    @NotNull
    private BookingStatus toStatus;

    @NotNull
    private String actor;

    @NotNull
    private Instant occurredAt;

    @Size(max = 400)
    private String note;

    @NotNull
    private BookingDTO booking;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BookingStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(BookingStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public BookingStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(BookingStatus toStatus) {
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

    public BookingDTO getBooking() {
        return booking;
    }

    public void setBooking(BookingDTO booking) {
        this.booking = booking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookingStatusChangeDTO)) {
            return false;
        }

        BookingStatusChangeDTO bookingStatusChangeDTO = (BookingStatusChangeDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, bookingStatusChangeDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BookingStatusChangeDTO{" +
            "id=" + getId() +
            ", fromStatus='" + getFromStatus() + "'" +
            ", toStatus='" + getToStatus() + "'" +
            ", actor='" + getActor() + "'" +
            ", occurredAt='" + getOccurredAt() + "'" +
            ", note='" + getNote() + "'" +
            ", booking=" + getBooking() +
            "}";
    }
}
