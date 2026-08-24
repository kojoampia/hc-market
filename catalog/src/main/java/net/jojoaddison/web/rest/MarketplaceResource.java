package net.jojoaddison.web.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.service.MarketplaceService;
import net.jojoaddison.service.MarketplaceService.BrowseFilter;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.AvailabilityDay;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.CategoryView;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.Facets;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.Page;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ProfessionalCard;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ProfessionalDetail;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ReviewView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public marketplace API — spec §6, "Public / customer".
 *
 * <h2>Hand-owned, not generated</h2>
 *
 * <p>This class deliberately replaces the JDL-generated {@code ProfessionalResource},
 * {@code CategoryResource} and {@code ReviewResource}, which were deleted. They cannot coexist:
 * the generated resources map {@code GET /api/professionals/{id}} on a numeric id, while spec §6
 * requires {@code GET /api/professionals/{ref}} on the business reference ("p1"). Two controllers
 * claiming the same path template is an ambiguous mapping and Spring refuses to start.
 *
 * <p><strong>Regeneration hazard.</strong> Running {@code jhipster jdl jdl/catalog.jdl --force}
 * will recreate those three generated resources, and the application will then fail to start with
 * an ambiguous-mapping error naming this class. That failure is loud and the fix is to delete them
 * again — but it is a real trap, so it is written down here rather than discovered.
 *
 * <p>Every endpoint is a public read: no token required, per spec §6.
 */
@RestController
public class MarketplaceResource {

    private final MarketplaceService marketplace;

    public MarketplaceResource(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    /** Discover — the four category tiles, with a live count and the specialities in use. */
    @GetMapping("/api/categories")
    public List<CategoryView> categories() {
        return marketplace.listCategories();
    }

    /**
     * Browse. Paging is applied after filtering and sorting, so the totals in the response describe
     * the filtered set rather than the whole catalogue.
     */
    @GetMapping("/api/professionals")
    public Page<ProfessionalCard> professionals(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String speciality,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String city,
        @RequestParam(required = false) Long maxPriceMinor,
        @RequestParam(required = false) BigDecimal minRating,
        @RequestParam(defaultValue = "false") boolean verifiedOnly,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "recommended") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "6") int size
    ) {
        List<ProfessionalCard> all = marketplace.browse(new BrowseFilter(category, speciality, mode, city, maxPriceMinor, minRating, verifiedOnly, q, sort));
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new Page<>(all.subList(from, to), page, size, all.size(), totalPages);
    }

    /** Browse — the live counts beside each filter, computed against the other active filters. */
    @GetMapping("/api/professionals/facets")
    public Facets facets(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String speciality,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String city,
        @RequestParam(required = false) Long maxPriceMinor,
        @RequestParam(required = false) BigDecimal minRating,
        @RequestParam(defaultValue = "false") boolean verifiedOnly,
        @RequestParam(required = false) String q
    ) {
        return marketplace.facets(new BrowseFilter(category, speciality, mode, city, maxPriceMinor, minRating, verifiedOnly, q, null));
    }

    /**
     * Smoke test and seed verification. {@code deploy-dev.sh} compares this against the row count
     * in the seed file and fails the run on a mismatch.
     *
     * <p>Returns a bare number rather than JSON because that is what the script's
     * {@code [[ "$got" == "$expect" ]]} comparison reads.
     */
    @GetMapping("/api/professionals/count")
    public long professionalCount() {
        return marketplace.countProfessionals();
    }

    /** Seed verification, as above. */
    @GetMapping("/api/reviews/count")
    public long reviewCount() {
        return marketplace.countReviews();
    }

    /** Profile — with services, derived rating and the star distribution. */
    @GetMapping("/api/professionals/{ref}")
    public ResponseEntity<ProfessionalDetail> professional(@PathVariable String ref) {
        return ResponseEntity.of(marketplace.findProfessional(ref));
    }

    /**
     * Profile and booking wizard step 2. Defaults to the prototype's ten-day strip when no window
     * is given.
     */
    @GetMapping("/api/professionals/{ref}/availability")
    public ResponseEntity<List<AvailabilityDay>> availability(
        @PathVariable String ref,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!marketplace.exists(ref)) {
            return ResponseEntity.notFound().build();
        }
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(10) : to;
        return ResponseEntity.ok(marketplace.availability(ref, start, end));
    }

    /** Profile — paginated reviews, newest first. */
    @GetMapping("/api/professionals/{ref}/reviews")
    public ResponseEntity<Page<ReviewView>> reviews(
        @PathVariable String ref,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "4") int size
    ) {
        if (!marketplace.exists(ref)) {
            return ResponseEntity.notFound().build();
        }
        org.springframework.data.domain.Page<ReviewView> found = marketplace.reviews(ref, page, size);
        return ResponseEntity.ok(new Page<>(found.getContent(), page, size, found.getTotalElements(), found.getTotalPages()));
    }
}
