package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.BrokerageConfig} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BrokerageConfigDTO implements Serializable {

    private Long id;

    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "1")
    private BigDecimal commissionRate;

    @NotNull
    private Integer payoutLagDays;

    @NotNull
    private Integer freeCancellationHours;

    @NotNull
    private BigDecimal lateCancellationPct;

    @NotNull
    @Size(max = 3)
    private String currency;

    @NotNull
    private Instant effectiveFrom;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public Integer getPayoutLagDays() {
        return payoutLagDays;
    }

    public void setPayoutLagDays(Integer payoutLagDays) {
        this.payoutLagDays = payoutLagDays;
    }

    public Integer getFreeCancellationHours() {
        return freeCancellationHours;
    }

    public void setFreeCancellationHours(Integer freeCancellationHours) {
        this.freeCancellationHours = freeCancellationHours;
    }

    public BigDecimal getLateCancellationPct() {
        return lateCancellationPct;
    }

    public void setLateCancellationPct(BigDecimal lateCancellationPct) {
        this.lateCancellationPct = lateCancellationPct;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrokerageConfigDTO)) {
            return false;
        }

        BrokerageConfigDTO brokerageConfigDTO = (BrokerageConfigDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, brokerageConfigDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BrokerageConfigDTO{" +
            "id=" + getId() +
            ", commissionRate=" + getCommissionRate() +
            ", payoutLagDays=" + getPayoutLagDays() +
            ", freeCancellationHours=" + getFreeCancellationHours() +
            ", lateCancellationPct=" + getLateCancellationPct() +
            ", currency='" + getCurrency() + "'" +
            ", effectiveFrom='" + getEffectiveFrom() + "'" +
            "}";
    }
}
