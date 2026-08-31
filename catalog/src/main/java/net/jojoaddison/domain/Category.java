package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Category.
 */
@Entity
@Table(name = "category")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Category implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 32)
    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 400)
    @Column(name = "blurb", length = 400)
    private String blurb;

    @Column(name = "icon")
    private String icon;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "category")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
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
    private Set<Professional> professionals = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Category id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return this.code;
    }

    public Category code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return this.name;
    }

    public Category name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBlurb() {
        return this.blurb;
    }

    public Category blurb(String blurb) {
        this.setBlurb(blurb);
        return this;
    }

    public void setBlurb(String blurb) {
        this.blurb = blurb;
    }

    public String getIcon() {
        return this.icon;
    }

    public Category icon(String icon) {
        this.setIcon(icon);
        return this;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public Category sortOrder(Integer sortOrder) {
        this.setSortOrder(sortOrder);
        return this;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Set<Professional> getProfessionals() {
        return this.professionals;
    }

    public void setProfessionals(Set<Professional> professionals) {
        if (this.professionals != null) {
            this.professionals.forEach(i -> i.setCategory(null));
        }
        if (professionals != null) {
            professionals.forEach(i -> i.setCategory(this));
        }
        this.professionals = professionals;
    }

    public Category professionals(Set<Professional> professionals) {
        this.setProfessionals(professionals);
        return this;
    }

    public Category addProfessional(Professional professional) {
        this.professionals.add(professional);
        professional.setCategory(this);
        return this;
    }

    public Category removeProfessional(Professional professional) {
        this.professionals.remove(professional);
        professional.setCategory(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category)) {
            return false;
        }
        return getId() != null && getId().equals(((Category) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Category{" +
            "id=" + getId() +
            ", code='" + getCode() + "'" +
            ", name='" + getName() + "'" +
            ", blurb='" + getBlurb() + "'" +
            ", icon='" + getIcon() + "'" +
            ", sortOrder=" + getSortOrder() +
            "}";
    }
}
