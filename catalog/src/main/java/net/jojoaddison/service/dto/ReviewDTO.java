package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Review} entity.
 */
@Schema(
    description = "`customerLogin` is spec §9's missing half: it requires a review to be backed by a COMPLETED\nbooking \"for that customer\", but the specified entity carried no customer identity to match\nagainst. It is never serialised to the public DTO — `authorName` and `authorInitials` are what\na reader sees.\n\n`bookingReference` is unique, which is what makes \"one review per booking\" a schema guarantee\nrather than a service-layer hope."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ReviewDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String customerLogin;

    @NotNull
    private String authorName;

    @Size(max = 4)
    private String authorInitials;

    @NotNull
    @Min(value = 1)
    @Max(value = 5)
    private Integer stars;

    @NotNull
    private LocalDate publishedOn;

    @Lob
    private String body;

    @Lob
    private String professionalReply;

    @NotNull
    private String bookingReference;

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

    public String getCustomerLogin() {
        return customerLogin;
    }

    public void setCustomerLogin(String customerLogin) {
        this.customerLogin = customerLogin;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorInitials() {
        return authorInitials;
    }

    public void setAuthorInitials(String authorInitials) {
        this.authorInitials = authorInitials;
    }

    public Integer getStars() {
        return stars;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(LocalDate publishedOn) {
        this.publishedOn = publishedOn;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getProfessionalReply() {
        return professionalReply;
    }

    public void setProfessionalReply(String professionalReply) {
        this.professionalReply = professionalReply;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
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
        if (!(o instanceof ReviewDTO)) {
            return false;
        }

        ReviewDTO reviewDTO = (ReviewDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, reviewDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ReviewDTO{" +
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
            ", professional=" + getProfessional() +
            "}";
    }
}
