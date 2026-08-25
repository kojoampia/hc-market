package net.jojoaddison.service.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.Credential;
import net.jojoaddison.domain.Highlight;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.domain.enumeration.VerificationState;
import net.jojoaddison.repository.AvailabilitySlotRepository;
import net.jojoaddison.repository.CategoryRepository;
import net.jojoaddison.domain.Favourite;
import net.jojoaddison.repository.CredentialRepository;
import net.jojoaddison.repository.FavouriteQueryRepository;
import net.jojoaddison.repository.HighlightRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ReviewRepository;
import net.jojoaddison.repository.ServiceOfferingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the catalog's slice of {@code demo/seed-data.json}: categories, professionals, their
 * services, availability and reviews.
 *
 * <p>Ratings are not loaded, because ratings are not stored. They appear the moment reviews exist,
 * through the {@code professional_rating} view.
 */
@Service
public class CatalogSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogSeeder.class);

    private final CategoryRepository categoryRepository;
    private final ProfessionalRepository professionalRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ReviewRepository reviewRepository;
    private final CredentialRepository credentialRepository;
    private final HighlightRepository highlightRepository;
    private final FavouriteQueryRepository favouriteRepository;

    public CatalogSeeder(
        CategoryRepository categoryRepository,
        ProfessionalRepository professionalRepository,
        ServiceOfferingRepository serviceOfferingRepository,
        AvailabilitySlotRepository availabilitySlotRepository,
        ReviewRepository reviewRepository,
        CredentialRepository credentialRepository,
        HighlightRepository highlightRepository,
        FavouriteQueryRepository favouriteRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.professionalRepository = professionalRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.reviewRepository = reviewRepository;
        this.credentialRepository = credentialRepository;
        this.highlightRepository = highlightRepository;
        this.favouriteRepository = favouriteRepository;
    }

    /** Idempotent: the loader is safe to run on every restart. */
    public boolean alreadySeeded() {
        return professionalRepository.count() > 0;
    }

    /**
     * Wipes the catalog tables in dependency order. Used by {@code POST /management/healthconnect/reseed},
     * which exists only under {@code test & dev}.
     */
    @Transactional
    public void clear() {
        favouriteRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        availabilitySlotRepository.deleteAllInBatch();
        serviceOfferingRepository.deleteAllInBatch();
        credentialRepository.deleteAllInBatch();
        highlightRepository.deleteAllInBatch();
        professionalRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    @Transactional
    public void load(SeedFile seed, boolean anchorDates) {
        long shiftDays = anchorDates ? 0 : ChronoUnit.DAYS.between(seed.meta().demoToday(), LocalDate.now());
        if (shiftDays != 0) {
            LOG.info("shifting every seed date by {} days (anchor is {})", shiftDays, seed.meta().demoToday());
        }

        Map<String, Category> categoriesByCode = new HashMap<>();
        for (SeedFile.SeedCategory c : seed.categories()) {
            Category category = new Category().code(c.code()).name(c.name()).blurb(c.blurb()).icon(c.icon()).sortOrder(c.sortOrder());
            categoriesByCode.put(c.code(), categoryRepository.save(category));
        }

        Map<String, Professional> professionalsByRef = new HashMap<>();
        for (SeedFile.SeedProfessional p : seed.professionals()) {
            Category category = categoriesByCode.get(p.categoryCode());
            if (category == null) {
                throw new IllegalStateException("seed professional " + p.ref() + " names unknown category " + p.categoryCode());
            }
            Professional professional = new Professional()
                .reference(p.ref())
                .userLogin(p.userLogin())
                .displayName(p.displayName())
                .initials(p.initials())
                .headline(p.headline())
                .speciality(p.speciality())
                .city(p.city())
                .countryCode(p.countryCode())
                .yearsPractising(p.yearsPractising())
                .verification(VerificationState.valueOf(p.verification()))
                .insured(p.insured())
                .policeClearance(p.policeClearance())
                .responseMinutes(p.responseMinutes())
                .rebookRatePct(p.rebookRatePct())
                .bio(p.bio())
                .languages(join(p.languages()))
                .deliveryModes(join(p.deliveryModes()))
                .avatarGradientFrom(p.avatarGradientFrom())
                .avatarGradientTo(p.avatarGradientTo())
                .category(category);
            professional = professionalRepository.save(professional);
            professionalsByRef.put(p.ref(), professional);

            int order = 1;
            for (String label : orEmpty(p.credentials())) {
                credentialRepository.save(new Credential().label(label).sortOrder(order++).professional(professional));
            }
            order = 1;
            for (String label : orEmpty(p.highlights())) {
                highlightRepository.save(new Highlight().label(label).sortOrder(order++).professional(professional));
            }
            for (SeedFile.SeedService s : orEmpty(p.services())) {
                serviceOfferingRepository.save(
                    new ServiceOffering()
                        .reference(s.ref())
                        .name(s.name())
                        .durationMinutes(s.durationMinutes())
                        .priceMinor(s.priceMinor())
                        .currency(s.currency())
                        .description(s.description())
                        .active(s.active())
                        .sortOrder(s.sortOrder())
                        .professional(professional)
                );
            }
            for (SeedFile.SeedAvailability day : orEmpty(p.availability())) {
                for (String time : orEmpty(day.slots())) {
                    availabilitySlotRepository.save(
                        new AvailabilitySlot().slotDate(day.date().plusDays(shiftDays)).slotTime(time).taken(false).professional(professional)
                    );
                }
            }
        }

        for (SeedFile.SeedReview r : seed.reviews()) {
            Professional professional = professionalsByRef.get(r.professionalRef());
            if (professional == null) {
                throw new IllegalStateException("seed review " + r.ref() + " names unknown professional " + r.professionalRef());
            }
            reviewRepository.save(
                new Review()
                    .reference(r.ref())
                    .customerLogin(r.customerLogin())
                    .authorName(r.authorName())
                    .authorInitials(r.authorInitials())
                    .stars(r.stars())
                    .publishedOn(r.publishedOn().plusDays(shiftDays))
                    .body(r.body())
                    .professionalReply(r.professionalReply())
                    .bookingReference(r.bookingReference())
                    .professional(professional)
            );
        }

        // The prototype's FAVOURITES array belongs to its single fully-described customer, so the
        // seeded Saved list is attached to whoever that is rather than to a hard-coded login.
        String savedBy = seed.customers() == null || seed.customers().isEmpty() ? null : seed.customers().get(0).userLogin();
        if (savedBy != null) {
            for (String ref : orEmpty(seed.favourites())) {
                if (professionalsByRef.containsKey(ref)) {
                    favouriteRepository.save(new Favourite().customerLogin(savedBy).professionalRef(ref).addedAt(Instant.now()));
                }
            }
        }

        LOG.info(
            "seeded {} categories, {} professionals, {} reviews",
            seed.categories().size(),
            seed.professionals().size(),
            seed.reviews().size()
        );
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
