package net.jojoaddison.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.VerificationState;
import net.jojoaddison.domain.VerificationReview;
import net.jojoaddison.domain.ProfessionalRating;
import net.jojoaddison.repository.CategoryRepository;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.VerificationReviewQueryRepository;
import net.jojoaddison.repository.ProfessionalRatingRepository;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.AvailabilityDay;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.CategoryView;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.Facet;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.Facets;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ProfessionalCard;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ProfessionalDetail;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ReviewView;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ServiceView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public read side of the catalog.
 *
 * <p>Every figure this class returns is computed at read time from the rows that justify it. There
 * is no cached rating, no stored review count and no denormalised "from" price — the prototype
 * derived all three at render time and could not drift, and this preserves that property across the
 * persistence boundary.
 */
@Service
@Transactional(readOnly = true)
public class MarketplaceService {

    private final MarketplaceQueryRepository marketplace;
    private final CategoryRepository categories;
    private final ProfessionalRatingRepository ratings;
    private final VerificationReviewQueryRepository reviewQueries;

    public MarketplaceService(
        MarketplaceQueryRepository marketplace,
        CategoryRepository categories,
        ProfessionalRatingRepository ratings,
        VerificationReviewQueryRepository reviewQueries
    ) {
        this.marketplace = marketplace;
        this.categories = categories;
        this.ratings = ratings;
        this.reviewQueries = reviewQueries;
    }

    // ------------------------------------------------------------------ categories --

    public List<CategoryView> listCategories() {
        return categories
            .findAll()
            .stream()
            .sorted(Comparator.comparing(Category::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(c ->
                new CategoryView(
                    c.getCode(),
                    c.getName(),
                    c.getBlurb(),
                    c.getIcon(),
                    c.getSortOrder(),
                    marketplace.countByCategoryCode(c.getCode()),
                    marketplace.findSpecialitiesByCategoryCode(c.getCode())
                )
            )
            .toList();
    }

    // --------------------------------------------------------------- professionals --

    public long countProfessionals() {
        return marketplace.count();
    }

    public long countReviews() {
        return marketplace.countAllReviews();
    }

    /**
     * Browse. Filtering is done in memory over the full professional set, which is correct for a
     * catalogue of this size and honest about it: with 18 professionals — and comfortably at 500 —
     * the query cost is dominated by the round trip, not the scan. Spec §13 open question #6 is
     * where the threshold for a real search backend gets decided.
     */
    public List<ProfessionalCard> browse(BrowseFilter filter) {
        List<Professional> all = marketplace.findAll();
        List<ProfessionalCard> cards = withRatings(all).stream().filter(filter::matches).collect(Collectors.toCollection(ArrayList::new));
        cards.sort(filter.comparator());
        return cards;
    }

    public Facets facets(BrowseFilter filter) {
        List<ProfessionalCard> matching = browse(filter);
        Map<String, String> categoryNames = categories.findAll().stream().collect(Collectors.toMap(Category::getCode, Category::getName, (a, b) -> a));

        List<Long> prices = matching.stream().map(ProfessionalCard::fromPriceMinor).filter(java.util.Objects::nonNull).sorted().toList();

        return new Facets(
            tally(matching, ProfessionalCard::categoryCode, code -> categoryNames.getOrDefault(code, code)),
            tally(matching, ProfessionalCard::speciality, Function.identity()),
            tally(matching, ProfessionalCard::city, Function.identity()),
            tallyMulti(matching),
            matching.size(),
            prices.isEmpty() ? null : prices.get(0),
            prices.isEmpty() ? null : prices.get(prices.size() - 1)
        );
    }

    public Optional<ProfessionalDetail> findProfessional(String reference) {
        return marketplace
            .findByReference(reference)
            .map(p -> {
                ProfessionalCard card = toCard(p, ratingOf(p));
                List<ServiceView> services = marketplace
                    .findActiveServices(reference)
                    .stream()
                    .map(s ->
                        new ServiceView(
                            s.getReference(),
                            s.getName(),
                            s.getDurationMinutes(),
                            s.getPriceMinor(),
                            s.getCurrency(),
                            s.getDescription(),
                            Boolean.TRUE.equals(s.getActive()),
                            s.getSortOrder()
                        )
                    )
                    .toList();
                return new ProfessionalDetail(
                    card,
                    p.getBio(),
                    p.getCountryCode(),
                    p.getCredentials().stream().sorted(Comparator.comparing(c -> nullSafe(c.getSortOrder()))).map(c -> c.getLabel()).toList(),
                    p.getHighlights().stream().sorted(Comparator.comparing(h -> nullSafe(h.getSortOrder()))).map(h -> h.getLabel()).toList(),
                    services,
                    starDistribution(reference),
                    verifiedOn(reference)
                );
            });
    }

    /** Five buckets, one star to five, zero-filled — the profile screen draws a bar for each. */
    public List<Integer> starDistribution(String reference) {
        int[] buckets = new int[5];
        for (Object[] row : marketplace.countReviewsByStars(reference)) {
            int stars = ((Number) row[0]).intValue();
            if (stars >= 1 && stars <= 5) {
                buckets[stars - 1] = ((Number) row[1]).intValue();
            }
        }
        return Arrays.stream(buckets).boxed().toList();
    }

    public boolean exists(String reference) {
        return marketplace.findByReference(reference).isPresent();
    }

    /**
     * When this professional was last VERIFIED, or empty if nothing records it — {@code decisions.md}
     * D16/D31.
     *
     * <p>Only a {@code VERIFIED} decision counts. Taking the latest review of any kind would date a
     * badge from the review that removed it, which is worse than saying nothing.
     *
     * <p>Empty for every seeded professional: the seed is extracted from a prototype that has no
     * review history, so there is genuinely nothing to report and the profile says as much. The
     * reviewer and the evidence reference are deliberately not returned — see the DTO.
     */
    public Instant verifiedOn(String reference) {
        return reviewQueries
            .findByProfessionalReferenceOrderByReviewedAtDesc(reference)
            .stream()
            .filter(r -> r.getDecision() == VerificationState.VERIFIED)
            .map(VerificationReview::getReviewedAt)
            .findFirst()
            .orElse(null);
    }

    /**
     * The login that owns a professional reference — {@code decisions.md} D28.
     *
     * <p><strong>Never put this on a public DTO.</strong> {@code ProfessionalCard} and
     * {@code ProfessionalDetail} deliberately carry no login, and that is what made booking unable
     * to verify the {@code professionalLogin} its clients sent it. The answer was a second,
     * unroutable endpoint rather than a new field on the profile — see
     * {@link net.jojoaddison.web.rest.InternalProfessionalResource}.
     *
     * <p>Empty for an unknown reference, and empty for a blank login. The column is
     * {@code required unique}, so a null cannot be persisted — but {@code @NotNull} says nothing
     * about the empty string, which is why this filters on blankness rather than on presence.
     * Callers cannot act differently on the two: both mean the reference cannot be attributed to
     * anybody.
     */
    public Optional<String> loginOf(String reference) {
        return marketplace.findByReference(reference).map(Professional::getUserLogin).filter(login -> !login.isBlank());
    }

    // ---------------------------------------------------------------- availability --

    public List<AvailabilityDay> availability(String reference, LocalDate from, LocalDate to) {
        Map<LocalDate, List<String>> byDate = new LinkedHashMap<>();
        marketplace.findFreeSlots(reference, from, to).forEach(slot -> byDate.computeIfAbsent(slot.getSlotDate(), d -> new ArrayList<>()).add(SlotTime.format(slot.getSlotTime())));
        return byDate.entrySet().stream().map(e -> new AvailabilityDay(e.getKey(), e.getValue())).toList();
    }

    // --------------------------------------------------------------------- reviews --

    public org.springframework.data.domain.Page<ReviewView> reviews(String reference, int page, int size) {
        return marketplace
            .findReviews(reference, PageRequest.of(page, size))
            .map(r ->
                new ReviewView(
                    r.getReference(),
                    r.getAuthorName(),
                    r.getAuthorInitials(),
                    r.getStars(),
                    r.getPublishedOn(),
                    r.getBody(),
                    r.getProfessionalReply()
                )
            );
    }

    // --------------------------------------------------------------------- helpers --

    /** One batched view read for a whole page of cards, rather than one query per card. */
    private List<ProfessionalCard> withRatings(List<Professional> professionals) {
        Map<Long, ProfessionalRating> byId = ratings
            .findByProfessionalIdIn(professionals.stream().map(Professional::getId).toList())
            .stream()
            .collect(Collectors.toMap(ProfessionalRating::getProfessionalId, Function.identity()));
        return professionals.stream().map(p -> toCard(p, Optional.ofNullable(byId.get(p.getId())))).toList();
    }

    private Optional<ProfessionalRating> ratingOf(Professional p) {
        return ratings.findById(p.getId());
    }

    private ProfessionalCard toCard(Professional p, Optional<ProfessionalRating> rating) {
        Long fromPrice = marketplace.findFromPriceMinor(p.getReference());
        return new ProfessionalCard(
            p.getReference(),
            p.getDisplayName(),
            p.getInitials(),
            p.getHeadline(),
            p.getSpeciality(),
            p.getCategory() == null ? null : p.getCategory().getCode(),
            p.getCity(),
            split(p.getDeliveryModes()),
            p.getVerification() == null ? null : p.getVerification().name(),
            Boolean.TRUE.equals(p.getInsured()),
            Boolean.TRUE.equals(p.getPoliceClearance()),
            p.getYearsPractising(),
            p.getResponseMinutes(),
            p.getRebookRatePct(),
            split(p.getLanguages()),
            p.getAvatarGradientFrom(),
            p.getAvatarGradientTo(),
            // null, not zero: no reviews means unrated, which is not the same as rated badly.
            rating.map(ProfessionalRating::getRating).orElse(null),
            rating.map(ProfessionalRating::getReviewCount).orElse(0L),
            fromPrice,
            "GHS",
            p.getZoneId()
        );
    }

    private static List<String> split(String commaSeparated) {
        return commaSeparated == null || commaSeparated.isBlank() ? List.of() : Arrays.stream(commaSeparated.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static int nullSafe(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static List<Facet> tally(List<ProfessionalCard> cards, Function<ProfessionalCard, String> key, Function<String, String> label) {
        Map<String, Long> counts = new LinkedHashMap<>();
        cards.stream().map(key).filter(java.util.Objects::nonNull).sorted().forEach(v -> counts.merge(v, 1L, Long::sum));
        return counts.entrySet().stream().map(e -> new Facet(e.getKey(), label.apply(e.getKey()), e.getValue())).toList();
    }

    /** Delivery mode is many-per-professional, so it is tallied across the list, not by key. */
    private static List<Facet> tallyMulti(List<ProfessionalCard> cards) {
        Map<String, Long> counts = new LinkedHashMap<>();
        cards.stream().flatMap(c -> c.deliveryModes().stream()).sorted().forEach(v -> counts.merge(v, 1L, Long::sum));
        return counts.entrySet().stream().map(e -> new Facet(e.getKey(), e.getKey(), e.getValue())).toList();
    }

    /**
     * The Browse filter set from spec §6, with the prototype's six sort orders.
     *
     * <p>{@code minRating} deliberately excludes unrated professionals rather than treating them as
     * 0.0 — asking for "4 stars and up" should not surface someone with no reviews at all.
     */
    public record BrowseFilter(
        String category,
        String speciality,
        String mode,
        String city,
        Long maxPriceMinor,
        BigDecimal minRating,
        boolean verifiedOnly,
        String q,
        String sort
    ) {
        public boolean matches(ProfessionalCard c) {
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase(c.categoryCode())) return false;
            if (speciality != null && !speciality.isBlank() && !speciality.equalsIgnoreCase(c.speciality())) return false;
            if (city != null && !city.isBlank() && !city.equalsIgnoreCase(c.city())) return false;
            if (mode != null && !mode.isBlank() && c.deliveryModes().stream().noneMatch(m -> m.equalsIgnoreCase(mode))) return false;
            if (maxPriceMinor != null && (c.fromPriceMinor() == null || c.fromPriceMinor() > maxPriceMinor)) return false;
            if (minRating != null && (c.rating() == null || c.rating().compareTo(minRating) < 0)) return false;
            if (verifiedOnly && !"VERIFIED".equals(c.verification())) return false;
            if (q != null && !q.isBlank()) {
                String needle = q.toLowerCase();
                boolean hit =
                    contains(c.displayName(), needle) ||
                    contains(c.headline(), needle) ||
                    contains(c.speciality(), needle) ||
                    contains(c.city(), needle);
                if (!hit) return false;
            }
            return true;
        }

        private static boolean contains(String haystack, String needle) {
            return haystack != null && haystack.toLowerCase().contains(needle);
        }

        public Comparator<ProfessionalCard> comparator() {
            Comparator<ProfessionalCard> byRating = Comparator.comparing(
                ProfessionalCard::rating,
                Comparator.nullsLast(Comparator.reverseOrder())
            );
            return switch (sort == null ? "recommended" : sort) {
                case "rating" -> byRating;
                case "reviews" -> Comparator.comparingLong(ProfessionalCard::reviewCount).reversed();
                case "price-asc" -> Comparator.comparing(ProfessionalCard::fromPriceMinor, Comparator.nullsLast(Comparator.naturalOrder()));
                case "price-desc" -> Comparator.comparing(ProfessionalCard::fromPriceMinor, Comparator.nullsLast(Comparator.reverseOrder()));
                case "experience" -> Comparator.comparing(ProfessionalCard::yearsPractising, Comparator.nullsLast(Comparator.reverseOrder()));
                case "response" -> Comparator.comparing(ProfessionalCard::responseMinutes, Comparator.nullsLast(Comparator.naturalOrder()));
                // "recommended" is rating first, then volume — the prototype's default order.
                default -> byRating.thenComparing(Comparator.comparingLong(ProfessionalCard::reviewCount).reversed());
            };
        }
    }
}
