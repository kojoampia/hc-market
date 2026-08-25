package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.DeliveryMode;

/**
 * A DTO for the {@link net.jojoaddison.domain.Ledger} entity.
 */
@Schema(
    description = "One row per completed or late-cancelled booking. `bookingReference` is unique, so a replayed\nbooking.completed event cannot double-credit a professional.\n\nAMENDED, for the same reason `Review` gained `customerLogin` (decisions.md D8): the spec requires\n`/api/pro/**` to resolve the professional from the token and refuse anything that is not the\ncaller's, but the specified entity carried only `professionalRef` — nothing a JWT subject can be\nmatched against without asking the catalog service. `professionalLogin` makes the ownership check\nlocal to one row.\n\n`deliveryMode` is denormalised here on purpose, exactly as `grossMinor` already is. The earnings\nscreen breaks sessions down by format, and a ledger row must keep saying what it said when it was\nwritten even if the booking is later corrected — the same rule that stops a receipt changing when\na price is edited.\n\nThe money columns are all stored rather than derived, and that is not a contradiction of the\nderived-not-stored rule: commission depends on the BrokerageConfig *in force when the booking\ncompleted*, so recomputing it later from today's rate would rewrite history. What must never be\nstored is a total ACROSS rows — there is no professional.total_earnings anywhere."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LedgerDTO implements Serializable {

    private Long id;

    @NotNull
    private String bookingReference;

    @NotNull
    private String professionalRef;

    @NotNull
    private String professionalLogin;

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
    private DeliveryMode deliveryMode;

    private String serviceRef;

    private String serviceName;

    @NotNull
    private LocalDate earnedOn;

    private String reversalOf;

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

    public String getProfessionalLogin() {
        return professionalLogin;
    }

    public void setProfessionalLogin(String professionalLogin) {
        this.professionalLogin = professionalLogin;
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

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getServiceRef() {
        return serviceRef;
    }

    public void setServiceRef(String serviceRef) {
        this.serviceRef = serviceRef;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public LocalDate getEarnedOn() {
        return earnedOn;
    }

    public void setEarnedOn(LocalDate earnedOn) {
        this.earnedOn = earnedOn;
    }

    public String getReversalOf() {
        return reversalOf;
    }

    public void setReversalOf(String reversalOf) {
        this.reversalOf = reversalOf;
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
            ", professionalLogin='" + getProfessionalLogin() + "'" +
            ", grossMinor=" + getGrossMinor() +
            ", commissionMinor=" + getCommissionMinor() +
            ", netMinor=" + getNetMinor() +
            ", currency='" + getCurrency() + "'" +
            ", deliveryMode='" + getDeliveryMode() + "'" +
            ", serviceRef='" + getServiceRef() + "'" +
            ", serviceName='" + getServiceName() + "'" +
            ", earnedOn='" + getEarnedOn() + "'" +
            ", reversalOf='" + getReversalOf() + "'" +
            ", payout=" + getPayout() +
            "}";
    }
}
