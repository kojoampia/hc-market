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
 * `customerLogin` is spec §9's missing half: it requires a review to be backed by a COMPLETED
 * booking \"for that customer\", but the specified entity carried no customer identity to match
 * against. It is never serialised to the public DTO — `authorName` and `authorInitials` are what
 * a reader sees.
 *
 * `bookingReference` is unique, which is what makes \"one review per booking\" a schema guarantee
 * rather than a service-layer hope.
 */
@Entity
@Table(name = "review")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Review implements Serializable {

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
    @Column(name = "customer_login", nullable = false)
    private String customerLogin;

    @NotNull
    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Size(max = 4)
    @Column(name = "author_initials", length = 4)
    private String authorInitials;

    @NotNull
    @Min(value = 1)
    @Max(value = 5)
    @Column(name = "stars", nullable = false)
    private Integer stars;

    @NotNull
    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    @Lob
    @Column(name = "professional_reply")
    private String professionalReply;

    @NotNull
    @Column(name = "booking_reference", nullable = false, unique = true)
    private String bookingReference;

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

    public Review id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public Review reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCustomerLogin() {
        return this.customerLogin;
    }

    public Review customerLogin(String customerLogin) {
        this.setCustomerLogin(customerLogin);
        return this;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getAuthorName() {
        return this.authorName;
    }

    public Review authorName(String authorName) {
        this.setAuthorName(authorName);
        return this;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorInitials() {
        return this.authorInitials;
    }

    public Review authorInitials(String authorInitials) {
        this.setAuthorInitials(authorInitials);
        return this;
    }

    public void setAuthorInitials(String authorInitials) {
        this.authorInitials = authorInitials;
    }

    public Integer getStars() {
        return this.stars;
    }

    public Review stars(Integer stars) {
        this.setStars(stars);
        return this;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public LocalDate getPublishedOn() {
        return this.publishedOn;
    }

    public Review publishedOn(LocalDate publishedOn) {
        this.setPublishedOn(publishedOn);
        return this;
    }

    public void setPublishedOn(LocalDate publishedOn) {
        this.publishedOn = publishedOn;
    }

    public String getBody() {
        return this.body;
    }

    public Review body(String body) {
        this.setBody(body);
        return this;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getProfessionalReply() {
        return this.professionalReply;
    }

    public Review professionalReply(String professionalReply) {
        this.setProfessionalReply(professionalReply);
        return this;
    }

    public void setProfessionalReply(String professionalReply) {
        this.professionalReply = professionalReply;
    }

    public String getBookingReference() {
        return this.bookingReference;
    }

    public Review bookingReference(String bookingReference) {
        this.setBookingReference(bookingReference);
        return this;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public Review professional(Professional professional) {
        this.setProfessional(professional);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Review)) {
            return false;
        }
        return getId() != null && getId().equals(((Review) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Review{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", customerLogin='" + getCustomerLogin() + "'" +
            ", authorName='" + getAuthorName() + "'" +
            ", authorInitials='" + getAuthorInitials() + "'" +
            ", stars=" + getStars() +
            ", publishedOn='" + getPublishedOn() + "'" +
            ", body='" + getBody() + "'" +
            ", professionalReply='" + getProfessionalReply() + "'" +
            ", bookingReference='" + getBookingReference() + "'" +
            "}";
    }
}
