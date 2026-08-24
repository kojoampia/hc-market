package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.PayoutStatus;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Payout.
 */
@Entity
@Table(name = "payout")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Payout implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @NotNull
    @Column(name = "professional_ref", nullable = false)
    private String professionalRef;

    @NotNull
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @NotNull
    @Column(name = "gross_minor", nullable = false)
    private Long grossMinor;

    @NotNull
    @Column(name = "commission_minor", nullable = false)
    private Long commissionMinor;

    @NotNull
    @Column(name = "net_minor", nullable = false)
    private Long netMinor;

    @NotNull
    @Size(max = 3)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;

    @Column(name = "settled_on")
    private LocalDate settledOn;

    @Column(name = "bank_reference")
    private String bankReference;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "payout")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "payout" }, allowSetters = true)
    private Set<Ledger> entrieses = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Payout id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public Payout reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getProfessionalRef() {
        return this.professionalRef;
    }

    public Payout professionalRef(String professionalRef) {
        this.setProfessionalRef(professionalRef);
        return this;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public LocalDate getPeriodStart() {
        return this.periodStart;
    }

    public Payout periodStart(LocalDate periodStart) {
        this.setPeriodStart(periodStart);
        return this;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return this.periodEnd;
    }

    public Payout periodEnd(LocalDate periodEnd) {
        this.setPeriodEnd(periodEnd);
        return this;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Long getGrossMinor() {
        return this.grossMinor;
    }

    public Payout grossMinor(Long grossMinor) {
        this.setGrossMinor(grossMinor);
        return this;
    }

    public void setGrossMinor(Long grossMinor) {
        this.grossMinor = grossMinor;
    }

    public Long getCommissionMinor() {
        return this.commissionMinor;
    }

    public Payout commissionMinor(Long commissionMinor) {
        this.setCommissionMinor(commissionMinor);
        return this;
    }

    public void setCommissionMinor(Long commissionMinor) {
        this.commissionMinor = commissionMinor;
    }

    public Long getNetMinor() {
        return this.netMinor;
    }

    public Payout netMinor(Long netMinor) {
        this.setNetMinor(netMinor);
        return this;
    }

    public void setNetMinor(Long netMinor) {
        this.netMinor = netMinor;
    }

    public String getCurrency() {
        return this.currency;
    }

    public Payout currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PayoutStatus getStatus() {
        return this.status;
    }

    public Payout status(PayoutStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(PayoutStatus status) {
        this.status = status;
    }

    public LocalDate getSettledOn() {
        return this.settledOn;
    }

    public Payout settledOn(LocalDate settledOn) {
        this.setSettledOn(settledOn);
        return this;
    }

    public void setSettledOn(LocalDate settledOn) {
        this.settledOn = settledOn;
    }

    public String getBankReference() {
        return this.bankReference;
    }

    public Payout bankReference(String bankReference) {
        this.setBankReference(bankReference);
        return this;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public Set<Ledger> getEntrieses() {
        return this.entrieses;
    }

    public void setEntrieses(Set<Ledger> ledgers) {
        if (this.entrieses != null) {
            this.entrieses.forEach(i -> i.setPayout(null));
        }
        if (ledgers != null) {
            ledgers.forEach(i -> i.setPayout(this));
        }
        this.entrieses = ledgers;
    }

    public Payout entrieses(Set<Ledger> ledgers) {
        this.setEntrieses(ledgers);
        return this;
    }

    public Payout addEntries(Ledger ledger) {
        this.entrieses.add(ledger);
        ledger.setPayout(this);
        return this;
    }

    public Payout removeEntries(Ledger ledger) {
        this.entrieses.remove(ledger);
        ledger.setPayout(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Payout)) {
            return false;
        }
        return getId() != null && getId().equals(((Payout) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Payout{" +
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
