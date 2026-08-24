package net.jojoaddison.service.dto;

import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.VerificationState;

/**
 * A DTO for the {@link net.jojoaddison.domain.Professional} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ProfessionalDTO implements Serializable {

    private Long id;

    @NotNull
    private String reference;

    @NotNull
    private String userLogin;

    @NotNull
    private String displayName;

    @Size(max = 4)
    private String initials;

    @NotNull
    @Size(max = 160)
    private String headline;

    @NotNull
    private String speciality;

    @NotNull
    private String city;

    @NotNull
    @Size(max = 2)
    private String countryCode;

    @Min(value = 0)
    private Integer yearsPractising;

    @NotNull
    private VerificationState verification;

    @NotNull
    private Boolean insured;

    @NotNull
    private Boolean policeClearance;

    private Integer responseMinutes;

    @Min(value = 0)
    @Max(value = 100)
    private Integer rebookRatePct;

    @Lob
    private String bio;

    private String languages;

    private String deliveryModes;

    @Size(max = 7)
    private String avatarGradientFrom;

    @Size(max = 7)
    private String avatarGradientTo;

    private Instant publishedAt;

    @NotNull
    private CategoryDTO category;

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

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSpeciality() {
        return speciality;
    }

    public void setSpeciality(String speciality) {
        this.speciality = speciality;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public Integer getYearsPractising() {
        return yearsPractising;
    }

    public void setYearsPractising(Integer yearsPractising) {
        this.yearsPractising = yearsPractising;
    }

    public VerificationState getVerification() {
        return verification;
    }

    public void setVerification(VerificationState verification) {
        this.verification = verification;
    }

    public Boolean getInsured() {
        return insured;
    }

    public void setInsured(Boolean insured) {
        this.insured = insured;
    }

    public Boolean getPoliceClearance() {
        return policeClearance;
    }

    public void setPoliceClearance(Boolean policeClearance) {
        this.policeClearance = policeClearance;
    }

    public Integer getResponseMinutes() {
        return responseMinutes;
    }

    public void setResponseMinutes(Integer responseMinutes) {
        this.responseMinutes = responseMinutes;
    }

    public Integer getRebookRatePct() {
        return rebookRatePct;
    }

    public void setRebookRatePct(Integer rebookRatePct) {
        this.rebookRatePct = rebookRatePct;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public String getDeliveryModes() {
        return deliveryModes;
    }

    public void setDeliveryModes(String deliveryModes) {
        this.deliveryModes = deliveryModes;
    }

    public String getAvatarGradientFrom() {
        return avatarGradientFrom;
    }

    public void setAvatarGradientFrom(String avatarGradientFrom) {
        this.avatarGradientFrom = avatarGradientFrom;
    }

    public String getAvatarGradientTo() {
        return avatarGradientTo;
    }

    public void setAvatarGradientTo(String avatarGradientTo) {
        this.avatarGradientTo = avatarGradientTo;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public CategoryDTO getCategory() {
        return category;
    }

    public void setCategory(CategoryDTO category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfessionalDTO)) {
            return false;
        }

        ProfessionalDTO professionalDTO = (ProfessionalDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, professionalDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProfessionalDTO{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", userLogin='" + getUserLogin() + "'" +
            ", displayName='" + getDisplayName() + "'" +
            ", initials='" + getInitials() + "'" +
            ", headline='" + getHeadline() + "'" +
            ", speciality='" + getSpeciality() + "'" +
            ", city='" + getCity() + "'" +
            ", countryCode='" + getCountryCode() + "'" +
            ", yearsPractising=" + getYearsPractising() +
            ", verification='" + getVerification() + "'" +
            ", insured='" + getInsured() + "'" +
            ", policeClearance='" + getPoliceClearance() + "'" +
            ", responseMinutes=" + getResponseMinutes() +
            ", rebookRatePct=" + getRebookRatePct() +
            ", bio='" + getBio() + "'" +
            ", languages='" + getLanguages() + "'" +
            ", deliveryModes='" + getDeliveryModes() + "'" +
            ", avatarGradientFrom='" + getAvatarGradientFrom() + "'" +
            ", avatarGradientTo='" + getAvatarGradientTo() + "'" +
            ", publishedAt='" + getPublishedAt() + "'" +
            ", category=" + getCategory() +
            "}";
    }
}
