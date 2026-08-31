package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * The bookable unit, and it stays a materialised ROW rather than something computed at read time
 * (decisions.md D20). `taken` needs a row to lock: two customers booking the same 07:00 must collide
 * on a unique constraint over (professional, date, time), and with availability computed on demand
 * there is nothing to contend on and the double booking is silent. The same reasoning as the unique
 * (customer_login, professional_ref) on Favourite.
 *
 * `slotTime` was `String maxlength(5)`. It sorted correctly only by the accident of zero-padded
 * 24-hour text and accepted \"7:00\" and \"25:99\" — see decisions.md D20 and D26.
 */
@Entity
@Table(name = "availability_slot")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AvailabilitySlot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @NotNull
    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @NotNull
    @Column(name = "taken", nullable = false)
    private Boolean taken;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(
        value = {
            "services",
            "availabilities",
            "rules",
            "overrides",
            "reviews",
            "credentials",
            "highlights",
            "verificationReviews",
            "category",
        },
        allowSetters = true
    )
    private Professional professional;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public AvailabilitySlot id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSlotDate() {
        return this.slotDate;
    }

    public AvailabilitySlot slotDate(LocalDate slotDate) {
        this.setSlotDate(slotDate);
        return this;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalTime getSlotTime() {
        return this.slotTime;
    }

    public AvailabilitySlot slotTime(LocalTime slotTime) {
        this.setSlotTime(slotTime);
        return this;
    }

    public void setSlotTime(LocalTime slotTime) {
        this.slotTime = slotTime;
    }

    public Boolean getTaken() {
        return this.taken;
    }

    public AvailabilitySlot taken(Boolean taken) {
        this.setTaken(taken);
        return this;
    }

    public void setTaken(Boolean taken) {
        this.taken = taken;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public AvailabilitySlot professional(Professional professional) {
        this.setProfessional(professional);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AvailabilitySlot)) {
            return false;
        }
        return getId() != null && getId().equals(((AvailabilitySlot) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AvailabilitySlot{" +
            "id=" + getId() +
            ", slotDate='" + getSlotDate() + "'" +
            ", slotTime='" + getSlotTime() + "'" +
            ", taken='" + getTaken() + "'" +
            "}";
    }
}
