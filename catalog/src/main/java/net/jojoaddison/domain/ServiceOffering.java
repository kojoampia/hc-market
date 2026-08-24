package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A ServiceOffering.
 */
@Entity
@Table(name = "service_offering")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ServiceOffering implements Serializable {

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
    @Column(name = "name", nullable = false)
    private String name;

    @Min(value = 0)
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @NotNull
    @Min(value = 0L)
    @Column(name = "price_minor", nullable = false)
    private Long priceMinor;

    @NotNull
    @Size(max = 3)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "services", "availabilities", "reviews", "credentials", "highlights", "category" }, allowSetters = true)
    private Professional professional;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ServiceOffering id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public ServiceOffering reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getName() {
        return this.name;
    }

    public ServiceOffering name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDurationMinutes() {
        return this.durationMinutes;
    }

    public ServiceOffering durationMinutes(Integer durationMinutes) {
        this.setDurationMinutes(durationMinutes);
        return this;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Long getPriceMinor() {
        return this.priceMinor;
    }

    public ServiceOffering priceMinor(Long priceMinor) {
        this.setPriceMinor(priceMinor);
        return this;
    }

    public void setPriceMinor(Long priceMinor) {
        this.priceMinor = priceMinor;
    }

    public String getCurrency() {
        return this.currency;
    }

    public ServiceOffering currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return this.description;
    }

    public ServiceOffering description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return this.active;
    }

    public ServiceOffering active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public ServiceOffering sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public ServiceOffering professional(Professional professional) {
        this.setProfessional(professional);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceOffering)) {
            return false;
        }
        return getId() != null && getId().equals(((ServiceOffering) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ServiceOffering{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", name='" + getName() + "'" +
            ", durationMinutes=" + getDurationMinutes() +
            ", priceMinor=" + getPriceMinor() +
            ", currency='" + getCurrency() + "'" +
            ", description='" + getDescription() + "'" +
            ", active='" + getActive() + "'" +
            ", sortOrder=" + getSortOrder() +
            "}";
    }
}
