package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.VerificationState;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Professional.
 */
@Entity
@Table(name = "professional")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Professional implements Serializable {

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
    @Column(name = "user_login", nullable = false, unique = true)
    private String userLogin;

    @NotNull
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Size(max = 4)
    @Column(name = "initials", length = 4)
    private String initials;

    @NotNull
    @Size(max = 160)
    @Column(name = "headline", length = 160, nullable = false)
    private String headline;

    @NotNull
    @Column(name = "speciality", nullable = false)
    private String speciality;

    @NotNull
    @Column(name = "city", nullable = false)
    private String city;

    @NotNull
    @Size(max = 2)
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Min(value = 0)
    @Column(name = "years_practising")
    private Integer yearsPractising;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verification", nullable = false)
    private VerificationState verification;

    @NotNull
    @Column(name = "insured", nullable = false)
    private Boolean insured;

    @NotNull
    @Column(name = "police_clearance", nullable = false)
    private Boolean policeClearance;

    @Column(name = "response_minutes")
    private Integer responseMinutes;

    @Min(value = 0)
    @Max(value = 100)
    @Column(name = "rebook_rate_pct")
    private Integer rebookRatePct;

    @Lob
    @Column(name = "bio")
    private String bio;

    @Column(name = "languages")
    private String languages;

    @Column(name = "delivery_modes")
    private String deliveryModes;

    @Size(max = 7)
    @Column(name = "avatar_gradient_from", length = 7)
    private String avatarGradientFrom;

    @Size(max = 7)
    @Column(name = "avatar_gradient_to", length = 7)
    private String avatarGradientTo;

    @Column(name = "published_at")
    private Instant publishedAt;

    @NotNull
    @Size(max = 64)
    @Column(name = "zone_id", length = 64, nullable = false)
    private String zoneId;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<ServiceOffering> services = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<AvailabilitySlot> availabilities = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<AvailabilityRule> rules = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<AvailabilityOverride> overrides = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<Review> reviews = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<Credential> credentials = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<Highlight> highlights = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professional")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "professional" }, allowSetters = true)
    private Set<VerificationReview> verificationReviews = new HashSet<>();

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "professionals" }, allowSetters = true)
    private Category category;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Professional id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public Professional reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getUserLogin() {
        return this.userLogin;
    }

    public Professional userLogin(String userLogin) {
        this.setUserLogin(userLogin);
        return this;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Professional displayName(String displayName) {
        this.setDisplayName(displayName);
        return this;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getInitials() {
        return this.initials;
    }

    public Professional initials(String initials) {
        this.setInitials(initials);
        return this;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getHeadline() {
        return this.headline;
    }

    public Professional headline(String headline) {
        this.setHeadline(headline);
        return this;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSpeciality() {
        return this.speciality;
    }

    public Professional speciality(String speciality) {
        this.setSpeciality(speciality);
        return this;
    }

    public void setSpeciality(String speciality) {
        this.speciality = speciality;
    }

    public String getCity() {
        return this.city;
    }

    public Professional city(String city) {
        this.setCity(city);
        return this;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public Professional countryCode(String countryCode) {
        this.setCountryCode(countryCode);
        return this;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public Integer getYearsPractising() {
        return this.yearsPractising;
    }

    public Professional yearsPractising(Integer yearsPractising) {
        this.setYearsPractising(yearsPractising);
        return this;
    }

    public void setYearsPractising(Integer yearsPractising) {
        this.yearsPractising = yearsPractising;
    }

    public VerificationState getVerification() {
        return this.verification;
    }

    public Professional verification(VerificationState verification) {
        this.setVerification(verification);
        return this;
    }

    public void setVerification(VerificationState verification) {
        this.verification = verification;
    }

    public Boolean getInsured() {
        return this.insured;
    }

    public Professional insured(Boolean insured) {
        this.setInsured(insured);
        return this;
    }

    public void setInsured(Boolean insured) {
        this.insured = insured;
    }

    public Boolean getPoliceClearance() {
        return this.policeClearance;
    }

    public Professional policeClearance(Boolean policeClearance) {
        this.setPoliceClearance(policeClearance);
        return this;
    }

    public void setPoliceClearance(Boolean policeClearance) {
        this.policeClearance = policeClearance;
    }

    public Integer getResponseMinutes() {
        return this.responseMinutes;
    }

    public Professional responseMinutes(Integer responseMinutes) {
        this.setResponseMinutes(responseMinutes);
        return this;
    }

    public void setResponseMinutes(Integer responseMinutes) {
        this.responseMinutes = responseMinutes;
    }

    public Integer getRebookRatePct() {
        return this.rebookRatePct;
    }

    public Professional rebookRatePct(Integer rebookRatePct) {
        this.setRebookRatePct(rebookRatePct);
        return this;
    }

    public void setRebookRatePct(Integer rebookRatePct) {
        this.rebookRatePct = rebookRatePct;
    }

    public String getBio() {
        return this.bio;
    }

    public Professional bio(String bio) {
        this.setBio(bio);
        return this;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getLanguages() {
        return this.languages;
    }

    public Professional languages(String languages) {
        this.setLanguages(languages);
        return this;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public String getDeliveryModes() {
        return this.deliveryModes;
    }

    public Professional deliveryModes(String deliveryModes) {
        this.setDeliveryModes(deliveryModes);
        return this;
    }

    public void setDeliveryModes(String deliveryModes) {
        this.deliveryModes = deliveryModes;
    }

    public String getAvatarGradientFrom() {
        return this.avatarGradientFrom;
    }

    public Professional avatarGradientFrom(String avatarGradientFrom) {
        this.setAvatarGradientFrom(avatarGradientFrom);
        return this;
    }

    public void setAvatarGradientFrom(String avatarGradientFrom) {
        this.avatarGradientFrom = avatarGradientFrom;
    }

    public String getAvatarGradientTo() {
        return this.avatarGradientTo;
    }

    public Professional avatarGradientTo(String avatarGradientTo) {
        this.setAvatarGradientTo(avatarGradientTo);
        return this;
    }

    public void setAvatarGradientTo(String avatarGradientTo) {
        this.avatarGradientTo = avatarGradientTo;
    }

    public Instant getPublishedAt() {
        return this.publishedAt;
    }

    public Professional publishedAt(Instant publishedAt) {
        this.setPublishedAt(publishedAt);
        return this;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getZoneId() {
        return this.zoneId;
    }

    public Professional zoneId(String zoneId) {
        this.setZoneId(zoneId);
        return this;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public Set<ServiceOffering> getServices() {
        return this.services;
    }

    public void setServices(Set<ServiceOffering> serviceOfferings) {
        if (this.services != null) {
            this.services.forEach(i -> i.setProfessional(null));
        }
        if (serviceOfferings != null) {
            serviceOfferings.forEach(i -> i.setProfessional(this));
        }
        this.services = serviceOfferings;
    }

    public Professional services(Set<ServiceOffering> serviceOfferings) {
        this.setServices(serviceOfferings);
        return this;
    }

    public Professional addService(ServiceOffering serviceOffering) {
        this.services.add(serviceOffering);
        serviceOffering.setProfessional(this);
        return this;
    }

    public Professional removeService(ServiceOffering serviceOffering) {
        this.services.remove(serviceOffering);
        serviceOffering.setProfessional(null);
        return this;
    }

    public Set<AvailabilitySlot> getAvailabilities() {
        return this.availabilities;
    }

    public void setAvailabilities(Set<AvailabilitySlot> availabilitySlots) {
        if (this.availabilities != null) {
            this.availabilities.forEach(i -> i.setProfessional(null));
        }
        if (availabilitySlots != null) {
            availabilitySlots.forEach(i -> i.setProfessional(this));
        }
        this.availabilities = availabilitySlots;
    }

    public Professional availabilities(Set<AvailabilitySlot> availabilitySlots) {
        this.setAvailabilities(availabilitySlots);
        return this;
    }

    public Professional addAvailability(AvailabilitySlot availabilitySlot) {
        this.availabilities.add(availabilitySlot);
        availabilitySlot.setProfessional(this);
        return this;
    }

    public Professional removeAvailability(AvailabilitySlot availabilitySlot) {
        this.availabilities.remove(availabilitySlot);
        availabilitySlot.setProfessional(null);
        return this;
    }

    public Set<AvailabilityRule> getRules() {
        return this.rules;
    }

    public void setRules(Set<AvailabilityRule> availabilityRules) {
        if (this.rules != null) {
            this.rules.forEach(i -> i.setProfessional(null));
        }
        if (availabilityRules != null) {
            availabilityRules.forEach(i -> i.setProfessional(this));
        }
        this.rules = availabilityRules;
    }

    public Professional rules(Set<AvailabilityRule> availabilityRules) {
        this.setRules(availabilityRules);
        return this;
    }

    public Professional addRule(AvailabilityRule availabilityRule) {
        this.rules.add(availabilityRule);
        availabilityRule.setProfessional(this);
        return this;
    }

    public Professional removeRule(AvailabilityRule availabilityRule) {
        this.rules.remove(availabilityRule);
        availabilityRule.setProfessional(null);
        return this;
    }

    public Set<AvailabilityOverride> getOverrides() {
        return this.overrides;
    }

    public void setOverrides(Set<AvailabilityOverride> availabilityOverrides) {
        if (this.overrides != null) {
            this.overrides.forEach(i -> i.setProfessional(null));
        }
        if (availabilityOverrides != null) {
            availabilityOverrides.forEach(i -> i.setProfessional(this));
        }
        this.overrides = availabilityOverrides;
    }

    public Professional overrides(Set<AvailabilityOverride> availabilityOverrides) {
        this.setOverrides(availabilityOverrides);
        return this;
    }

    public Professional addOverride(AvailabilityOverride availabilityOverride) {
        this.overrides.add(availabilityOverride);
        availabilityOverride.setProfessional(this);
        return this;
    }

    public Professional removeOverride(AvailabilityOverride availabilityOverride) {
        this.overrides.remove(availabilityOverride);
        availabilityOverride.setProfessional(null);
        return this;
    }

    public Set<Review> getReviews() {
        return this.reviews;
    }

    public void setReviews(Set<Review> reviews) {
        if (this.reviews != null) {
            this.reviews.forEach(i -> i.setProfessional(null));
        }
        if (reviews != null) {
            reviews.forEach(i -> i.setProfessional(this));
        }
        this.reviews = reviews;
    }

    public Professional reviews(Set<Review> reviews) {
        this.setReviews(reviews);
        return this;
    }

    public Professional addReview(Review review) {
        this.reviews.add(review);
        review.setProfessional(this);
        return this;
    }

    public Professional removeReview(Review review) {
        this.reviews.remove(review);
        review.setProfessional(null);
        return this;
    }

    public Set<Credential> getCredentials() {
        return this.credentials;
    }

    public void setCredentials(Set<Credential> credentials) {
        if (this.credentials != null) {
            this.credentials.forEach(i -> i.setProfessional(null));
        }
        if (credentials != null) {
            credentials.forEach(i -> i.setProfessional(this));
        }
        this.credentials = credentials;
    }

    public Professional credentials(Set<Credential> credentials) {
        this.setCredentials(credentials);
        return this;
    }

    public Professional addCredential(Credential credential) {
        this.credentials.add(credential);
        credential.setProfessional(this);
        return this;
    }

    public Professional removeCredential(Credential credential) {
        this.credentials.remove(credential);
        credential.setProfessional(null);
        return this;
    }

    public Set<Highlight> getHighlights() {
        return this.highlights;
    }

    public void setHighlights(Set<Highlight> highlights) {
        if (this.highlights != null) {
            this.highlights.forEach(i -> i.setProfessional(null));
        }
        if (highlights != null) {
            highlights.forEach(i -> i.setProfessional(this));
        }
        this.highlights = highlights;
    }

    public Professional highlights(Set<Highlight> highlights) {
        this.setHighlights(highlights);
        return this;
    }

    public Professional addHighlight(Highlight highlight) {
        this.highlights.add(highlight);
        highlight.setProfessional(this);
        return this;
    }

    public Professional removeHighlight(Highlight highlight) {
        this.highlights.remove(highlight);
        highlight.setProfessional(null);
        return this;
    }

    public Set<VerificationReview> getVerificationReviews() {
        return this.verificationReviews;
    }

    public void setVerificationReviews(Set<VerificationReview> verificationReviews) {
        if (this.verificationReviews != null) {
            this.verificationReviews.forEach(i -> i.setProfessional(null));
        }
        if (verificationReviews != null) {
            verificationReviews.forEach(i -> i.setProfessional(this));
        }
        this.verificationReviews = verificationReviews;
    }

    public Professional verificationReviews(Set<VerificationReview> verificationReviews) {
        this.setVerificationReviews(verificationReviews);
        return this;
    }

    public Professional addVerificationReview(VerificationReview verificationReview) {
        this.verificationReviews.add(verificationReview);
        verificationReview.setProfessional(this);
        return this;
    }

    public Professional removeVerificationReview(VerificationReview verificationReview) {
        this.verificationReviews.remove(verificationReview);
        verificationReview.setProfessional(null);
        return this;
    }

    public Category getCategory() {
        return this.category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Professional category(Category category) {
        this.setCategory(category);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Professional)) {
            return false;
        }
        return getId() != null && getId().equals(((Professional) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Professional{" +
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
            ", zoneId='" + getZoneId() + "'" +
            "}";
    }
}
