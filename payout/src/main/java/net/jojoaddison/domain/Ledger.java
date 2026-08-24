package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * One row per completed or late-cancelled booking. `bookingReference` is unique, so a replayed
 * booking.completed event cannot double-credit a professional.
 */
@Entity
@Table(name = "ledger")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Ledger implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "booking_reference", nullable = false, unique = true)
    private String bookingReference;

    @NotNull
    @Column(name = "professional_ref", nullable = false)
    private String professionalRef;

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
    @Column(name = "earned_on", nullable = false)
    private LocalDate earnedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "entrieses" }, allowSetters = true)
    private Payout payout;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Ledger id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingReference() {
        return this.bookingReference;
    }

    public Ledger bookingReference(String bookingReference) {
        this.setBookingReference(bookingReference);
        return this;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getProfessionalRef() {
        return this.professionalRef;
    }

    public Ledger professionalRef(String professionalRef) {
        this.setProfessionalRef(professionalRef);
        return this;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public Long getGrossMinor() {
        return this.grossMinor;
    }

    public Ledger grossMinor(Long grossMinor) {
        this.setGrossMinor(grossMinor);
        return this;
    }

    public void setGrossMinor(Long grossMinor) {
        this.grossMinor = grossMinor;
    }

    public Long getCommissionMinor() {
        return this.commissionMinor;
    }

    public Ledger commissionMinor(Long commissionMinor) {
        this.setCommissionMinor(commissionMinor);
        return this;
    }

    public void setCommissionMinor(Long commissionMinor) {
        this.commissionMinor = commissionMinor;
    }

    public Long getNetMinor() {
        return this.netMinor;
    }

    public Ledger netMinor(Long netMinor) {
        this.setNetMinor(netMinor);
        return this;
    }

    public void setNetMinor(Long netMinor) {
        this.netMinor = netMinor;
    }

    public String getCurrency() {
        return this.currency;
    }

    public Ledger currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getEarnedOn() {
        return this.earnedOn;
    }

    public Ledger earnedOn(LocalDate earnedOn) {
        this.setEarnedOn(earnedOn);
        return this;
    }

    public void setEarnedOn(LocalDate earnedOn) {
        this.earnedOn = earnedOn;
    }

    public Payout getPayout() {
        return this.payout;
    }

    public void setPayout(Payout payout) {
        this.payout = payout;
    }

    public Ledger payout(Payout payout) {
        this.setPayout(payout);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ledger)) {
            return false;
        }
        return getId() != null && getId().equals(((Ledger) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Ledger{" +
            "id=" + getId() +
            ", bookingReference='" + getBookingReference() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", grossMinor=" + getGrossMinor() +
            ", commissionMinor=" + getCommissionMinor() +
            ", netMinor=" + getNetMinor() +
            ", currency='" + getCurrency() + "'" +
            ", earnedOn='" + getEarnedOn() + "'" +
            "}";
    }
}
