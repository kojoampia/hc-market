package net.jojoaddison.service.dto.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The public read contract of the catalog service, shaped by spec §6 — which in turn is shaped by
 * what a prototype screen needs. Records, per spec §3.
 *
 * <p>Two rules hold throughout:
 *
 * <ul>
 *   <li><strong>Money is minor units.</strong> {@code priceMinor} is pesewas; 28000 is ₵280.00. The
 *       12% brokerage fee is inside that number, not added to it.
 *   <li><strong>Ratings are nullable.</strong> A professional with no reviews has no rating — not a
 *       zero. The {@code professional_rating} view has no row for them, and a 0.0 would read as
 *       "rated badly" rather than "not yet rated".
 * </ul>
 */
public final class MarketplaceDtos {

    private MarketplaceDtos() {}

    /**
     * A category tile on Discover. {@code professionalCount} and {@code specialities} are both
     * derived — counted and DISTINCT-ed over the professionals in the category, never stored.
     */
    public record CategoryView(
        String code,
        String name,
        String blurb,
        String icon,
        Integer sortOrder,
        long professionalCount,
        List<String> specialities
    ) {}

    /** A card on Browse or Discover. Deliberately smaller than {@link ProfessionalDetail}. */
    public record ProfessionalCard(
        String ref,
        String displayName,
        String initials,
        String headline,
        String speciality,
        String categoryCode,
        String city,
        List<String> deliveryModes,
        String verification,
        boolean insured,
        boolean policeClearance,
        Integer yearsPractising,
        Integer responseMinutes,
        Integer rebookRatePct,
        List<String> languages,
        String avatarGradientFrom,
        String avatarGradientTo,
        BigDecimal rating,
        long reviewCount,
        Long fromPriceMinor,
        String currency,
        /**
         * The IANA zone the professional's wall clock belongs to — {@code decisions.md} D21.
         *
         * <p>On the CARD rather than only the detail, because Browse shows availability times too
         * and a client that has to fetch the full profile to know what "07:00" means will simply
         * assume. It is a display fact, not a private one: nothing here is disclosed that the city
         * on the same card does not already imply.
         */
        String zoneId
    ) {}

    /** The profile screen: everything on the card, plus the things only that screen shows. */
    public record ProfessionalDetail(
        ProfessionalCard card,
        String bio,
        String countryCode,
        List<String> credentials,
        List<String> highlights,
        List<ServiceView> services,
        List<Integer> starDistribution,
        /**
         * When this professional was last verified, or null if there is no record — decisions.md
         * D16/D31.
         *
         * <p>The DATE ONLY. The reviewer's login and the evidence reference stay on the desk
         * endpoint: a customer needs to know that a person checked and when, not which member of
         * staff it was or what case number they filed. Widening this to carry either would turn an
         * audit trail into a disclosure.
         *
         * <p>Null for every seeded professional, because the prototype it is extracted from has no
         * review history to extract. That is the honest answer and the screen says so rather than
         * inventing a date.
         */
        Instant verifiedOn
    ) {}

    public record ServiceView(
        String ref,
        String name,
        Integer durationMinutes,
        long priceMinor,
        String currency,
        String description,
        boolean active,
        Integer sortOrder
    ) {}

    /** One day of the profile's "next 10 days" strip and of booking wizard step 2. */
    public record AvailabilityDay(LocalDate date, List<String> slots) {}

    public record ReviewView(
        String ref,
        String authorName,
        String authorInitials,
        int stars,
        LocalDate publishedOn,
        String body,
        String professionalReply
    ) {}

    /** One page of anything, in the shape the prototype's pagers expect. */
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    /**
     * The live counts beside each Browse filter. Every list is computed against the *other* active
     * filters, which is what makes the counts honest as the user narrows down.
     */
    public record Facets(
        List<Facet> categories,
        List<Facet> specialities,
        List<Facet> cities,
        List<Facet> deliveryModes,
        long total,
        Long minPriceMinor,
        Long maxPriceMinor
    ) {}

    public record Facet(String value, String label, long count) {}
}
