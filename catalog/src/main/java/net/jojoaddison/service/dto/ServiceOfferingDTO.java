package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.ServiceOffering} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ServiceOfferingDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String name;

    @Min(value = 0)
    private Integer durationMinutes;

    @NotNull
    @Min(value = 0L)
    private Long priceMinor;

    @NotNull
    @Size(max = 3)
    private String currency;

    @Size(max = 500)
    private String description;

    @NotNull
    private Boolean active;

    private Integer sortOrder;

    @NotNull
    private ProfessionalDTO professional;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Long getPriceMinor() {
        return priceMinor;
    }

    public void setPriceMinor(Long priceMinor) {
        this.priceMinor = priceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public ProfessionalDTO getProfessional() {
        return professional;
    }

    public void setProfessional(ProfessionalDTO professional) {
        this.professional = professional;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceOfferingDTO)) {
            return false;
        }

        ServiceOfferingDTO serviceOfferingDTO = (ServiceOfferingDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, serviceOfferingDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ServiceOfferingDTO{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", name='" + getName() + "'" +
            ", durationMinutes=" + getDurationMinutes() +
            ", priceMinor=" + getPriceMinor() +
            ", currency='" + getCurrency() + "'" +
            ", description='" + getDescription() + "'" +
            ", active='" + getActive() + "'" +
            ", sortOrder=" + getSortOrder() +
            ", professional=" + getProfessional() +
            "}";
    }
}
