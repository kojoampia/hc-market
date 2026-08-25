package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * The customer's Saved list.
 *
 * Deliberately NOT a relationship to Professional. A favourite is keyed by the professional's
 * business reference and the customer's login, both of which are stable and meaningful outside this
 * service; a foreign key would mean a customer's saved list could not survive a professional being
 * removed from the catalogue, and \"the person I saved is gone\" is a thing the screen should be able
 * to say rather than a row that vanishes.
 *
 * `customerLogin` + `professionalRef` is unique: saving twice is the same as saving once, and the
 * schema is what makes that true rather than a check in the resource.
 */
@Schema(
    description = "The customer's Saved list.\n\nDeliberately NOT a relationship to Professional. A favourite is keyed by the professional's\nbusiness reference and the customer's login, both of which are stable and meaningful outside this\nservice; a foreign key would mean a customer's saved list could not survive a professional being\nremoved from the catalogue, and \"the person I saved is gone\" is a thing the screen should be able\nto say rather than a row that vanishes.\n\n`customerLogin` + `professionalRef` is unique: saving twice is the same as saving once, and the\nschema is what makes that true rather than a check in the resource."
)
@Entity
@Table(name = "favourite")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Favourite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "customer_login", nullable = false)
    private String customerLogin;

    @NotNull
    @Column(name = "professional_ref", nullable = false)
    private String professionalRef;

    @NotNull
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Favourite id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerLogin() {
        return this.customerLogin;
    }

    public Favourite customerLogin(String customerLogin) {
        this.setCustomerLogin(customerLogin);
        return this;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getProfessionalRef() {
        return this.professionalRef;
    }

    public Favourite professionalRef(String professionalRef) {
        this.setProfessionalRef(professionalRef);
        return this;
    }

    public void setProfessionalRef(String professionalRef) {
        this.professionalRef = professionalRef;
    }

    public Instant getAddedAt() {
        return this.addedAt;
    }

    public Favourite addedAt(Instant addedAt) {
        this.setAddedAt(addedAt);
        return this;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Favourite)) {
            return false;
        }
        return getId() != null && getId().equals(((Favourite) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Favourite{" +
            "id=" + getId() +
            ", customerLogin='" + getCustomerLogin() + "'" +
            ", professionalRef='" + getProfessionalRef() + "'" +
            ", addedAt='" + getAddedAt() + "'" +
            "}";
    }
}
