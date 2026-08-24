package net.jojoaddison.service.criteria;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.VerificationState;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link net.jojoaddison.domain.Professional} entity. This class is used
 * in {@link net.jojoaddison.web.rest.ProfessionalResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /professionals?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ProfessionalCriteria implements Serializable, Criteria {

    /**
     * Class for filtering VerificationState
     */
    public static class VerificationStateFilter extends Filter<VerificationState> {

        public VerificationStateFilter() {}

        public VerificationStateFilter(VerificationStateFilter filter) {
            super(filter);
        }

        @Override
        public VerificationStateFilter copy() {
            return new VerificationStateFilter(this);
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter reference;

    private StringFilter userLogin;

    private StringFilter displayName;

    private StringFilter initials;

    private StringFilter headline;

    private StringFilter speciality;

    private StringFilter city;

    private StringFilter countryCode;

    private IntegerFilter yearsPractising;

    private VerificationStateFilter verification;

    private BooleanFilter insured;

    private BooleanFilter policeClearance;

    private IntegerFilter responseMinutes;

    private IntegerFilter rebookRatePct;

    private StringFilter languages;

    private StringFilter deliveryModes;

    private StringFilter avatarGradientFrom;

    private StringFilter avatarGradientTo;

    private InstantFilter publishedAt;

    private LongFilter serviceId;

    private LongFilter availabilityId;

    private LongFilter reviewId;

    private LongFilter credentialId;

    private LongFilter highlightId;

    private LongFilter categoryId;

    private Boolean distinct;

    public ProfessionalCriteria() {}

    public ProfessionalCriteria(ProfessionalCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.reference = other.optionalReference().map(StringFilter::copy).orElse(null);
        this.userLogin = other.optionalUserLogin().map(StringFilter::copy).orElse(null);
        this.displayName = other.optionalDisplayName().map(StringFilter::copy).orElse(null);
        this.initials = other.optionalInitials().map(StringFilter::copy).orElse(null);
        this.headline = other.optionalHeadline().map(StringFilter::copy).orElse(null);
        this.speciality = other.optionalSpeciality().map(StringFilter::copy).orElse(null);
        this.city = other.optionalCity().map(StringFilter::copy).orElse(null);
        this.countryCode = other.optionalCountryCode().map(StringFilter::copy).orElse(null);
        this.yearsPractising = other.optionalYearsPractising().map(IntegerFilter::copy).orElse(null);
        this.verification = other.optionalVerification().map(VerificationStateFilter::copy).orElse(null);
        this.insured = other.optionalInsured().map(BooleanFilter::copy).orElse(null);
        this.policeClearance = other.optionalPoliceClearance().map(BooleanFilter::copy).orElse(null);
        this.responseMinutes = other.optionalResponseMinutes().map(IntegerFilter::copy).orElse(null);
        this.rebookRatePct = other.optionalRebookRatePct().map(IntegerFilter::copy).orElse(null);
        this.languages = other.optionalLanguages().map(StringFilter::copy).orElse(null);
        this.deliveryModes = other.optionalDeliveryModes().map(StringFilter::copy).orElse(null);
        this.avatarGradientFrom = other.optionalAvatarGradientFrom().map(StringFilter::copy).orElse(null);
        this.avatarGradientTo = other.optionalAvatarGradientTo().map(StringFilter::copy).orElse(null);
        this.publishedAt = other.optionalPublishedAt().map(InstantFilter::copy).orElse(null);
        this.serviceId = other.optionalServiceId().map(LongFilter::copy).orElse(null);
        this.availabilityId = other.optionalAvailabilityId().map(LongFilter::copy).orElse(null);
        this.reviewId = other.optionalReviewId().map(LongFilter::copy).orElse(null);
        this.credentialId = other.optionalCredentialId().map(LongFilter::copy).orElse(null);
        this.highlightId = other.optionalHighlightId().map(LongFilter::copy).orElse(null);
        this.categoryId = other.optionalCategoryId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public ProfessionalCriteria copy() {
        return new ProfessionalCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getReference() {
        return reference;
    }

    public Optional<StringFilter> optionalReference() {
        return Optional.ofNullable(reference);
    }

    public StringFilter reference() {
        if (reference == null) {
            setReference(new StringFilter());
        }
        return reference;
    }

    public void setReference(StringFilter reference) {
        this.reference = reference;
    }

    public StringFilter getUserLogin() {
        return userLogin;
    }

    public Optional<StringFilter> optionalUserLogin() {
        return Optional.ofNullable(userLogin);
    }

    public StringFilter userLogin() {
        if (userLogin == null) {
            setUserLogin(new StringFilter());
        }
        return userLogin;
    }

    public void setUserLogin(StringFilter userLogin) {
        this.userLogin = userLogin;
    }

    public StringFilter getDisplayName() {
        return displayName;
    }

    public Optional<StringFilter> optionalDisplayName() {
        return Optional.ofNullable(displayName);
    }

    public StringFilter displayName() {
        if (displayName == null) {
            setDisplayName(new StringFilter());
        }
        return displayName;
    }

    public void setDisplayName(StringFilter displayName) {
        this.displayName = displayName;
    }

    public StringFilter getInitials() {
        return initials;
    }

    public Optional<StringFilter> optionalInitials() {
        return Optional.ofNullable(initials);
    }

    public StringFilter initials() {
        if (initials == null) {
            setInitials(new StringFilter());
        }
        return initials;
    }

    public void setInitials(StringFilter initials) {
        this.initials = initials;
    }

    public StringFilter getHeadline() {
        return headline;
    }

    public Optional<StringFilter> optionalHeadline() {
        return Optional.ofNullable(headline);
    }

    public StringFilter headline() {
        if (headline == null) {
            setHeadline(new StringFilter());
        }
        return headline;
    }

    public void setHeadline(StringFilter headline) {
        this.headline = headline;
    }

    public StringFilter getSpeciality() {
        return speciality;
    }

    public Optional<StringFilter> optionalSpeciality() {
        return Optional.ofNullable(speciality);
    }

    public StringFilter speciality() {
        if (speciality == null) {
            setSpeciality(new StringFilter());
        }
        return speciality;
    }

    public void setSpeciality(StringFilter speciality) {
        this.speciality = speciality;
    }

    public StringFilter getCity() {
        return city;
    }

    public Optional<StringFilter> optionalCity() {
        return Optional.ofNullable(city);
    }

    public StringFilter city() {
        if (city == null) {
            setCity(new StringFilter());
        }
        return city;
    }

    public void setCity(StringFilter city) {
        this.city = city;
    }

    public StringFilter getCountryCode() {
        return countryCode;
    }

    public Optional<StringFilter> optionalCountryCode() {
        return Optional.ofNullable(countryCode);
    }

    public StringFilter countryCode() {
        if (countryCode == null) {
            setCountryCode(new StringFilter());
        }
        return countryCode;
    }

    public void setCountryCode(StringFilter countryCode) {
        this.countryCode = countryCode;
    }

    public IntegerFilter getYearsPractising() {
        return yearsPractising;
    }

    public Optional<IntegerFilter> optionalYearsPractising() {
        return Optional.ofNullable(yearsPractising);
    }

    public IntegerFilter yearsPractising() {
        if (yearsPractising == null) {
            setYearsPractising(new IntegerFilter());
        }
        return yearsPractising;
    }

    public void setYearsPractising(IntegerFilter yearsPractising) {
        this.yearsPractising = yearsPractising;
    }

    public VerificationStateFilter getVerification() {
        return verification;
    }

    public Optional<VerificationStateFilter> optionalVerification() {
        return Optional.ofNullable(verification);
    }

    public VerificationStateFilter verification() {
        if (verification == null) {
            setVerification(new VerificationStateFilter());
        }
        return verification;
    }

    public void setVerification(VerificationStateFilter verification) {
        this.verification = verification;
    }

    public BooleanFilter getInsured() {
        return insured;
    }

    public Optional<BooleanFilter> optionalInsured() {
        return Optional.ofNullable(insured);
    }

    public BooleanFilter insured() {
        if (insured == null) {
            setInsured(new BooleanFilter());
        }
        return insured;
    }

    public void setInsured(BooleanFilter insured) {
        this.insured = insured;
    }

    public BooleanFilter getPoliceClearance() {
        return policeClearance;
    }

    public Optional<BooleanFilter> optionalPoliceClearance() {
        return Optional.ofNullable(policeClearance);
    }

    public BooleanFilter policeClearance() {
        if (policeClearance == null) {
            setPoliceClearance(new BooleanFilter());
        }
        return policeClearance;
    }

    public void setPoliceClearance(BooleanFilter policeClearance) {
        this.policeClearance = policeClearance;
    }

    public IntegerFilter getResponseMinutes() {
        return responseMinutes;
    }

    public Optional<IntegerFilter> optionalResponseMinutes() {
        return Optional.ofNullable(responseMinutes);
    }

    public IntegerFilter responseMinutes() {
        if (responseMinutes == null) {
            setResponseMinutes(new IntegerFilter());
        }
        return responseMinutes;
    }

    public void setResponseMinutes(IntegerFilter responseMinutes) {
        this.responseMinutes = responseMinutes;
    }

    public IntegerFilter getRebookRatePct() {
        return rebookRatePct;
    }

    public Optional<IntegerFilter> optionalRebookRatePct() {
        return Optional.ofNullable(rebookRatePct);
    }

    public IntegerFilter rebookRatePct() {
        if (rebookRatePct == null) {
            setRebookRatePct(new IntegerFilter());
        }
        return rebookRatePct;
    }

    public void setRebookRatePct(IntegerFilter rebookRatePct) {
        this.rebookRatePct = rebookRatePct;
    }

    public StringFilter getLanguages() {
        return languages;
    }

    public Optional<StringFilter> optionalLanguages() {
        return Optional.ofNullable(languages);
    }

    public StringFilter languages() {
        if (languages == null) {
            setLanguages(new StringFilter());
        }
        return languages;
    }

    public void setLanguages(StringFilter languages) {
        this.languages = languages;
    }

    public StringFilter getDeliveryModes() {
        return deliveryModes;
    }

    public Optional<StringFilter> optionalDeliveryModes() {
        return Optional.ofNullable(deliveryModes);
    }

    public StringFilter deliveryModes() {
        if (deliveryModes == null) {
            setDeliveryModes(new StringFilter());
        }
        return deliveryModes;
    }

    public void setDeliveryModes(StringFilter deliveryModes) {
        this.deliveryModes = deliveryModes;
    }

    public StringFilter getAvatarGradientFrom() {
        return avatarGradientFrom;
    }

    public Optional<StringFilter> optionalAvatarGradientFrom() {
        return Optional.ofNullable(avatarGradientFrom);
    }

    public StringFilter avatarGradientFrom() {
        if (avatarGradientFrom == null) {
            setAvatarGradientFrom(new StringFilter());
        }
        return avatarGradientFrom;
    }

    public void setAvatarGradientFrom(StringFilter avatarGradientFrom) {
        this.avatarGradientFrom = avatarGradientFrom;
    }

    public StringFilter getAvatarGradientTo() {
        return avatarGradientTo;
    }

    public Optional<StringFilter> optionalAvatarGradientTo() {
        return Optional.ofNullable(avatarGradientTo);
    }

    public StringFilter avatarGradientTo() {
        if (avatarGradientTo == null) {
            setAvatarGradientTo(new StringFilter());
        }
        return avatarGradientTo;
    }

    public void setAvatarGradientTo(StringFilter avatarGradientTo) {
        this.avatarGradientTo = avatarGradientTo;
    }

    public InstantFilter getPublishedAt() {
        return publishedAt;
    }

    public Optional<InstantFilter> optionalPublishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public InstantFilter publishedAt() {
        if (publishedAt == null) {
            setPublishedAt(new InstantFilter());
        }
        return publishedAt;
    }

    public void setPublishedAt(InstantFilter publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LongFilter getServiceId() {
        return serviceId;
    }

    public Optional<LongFilter> optionalServiceId() {
        return Optional.ofNullable(serviceId);
    }

    public LongFilter serviceId() {
        if (serviceId == null) {
            setServiceId(new LongFilter());
        }
        return serviceId;
    }

    public void setServiceId(LongFilter serviceId) {
        this.serviceId = serviceId;
    }

    public LongFilter getAvailabilityId() {
        return availabilityId;
    }

    public Optional<LongFilter> optionalAvailabilityId() {
        return Optional.ofNullable(availabilityId);
    }

    public LongFilter availabilityId() {
        if (availabilityId == null) {
            setAvailabilityId(new LongFilter());
        }
        return availabilityId;
    }

    public void setAvailabilityId(LongFilter availabilityId) {
        this.availabilityId = availabilityId;
    }

    public LongFilter getReviewId() {
        return reviewId;
    }

    public Optional<LongFilter> optionalReviewId() {
        return Optional.ofNullable(reviewId);
    }

    public LongFilter reviewId() {
        if (reviewId == null) {
            setReviewId(new LongFilter());
        }
        return reviewId;
    }

    public void setReviewId(LongFilter reviewId) {
        this.reviewId = reviewId;
    }

    public LongFilter getCredentialId() {
        return credentialId;
    }

    public Optional<LongFilter> optionalCredentialId() {
        return Optional.ofNullable(credentialId);
    }

    public LongFilter credentialId() {
        if (credentialId == null) {
            setCredentialId(new LongFilter());
        }
        return credentialId;
    }

    public void setCredentialId(LongFilter credentialId) {
        this.credentialId = credentialId;
    }

    public LongFilter getHighlightId() {
        return highlightId;
    }

    public Optional<LongFilter> optionalHighlightId() {
        return Optional.ofNullable(highlightId);
    }

    public LongFilter highlightId() {
        if (highlightId == null) {
            setHighlightId(new LongFilter());
        }
        return highlightId;
    }

    public void setHighlightId(LongFilter highlightId) {
        this.highlightId = highlightId;
    }

    public LongFilter getCategoryId() {
        return categoryId;
    }

    public Optional<LongFilter> optionalCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public LongFilter categoryId() {
        if (categoryId == null) {
            setCategoryId(new LongFilter());
        }
        return categoryId;
    }

    public void setCategoryId(LongFilter categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProfessionalCriteria that = (ProfessionalCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(reference, that.reference) &&
            Objects.equals(userLogin, that.userLogin) &&
            Objects.equals(displayName, that.displayName) &&
            Objects.equals(initials, that.initials) &&
            Objects.equals(headline, that.headline) &&
            Objects.equals(speciality, that.speciality) &&
            Objects.equals(city, that.city) &&
            Objects.equals(countryCode, that.countryCode) &&
            Objects.equals(yearsPractising, that.yearsPractising) &&
            Objects.equals(verification, that.verification) &&
            Objects.equals(insured, that.insured) &&
            Objects.equals(policeClearance, that.policeClearance) &&
            Objects.equals(responseMinutes, that.responseMinutes) &&
            Objects.equals(rebookRatePct, that.rebookRatePct) &&
            Objects.equals(languages, that.languages) &&
            Objects.equals(deliveryModes, that.deliveryModes) &&
            Objects.equals(avatarGradientFrom, that.avatarGradientFrom) &&
            Objects.equals(avatarGradientTo, that.avatarGradientTo) &&
            Objects.equals(publishedAt, that.publishedAt) &&
            Objects.equals(serviceId, that.serviceId) &&
            Objects.equals(availabilityId, that.availabilityId) &&
            Objects.equals(reviewId, that.reviewId) &&
            Objects.equals(credentialId, that.credentialId) &&
            Objects.equals(highlightId, that.highlightId) &&
            Objects.equals(categoryId, that.categoryId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            reference,
            userLogin,
            displayName,
            initials,
            headline,
            speciality,
            city,
            countryCode,
            yearsPractising,
            verification,
            insured,
            policeClearance,
            responseMinutes,
            rebookRatePct,
            languages,
            deliveryModes,
            avatarGradientFrom,
            avatarGradientTo,
            publishedAt,
            serviceId,
            availabilityId,
            reviewId,
            credentialId,
            highlightId,
            categoryId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProfessionalCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalReference().map(f -> "reference=" + f + ", ").orElse("") +
            optionalUserLogin().map(f -> "userLogin=" + f + ", ").orElse("") +
            optionalDisplayName().map(f -> "displayName=" + f + ", ").orElse("") +
            optionalInitials().map(f -> "initials=" + f + ", ").orElse("") +
            optionalHeadline().map(f -> "headline=" + f + ", ").orElse("") +
            optionalSpeciality().map(f -> "speciality=" + f + ", ").orElse("") +
            optionalCity().map(f -> "city=" + f + ", ").orElse("") +
            optionalCountryCode().map(f -> "countryCode=" + f + ", ").orElse("") +
            optionalYearsPractising().map(f -> "yearsPractising=" + f + ", ").orElse("") +
            optionalVerification().map(f -> "verification=" + f + ", ").orElse("") +
            optionalInsured().map(f -> "insured=" + f + ", ").orElse("") +
            optionalPoliceClearance().map(f -> "policeClearance=" + f + ", ").orElse("") +
            optionalResponseMinutes().map(f -> "responseMinutes=" + f + ", ").orElse("") +
            optionalRebookRatePct().map(f -> "rebookRatePct=" + f + ", ").orElse("") +
            optionalLanguages().map(f -> "languages=" + f + ", ").orElse("") +
            optionalDeliveryModes().map(f -> "deliveryModes=" + f + ", ").orElse("") +
            optionalAvatarGradientFrom().map(f -> "avatarGradientFrom=" + f + ", ").orElse("") +
            optionalAvatarGradientTo().map(f -> "avatarGradientTo=" + f + ", ").orElse("") +
            optionalPublishedAt().map(f -> "publishedAt=" + f + ", ").orElse("") +
            optionalServiceId().map(f -> "serviceId=" + f + ", ").orElse("") +
            optionalAvailabilityId().map(f -> "availabilityId=" + f + ", ").orElse("") +
            optionalReviewId().map(f -> "reviewId=" + f + ", ").orElse("") +
            optionalCredentialId().map(f -> "credentialId=" + f + ", ").orElse("") +
            optionalHighlightId().map(f -> "highlightId=" + f + ", ").orElse("") +
            optionalCategoryId().map(f -> "categoryId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
