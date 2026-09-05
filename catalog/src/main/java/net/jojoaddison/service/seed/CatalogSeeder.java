package net.jojoaddison.service.seed;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jojoaddison.service.SlotTime;
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

    /**
     * The zone every seeded professional is in — {@code decisions.md} D21.
     *
     * <p>Ghana is UTC+0 all year with no daylight saving, so this was correct while it was implicit.
     * What it was not is legible: nothing in the schema said which zone a 07:00 availability slot's
     * 07:00 belonged to. The value has not changed; only whether it is written down.
     */
    static final String DEFAULT_ZONE_ID = "Africa/Accra";

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
        // decisions.md D48. Not LocalDate.now(): the calendar is the estate's, and the four seeded
        // services have to arrive at the same number or their dates stop lining up with each other.
        long shiftDays = SeedCalendar.shiftDays(seed.meta().demoToday(), anchorDates);
        if (shiftDays != 0) {
            // The day is DERIVED from the shift rather than read from the clock a second time: two
            // reads either side of Accra midnight would print a date the seed was not loaded against,
            // in the one log line whose job is to explain a disagreement between services.
            LOG.info(
                "shifting every seed date by {} days: {} -> {} in {}",
                shiftDays,
                seed.meta().demoToday(),
                seed.meta().demoToday().plusDays(shiftDays),
                SeedCalendar.SEED_ZONE
            );
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
                // decisions.md D21. Not in the seed file and deliberately not added to it: the seed
                // is REGENERATED from the prototype and asserts its figures, and the prototype has
                // no zone in it — every professional there is in Accra, implicitly, which is the
                // assumption D21 exists to make explicit rather than to encode twice.
                //
                // So the default lands here, where it is one line and visibly a default, instead of
                // in eighteen extracted records where it would look like data somebody chose.
                .zoneId(DEFAULT_ZONE_ID)
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
                        new AvailabilitySlot().slotDate(day.date().plusDays(shiftDays)).slotTime(SlotTime.parse(time)).taken(false).professional(professional)
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
