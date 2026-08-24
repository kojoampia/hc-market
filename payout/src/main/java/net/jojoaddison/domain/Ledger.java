package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * One row per completed or late-cancelled booking. `bookingReference` is unique, so a replayed
 * booking.completed event cannot double-credit a professional.
 *
 * AMENDED, for the same reason `Review` gained `customerLogin` (decisions.md D8): the spec requires
 * `/api/pro/**` to resolve the professional from the token and refuse anything that is not the
 * caller's, but the specified entity carried only `professionalRef` — nothing a JWT subject can be
 * matched against without asking the catalog service. `professionalLogin` makes the ownership check
 * local to one row.
 *
 * `deliveryMode` is denormalised here on purpose, exactly as `grossMinor` already is. The earnings
 * screen breaks sessions down by format, and a ledger row must keep saying what it said when it was
 * written even if the booking is later corrected — the same rule that stops a receipt changing when
 * a price is edited.
 *
 * The money columns are all stored rather than derived, and that is not a contradiction of the
 * derived-not-stored rule: commission depends on the BrokerageConfig *in force when the booking
 * completed*, so recomputing it later from today's rate would rewrite history. What must never be
 * stored is a total ACROSS rows — there is no professional.total_earnings anywhere.
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
    @Column(name = "professional_login", nullable = false)
    private String professionalLogin;

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
    @Column(name = "delivery_mode", nullable = false)
    private DeliveryMode deliveryMode;

    @Column(name = "service_ref")
    private String serviceRef;

    @Column(name = "service_name")
    private String serviceName;

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

    public String getProfessionalLogin() {
        return this.professionalLogin;
    }

    public Ledger professionalLogin(String professionalLogin) {
        this.setProfessionalLogin(professionalLogin);
        return this;
    }

    public void setProfessionalLogin(String professionalLogin) {
        this.professionalLogin = professionalLogin;
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

    public DeliveryMode getDeliveryMode() {
        return this.deliveryMode;
    }

    public Ledger deliveryMode(DeliveryMode deliveryMode) {
        this.setDeliveryMode(deliveryMode);
        return this;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getServiceRef() {
        return this.serviceRef;
    }

    public Ledger serviceRef(String serviceRef) {
        this.setServiceRef(serviceRef);
        return this;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public Ledger serviceName(String serviceName) {
        this.setServiceName(serviceName);
        return this;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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
            ", professionalLogin='" + getProfessionalLogin() + "'" +
            ", grossMinor=" + getGrossMinor() +
            ", commissionMinor=" + getCommissionMinor() +
            ", netMinor=" + getNetMinor() +
            ", currency='" + getCurrency() + "'" +
            ", deliveryMode='" + getDeliveryMode() + "'" +
            ", serviceRef='" + getServiceRef() + "'" +
            ", serviceName='" + getServiceName() + "'" +
            ", earnedOn='" + getEarnedOn() + "'" +
            "}";
    }
}
