package net.jojoaddison.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A BrokerageConfig.
 */
@Entity
@Table(name = "brokerage_config")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BrokerageConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "1")
    @Column(name = "commission_rate", precision = 21, scale = 2, nullable = false)
    private BigDecimal commissionRate;

    @NotNull
    @Column(name = "payout_lag_days", nullable = false)
    private Integer payoutLagDays;

    @NotNull
    @Column(name = "free_cancellation_hours", nullable = false)
    private Integer freeCancellationHours;

    @NotNull
    @Column(name = "late_cancellation_pct", precision = 21, scale = 2, nullable = false)
    private BigDecimal lateCancellationPct;

    @NotNull
    @Size(max = 3)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @NotNull
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public BrokerageConfig id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getCommissionRate() {
        return this.commissionRate;
    }

    public BrokerageConfig commissionRate(BigDecimal commissionRate) {
        this.setCommissionRate(commissionRate);
        return this;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public Integer getPayoutLagDays() {
        return this.payoutLagDays;
    }

    public BrokerageConfig payoutLagDays(Integer payoutLagDays) {
        this.setPayoutLagDays(payoutLagDays);
        return this;
    }

    public void setPayoutLagDays(Integer payoutLagDays) {
        this.payoutLagDays = payoutLagDays;
    }

    public Integer getFreeCancellationHours() {
        return this.freeCancellationHours;
    }

    public BrokerageConfig freeCancellationHours(Integer freeCancellationHours) {
        this.setFreeCancellationHours(freeCancellationHours);
        return this;
    }

    public void setFreeCancellationHours(Integer freeCancellationHours) {
        this.freeCancellationHours = freeCancellationHours;
    }

    public BigDecimal getLateCancellationPct() {
        return this.lateCancellationPct;
    }

    public BrokerageConfig lateCancellationPct(BigDecimal lateCancellationPct) {
        this.setLateCancellationPct(lateCancellationPct);
        return this;
    }

    public void setLateCancellationPct(BigDecimal lateCancellationPct) {
        this.lateCancellationPct = lateCancellationPct;
    }

    public String getCurrency() {
        return this.currency;
    }

    public BrokerageConfig currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getEffectiveFrom() {
        return this.effectiveFrom;
    }

    public BrokerageConfig effectiveFrom(Instant effectiveFrom) {
        this.setEffectiveFrom(effectiveFrom);
        return this;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrokerageConfig)) {
            return false;
        }
        return getId() != null && getId().equals(((BrokerageConfig) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BrokerageConfig{" +
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
