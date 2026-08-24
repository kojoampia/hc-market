package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Ledger} entity.
 */
@Schema(
    description = "One row per completed or late-cancelled booking. `bookingReference` is unique, so a replayed\nbooking.completed event cannot double-credit a professional."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LedgerDTO implements Serializable {

    private Long id;

    @NotNull
    private String bookingReference;

    @NotNull
    private String professionalRef;

    @NotNull
    private Long grossMinor;

    @NotNull
    private Long commissionMinor;

    @NotNull
    private Long netMinor;

    @NotNull
    @Size(max = 3)
    private String currency;

    @NotNull
    private LocalDate earnedOn;

    private PayoutDTO payout;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getProfessionalRef() {
        return professionalRef;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public Long getGrossMinor() {
        return grossMinor;
    }

    public void setGrossMinor(Long grossMinor) {
        this.grossMinor = grossMinor;
    }

    public Long getCommissionMinor() {
        return commissionMinor;
    }

    public void setCommissionMinor(Long commissionMinor) {
        this.commissionMinor = commissionMinor;
    }

    public Long getNetMinor() {
        return netMinor;
    }

    public void setNetMinor(Long netMinor) {
        this.netMinor = netMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getEarnedOn() {
        return earnedOn;
    }

    public void setEarnedOn(LocalDate earnedOn) {
        this.earnedOn = earnedOn;
    }

    public PayoutDTO getPayout() {
        return payout;
    }

    public void setPayout(PayoutDTO payout) {
        this.payout = payout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerDTO)) {
            return false;
        }

        LedgerDTO ledgerDTO = (LedgerDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, ledgerDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LedgerDTO{" +
            "id=" + getId() +
            ", bookingReference='" + getBookingReference() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", grossMinor=" + getGrossMinor() +
            ", commissionMinor=" + getCommissionMinor() +
            ", netMinor=" + getNetMinor() +
            ", currency='" + getCurrency() + "'" +
            ", earnedOn='" + getEarnedOn() + "'" +
            ", payout=" + getPayout() +
            "}";
    }
}
