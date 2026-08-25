package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jojoaddison.service.SlotTime;
import java.time.LocalTime;
import net.jojoaddison.domain.AvailabilityOverride;
import net.jojoaddison.domain.AvailabilityRule;
import net.jojoaddison.domain.enumeration.Weekday;
import net.jojoaddison.service.AvailabilityPlanner;
import net.jojoaddison.service.dto.marketplace.ProDtos.GeneratedView;
import net.jojoaddison.service.dto.marketplace.ProDtos.OverrideView;
import net.jojoaddison.service.dto.marketplace.ProDtos.RuleView;
import net.jojoaddison.service.dto.marketplace.ProDtos.SaveOverride;
import net.jojoaddison.service.dto.marketplace.ProDtos.SaveRule;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Credential;
import net.jojoaddison.domain.Highlight;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Review;
import net.jojoaddison.domain.ServiceOffering;
import net.jojoaddison.repository.AvailabilitySlotRepository;
import net.jojoaddison.repository.CredentialRepository;
import net.jojoaddison.repository.HighlightRepository;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ReviewRepository;
import net.jojoaddison.repository.ServiceOfferingRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.MarketplaceService;
import net.jojoaddison.service.dto.marketplace.ProDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * The professional's own listing — spec §6, "Professional workspace": the services editor, the
 * listing editor, working hours, and reviews with public replies.
 *
 * <h2>Ownership</h2>
 *
 * <p>No endpoint here takes a professional reference. The caller is resolved from the JWT and every
 * query is <strong>scoped by {@code userLogin} in the query itself</strong> rather than fetched and
 * then checked — so "refuse any reference that is not the caller's" is a property of the SQL, not of
 * a conditional someone can forget to write. A reference that is not yours simply is not found.
 */
@RestController
@RequestMapping("/api/pro")
@Transactional
public class ProWorkspaceResource {

    private final MarketplaceQueryRepository marketplace;
    private final ServiceOfferingRepository services;
    private final AvailabilitySlotRepository slots;
    private final ReviewRepository reviews;
    private final CredentialRepository credentials;
    private final HighlightRepository highlights;
    private final MarketplaceService marketplaceService;
    private final AvailabilityPlanner planner;

    public ProWorkspaceResource(
        MarketplaceQueryRepository marketplace,
        ServiceOfferingRepository services,
        AvailabilitySlotRepository slots,
        ReviewRepository reviews,
        CredentialRepository credentials,
        HighlightRepository highlights,
        MarketplaceService marketplaceService,
        AvailabilityPlanner planner
    ) {
        this.marketplace = marketplace;
        this.services = services;
        this.slots = slots;
        this.reviews = reviews;
        this.credentials = credentials;
        this.highlights = highlights;
        this.marketplaceService = marketplaceService;
        this.planner = planner;
    }

    // ------------------------------------------------------------------- services --

    /** The editor, which shows hidden services too — otherwise the publish toggle is one-way. */
    @GetMapping("/services")
    @Transactional(readOnly = true)
    public List<OwnedService> listServices() {
        return marketplace.findAllServicesForOwner(me()).stream().map(ProWorkspaceResource::toOwned).toList();
    }

    @PostMapping("/services")
    public ResponseEntity<OwnedService> addService(@Valid @RequestBody SaveService body) {
        Professional owner = meOrThrow();
        ServiceOffering s = new ServiceOffering()
            .reference("s-" + UUID.randomUUID().toString().substring(0, 8))
            .name(body.name())
            .durationMinutes(body.durationMinutes())
            .priceMinor(body.priceMinor())
            .currency(body.currency() == null ? "GHS" : body.currency())
            .description(body.description())
            // A new service starts HIDDEN. Publishing is a decision, and a half-finished listing
            // appearing in Browse the moment it is saved is not one the professional made.
            .active(false)
            .sortOrder(body.sortOrder())
            .professional(owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOwned(services.save(s)));
    }

    @PutMapping("/services/{ref}")
    public OwnedService updateService(@PathVariable String ref, @Valid @RequestBody SaveService body) {
        ServiceOffering s = ownedService(ref);
        s.name(body.name())
            .durationMinutes(body.durationMinutes())
            .priceMinor(body.priceMinor())
            .description(body.description())
            .sortOrder(body.sortOrder());
        if (body.currency() != null) {
            s.currency(body.currency());
        }
        // Editing a price does NOT touch any booking already made against it: Booking carries its
        // own priceMinor and serviceName precisely so a receipt cannot change under the customer.
        return toOwned(services.save(s));
    }

    @PostMapping("/services/{ref}/publish")
    public OwnedService publish(@PathVariable String ref) {
        return toOwned(services.save(ownedService(ref).active(true)));
    }

    @PostMapping("/services/{ref}/hide")
    public OwnedService hide(@PathVariable String ref) {
        // Hiding removes it from Browse. Bookings already taken against it stand — they are the
        // customer's, not the listing's.
        return toOwned(services.save(ownedService(ref).active(false)));
    }

    // -------------------------------------------------------------------- profile --

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public OwnedProfile profile() {
        Professional p = meOrThrow();
        return new OwnedProfile(
            p.getReference(),
            p.getDisplayName(),
            p.getHeadline(),
            p.getSpeciality(),
            p.getCity(),
            p.getCountryCode(),
            p.getYearsPractising(),
            p.getBio(),
            split(p.getLanguages()),
            split(p.getDeliveryModes()),
            p.getResponseMinutes(),
            labels(p.getCredentials().stream().sorted(bySortOrder(Credential::getSortOrder)).map(Credential::getLabel).toList()),
            labels(p.getHighlights().stream().sorted(bySortOrder(Highlight::getSortOrder)).map(Highlight::getLabel).toList()),
            p.getVerification() == null ? null : p.getVerification().name(),
            Boolean.TRUE.equals(p.getInsured()),
            Boolean.TRUE.equals(p.getPoliceClearance())
        );
    }

    /**
     * Saves the listing.
     *
     * <p>{@code verification}, {@code insured} and {@code policeClearance} are deliberately absent
     * from {@link SaveProfile}: a professional who can set their own verified flag is a trust chain
     * with a hole in it. They move only through the admin queue — spec §13 #3, still open.
     */
    @PutMapping("/profile")
    public OwnedProfile saveProfile(@Valid @RequestBody SaveProfile body) {
        Professional p = meOrThrow();
        if (body.displayName() != null) p.displayName(body.displayName());
        if (body.headline() != null) p.headline(body.headline());
        if (body.speciality() != null) p.speciality(body.speciality());
        if (body.city() != null) p.city(body.city());
        if (body.yearsPractising() != null) p.yearsPractising(body.yearsPractising());
        if (body.bio() != null) p.bio(body.bio());
        if (body.responseMinutes() != null) p.responseMinutes(body.responseMinutes());
        if (body.languages() != null) p.languages(join(body.languages()));
        if (body.deliveryModes() != null) p.deliveryModes(join(body.deliveryModes()));
        marketplace.save(p);

        if (body.credentials() != null) {
            credentials.deleteAll(p.getCredentials());
            p.getCredentials().clear();
            int i = 1;
            for (String label : body.credentials()) {
                credentials.save(new Credential().label(label).sortOrder(i++).professional(p));
            }
        }
        if (body.highlights() != null) {
            highlights.deleteAll(p.getHighlights());
            p.getHighlights().clear();
            int i = 1;
            for (String label : body.highlights()) {
                highlights.save(new Highlight().label(label).sortOrder(i++).professional(p));
            }
        }
        return profile();
    }

    /** The verification checklist — read-only, and every field derived from what exists. */
    @GetMapping("/profile/verification")
    @Transactional(readOnly = true)
    public VerificationChecklist verification() {
        Professional p = meOrThrow();
        return new VerificationChecklist(
            p.getVerification() == null ? null : p.getVerification().name(),
            Boolean.TRUE.equals(p.getInsured()),
            Boolean.TRUE.equals(p.getPoliceClearance()),
            !p.getCredentials().isEmpty(),
            marketplace.findAllServicesForOwner(me()).stream().anyMatch(s -> Boolean.TRUE.equals(s.getActive()))
        );
    }

    // --------------------------------------------------------------- availability --

    @GetMapping("/availability")
    @Transactional(readOnly = true)
    public List<WorkingDay> availability(@RequestParam(required = false) LocalDate from) {
        LocalDate start = from == null ? LocalDate.now() : from;
        Map<LocalDate, List<Slot>> byDay = new LinkedHashMap<>();
        for (AvailabilitySlot s : marketplace.findOwnedSlots(me(), start)) {
            byDay.computeIfAbsent(s.getSlotDate(), d -> new ArrayList<>()).add(new Slot(SlotTime.format(s.getSlotTime()), Boolean.TRUE.equals(s.getTaken())));
        }
        return byDay.entrySet().stream().map(e -> new WorkingDay(e.getKey(), e.getValue())).toList();
    }

    /**
     * Replaces one day's working hours.
     *
     * <p>A day is set whole rather than patched slot by slot, because that is how a professional
     * thinks about it. <strong>Slots already taken are never removed</strong> — a booked appointment
     * is a commitment to a customer, and quietly deleting it by editing working hours would cancel
     * someone's session without telling them. Removing a booked slot is a cancellation, and
     * cancellations go through the booking service where they raise an event.
     */
    @PutMapping("/availability")
    public List<WorkingDay> setAvailability(@Valid @RequestBody SetWorkingDay body) {
        Professional owner = meOrThrow();
        List<AvailabilitySlot> existing = marketplace
            .findOwnedSlots(me(), body.date())
            .stream()
            .filter(s -> s.getSlotDate().equals(body.date()))
            .toList();

        List<String> keep = existing.stream().filter(s -> Boolean.TRUE.equals(s.getTaken())).map(s -> SlotTime.format(s.getSlotTime())).toList();
        existing.stream().filter(s -> !Boolean.TRUE.equals(s.getTaken())).forEach(slots::delete);

        for (String time : body.slots()) {
            if (keep.contains(time)) {
                continue; // already there, and taken — leave it exactly as it is
            }
            slots.save(new AvailabilitySlot().slotDate(body.date()).slotTime(SlotTime.parse(time)).taken(false).professional(owner));
        }
        return availability(body.date());
    }

    // --------------------------------------------------------- availability rules --
    //
    // decisions.md D20. These endpoints exist HERE, on /api/pro/**, and not as the generated
    // AvailabilityRuleResource and AvailabilityOverrideResource, which were deleted: their unscoped
    // CRUD on /api/availability-rules would have let any authenticated user edit anyone's working
    // hours. Like every other /api/pro/** endpoint, none of these takes a professional parameter --
    // the owner comes from the JWT subject and nowhere else.

    @GetMapping("/availability/rules")
    @Transactional(readOnly = true)
    public List<RuleView> listRules() {
        return planner.rulesOf(meOrThrow()).stream().map(ProWorkspaceResource::toView).toList();
    }

    @PostMapping("/availability/rules")
    public ResponseEntity<RuleView> addRule(@Valid @RequestBody SaveRule body) {
        Professional owner = meOrThrow();
        AvailabilityRule rule = apply(new AvailabilityRule(), body).professional(owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(planner.saveRule(rule)));
    }

    @PutMapping("/availability/rules/{id}")
    public RuleView updateRule(@PathVariable Long id, @Valid @RequestBody SaveRule body) {
        return toView(planner.saveRule(apply(ownedRule(id), body)));
    }

    @DeleteMapping("/availability/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        // Deleting a rule stops FUTURE generation. Slots it already materialised stay until a
        // generation run over a window that no longer justifies them, and taken ones stay
        // regardless -- see AvailabilityPlanner. Deleting a rule is not a way to cancel bookings.
        planner.deleteRule(ownedRule(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability/overrides")
    @Transactional(readOnly = true)
    public List<OverrideView> listOverrides() {
        return planner.overridesOf(meOrThrow()).stream().map(ProWorkspaceResource::toView).toList();
    }

    /** One override per date, so saving the same date twice edits it rather than stacking. */
    @PutMapping("/availability/overrides")
    public OverrideView saveOverride(@Valid @RequestBody SaveOverride body) {
        Professional owner = meOrThrow();
        AvailabilityOverride override = planner.overrideOn(owner, body.date()).orElseGet(AvailabilityOverride::new).professional(owner);
        override
            .overrideDate(body.date())
            .closed(Boolean.TRUE.equals(body.closed()))
            .startTime(body.startTime() == null ? null : SlotTime.parse(body.startTime()))
            .endTime(body.endTime() == null ? null : SlotTime.parse(body.endTime()))
            .note(body.note());
        return toView(planner.saveOverride(override));
    }

    @DeleteMapping("/availability/overrides/{date}")
    public ResponseEntity<Void> deleteOverride(@PathVariable LocalDate date) {
        planner.overrideOn(meOrThrow(), date).ifPresent(planner::deleteOverride);
        return ResponseEntity.noContent().build();
    }

    /**
     * Materialises the rules into bookable slots.
     *
     * <p>Explicit rather than scheduled: there is no scheduler in this estate, and inventing one
     * here would be a second thing to operate. A professional generating their own calendar also
     * gets to see what changed, which {@link GeneratedView} reports.
     */
    @PostMapping("/availability/generate")
    public GeneratedView generate(@RequestParam(required = false) Integer weeks, @RequestParam(required = false) LocalDate from) {
        Professional owner = meOrThrow();
        LocalDate start = from == null ? LocalDate.now() : from;
        int horizon = weeks == null ? planner.defaultHorizonWeeks() : weeks;
        if (horizon < 1 || horizon > 52) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weeks must be between 1 and 52");
        }
        var result = planner.generate(owner, start, start.plusWeeks(horizon).minusDays(1));
        return new GeneratedView(
            result.from(),
            result.to(),
            result.created(),
            result.removed(),
            result.daysClosed(),
            result.keptBecauseTaken()
        );
    }

    private AvailabilityRule ownedRule(Long id) {
        String login = me();
        return planner
            .rulesOf(meOrThrow())
            .stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            // 404 rather than 403, the same as everywhere else here: a rule that is not yours is
            // indistinguishable from one that does not exist, and saying which is a disclosure.
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such availability rule for " + login));
    }

    private static AvailabilityRule apply(AvailabilityRule rule, SaveRule body) {
        LocalTime start = SlotTime.parse(body.startTime());
        LocalTime end = SlotTime.parse(body.endTime());
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a rule must start before it ends: %s to %s".formatted(start, end));
        }
        if (body.validUntil() != null && body.validUntil().isBefore(body.validFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validUntil is before validFrom");
        }
        Weekday weekday;
        try {
            weekday = Weekday.valueOf(body.weekday().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException notADay) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'%s' is not a weekday".formatted(body.weekday()));
        }
        return rule
            .weekday(weekday)
            .startTime(start)
            .endTime(end)
            .slotMinutes(body.slotMinutes())
            .validFrom(body.validFrom())
            .validUntil(body.validUntil())
            .active(body.active() == null || body.active());
    }

    private static RuleView toView(AvailabilityRule r) {
        return new RuleView(
            r.getId(),
            r.getWeekday() == null ? null : r.getWeekday().name(),
            SlotTime.format(r.getStartTime()),
            SlotTime.format(r.getEndTime()),
            r.getSlotMinutes(),
            r.getValidFrom(),
            r.getValidUntil(),
            Boolean.TRUE.equals(r.getActive())
        );
    }

    private static OverrideView toView(AvailabilityOverride o) {
        return new OverrideView(
            o.getId(),
            o.getOverrideDate(),
            Boolean.TRUE.equals(o.getClosed()),
            SlotTime.format(o.getStartTime()),
            SlotTime.format(o.getEndTime()),
            o.getNote()
        );
    }

    // -------------------------------------------------------------------- reviews --

    @GetMapping("/reviews")
    @Transactional(readOnly = true)
    public ReviewSummary reviews() {
        Professional p = meOrThrow();
        List<Review> all = marketplace.findReviewsForOwner(me());
        List<OwnedReview> views = all
            .stream()
            .map(r ->
                new OwnedReview(
                    r.getReference(),
                    r.getAuthorName(),
                    r.getAuthorInitials(),
                    r.getStars(),
                    r.getPublishedOn(),
                    r.getBody(),
                    r.getProfessionalReply(),
                    r.getProfessionalReply() == null
                )
            )
            .toList();
        return new ReviewSummary(
            views,
            marketplaceService.starDistribution(p.getReference()),
            all.size(),
            views.stream().filter(v -> v.professionalReply() != null).count()
        );
    }

    /**
     * A public reply — the only response to a review that exists anywhere in this system.
     *
     * <p>There is no endpoint to delete or edit a review, for anyone including admins, and replying
     * is deliberately <strong>once</strong>: a reply that can be rewritten after the fact is not the
     * public record it appears to be. Spec §9.
     */
    @PostMapping("/reviews/{ref}/reply")
    public OwnedReview reply(@PathVariable String ref, @Valid @RequestBody PublishReply body) {
        Review r = marketplace
            .findOwnedReview(ref, me())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such review"));
        if (r.getProfessionalReply() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "review " + ref + " already has a reply");
        }
        r.professionalReply(body.body());
        reviews.save(r);
        return new OwnedReview(
            r.getReference(),
            r.getAuthorName(),
            r.getAuthorInitials(),
            r.getStars(),
            r.getPublishedOn(),
            r.getBody(),
            r.getProfessionalReply(),
            false
        );
    }

    // ------------------------------------------------------------------- helpers --

    private String me() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated professional"));
    }

    /** 404 rather than 403: a login with no listing is indistinguishable from one that is not yours. */
    private Professional meOrThrow() {
        return marketplace
            .findByUserLogin(me())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no professional listing for this account"));
    }

    private ServiceOffering ownedService(String ref) {
        return marketplace
            .findOwnedService(ref, me())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such service"));
    }

    private static OwnedService toOwned(ServiceOffering s) {
        return new OwnedService(
            s.getReference(),
            s.getName(),
            s.getDurationMinutes(),
            s.getPriceMinor() == null ? 0L : s.getPriceMinor(),
            s.getCurrency(),
            s.getDescription(),
            Boolean.TRUE.equals(s.getActive()),
            s.getSortOrder()
        );
    }

    private static <T> Comparator<T> bySortOrder(java.util.function.Function<T, Integer> f) {
        return Comparator.comparing(f, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static List<String> labels(List<String> in) {
        return in == null ? List.of() : in;
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }
}
