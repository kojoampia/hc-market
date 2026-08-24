package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.PayoutStatus;

/**
 * A DTO for the {@link net.jojoaddison.domain.Payout} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PayoutDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String professionalRef;

    @NotNull
    private LocalDate periodStart;

    @NotNull
    private LocalDate periodEnd;

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
    private PayoutStatus status;

    private LocalDate settledOn;

    private String bankReference;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getProfessionalRef() {
        return professionalRef;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
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

    public PayoutStatus getStatus() {
        return status;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public LocalDate getSettledOn() {
        return settledOn;
    }

    public void setSettledOn(LocalDate settledOn) {
        this.settledOn = settledOn;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PayoutDTO)) {
            return false;
        }

        PayoutDTO payoutDTO = (PayoutDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, payoutDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PayoutDTO{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", periodStart='" + getPeriodStart() + "'" +
            ", periodEnd='" + getPeriodEnd() + "'" +
            ", grossMinor=" + getGrossMinor() +
            ", commissionMinor=" + getCommissionMinor() +
            ", netMinor=" + getNetMinor() +
            ", currency='" + getCurrency() + "'" +
            ", status='" + getStatus() + "'" +
            ", settledOn='" + getSettledOn() + "'" +
            ", bankReference='" + getBankReference() + "'" +
            "}";
    }
}
