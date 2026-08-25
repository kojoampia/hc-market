package net.jojoaddison.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * The shape of {@code demo/seed-data.json}, as far as the catalog service is concerned.
 *
 * <p>Each service reads <strong>only its own top-level sections</strong>, which is what lets the
 * same file load into five services without collision. {@code @JsonIgnoreProperties(ignoreUnknown)}
 * is therefore load-bearing rather than defensive: {@code bookings}, {@code sessions},
 * {@code threads} and the rest belong to other services and must be ignored here, not rejected.
 *
 * <p>Note what is <em>not</em> in this file: there is no rating anywhere. Ratings are derived from
 * {@code reviews} at read time by the {@code professional_rating} view, so the seed cannot ship an
 * inconsistency. See {@code deploy/demo/extract-seed.mjs}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedFile(
    @JsonProperty("$meta") Meta meta,
    List<SeedCategory> categories,
    List<SeedProfessional> professionals,
    List<SeedReview> reviews,
    /** Professional refs saved by the one customer the prototype describes in full. */
    List<String> favourites,
    List<SeedCustomer> customers
) {
    /** Only the login is needed here — the rest of the customer belongs to other services. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedCustomer(String ref, String userLogin) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, LocalDate demoToday, String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedCategory(String code, String name, String blurb, String icon, Integer sortOrder, List<String> specialities) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedProfessional(
        String ref,
        String userLogin,
        String displayName,
        String initials,
        String headline,
        String categoryCode,
        String speciality,
        String city,
        String countryCode,
        List<String> deliveryModes,
        Integer yearsPractising,
        String verification,
        Boolean insured,
        Boolean policeClearance,
        List<String> languages,
        Integer responseMinutes,
        Integer rebookRatePct,
        List<String> credentials,
        List<String> highlights,
        String bio,
        String avatarGradientFrom,
        String avatarGradientTo,
        List<SeedService> services,
        List<SeedAvailability> availability
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedService(
        String ref,
        String name,
        Integer durationMinutes,
        Long priceMinor,
        String currency,
        String description,
        Boolean active,
        Integer sortOrder
    ) {}

    /** One day, with the free slot times on it. The prototype seeds 21 days per professional. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedAvailability(LocalDate date, List<String> slots) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedReview(
        String ref,
        String professionalRef,
        String customerLogin,
        String authorName,
        String authorInitials,
        Integer stars,
        LocalDate publishedOn,
        String body,
        String professionalReply,
        String bookingReference
    ) {}
}
