package net.jojoaddison.web.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Favourite;
import net.jojoaddison.repository.FavouriteQueryRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.MarketplaceService;
import net.jojoaddison.service.dto.marketplace.MarketplaceDtos.ProfessionalCard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Saved list — spec §6, {@code GET/POST/DELETE /api/favourites}.
 *
 * <p>Replaces the generated {@code FavouriteResource}, which offered unscoped CRUD over every
 * customer's saved list on {@code /api/favourites}. That is both the wrong path semantics and a
 * disclosure: an authenticated customer could have read the whole table.
 *
 * <h2>Saving twice</h2>
 *
 * <p>A duplicate save returns the existing row rather than an error. Saving something already saved
 * is not a mistake the customer made — the heart was already filled in and they clicked it again, or
 * two tabs raced. The unique constraint on {@code (customer_login, professional_ref)} is what makes
 * this safe under a race: the second insert fails at the database and is caught here, rather than
 * being prevented by a look-then-write that has a window between the two.
 */
@RestController
@RequestMapping("/api/favourites")
@Transactional
public class FavouritesResource {

    private final FavouriteQueryRepository favourites;
    private final MarketplaceService marketplace;

    public FavouritesResource(FavouriteQueryRepository favourites, MarketplaceService marketplace) {
        this.favourites = favourites;
        this.marketplace = marketplace;
    }

    public record SaveFavourite(String professionalRef) {}

    /**
     * The Saved screen, newest first.
     *
     * <p>Returns the full professional card rather than bare references, because that is what the
     * screen renders and the alternative is N round trips. A saved professional who has since been
     * removed from the catalogue is reported with {@code professional: null} rather than dropped —
     * the row is the customer's, and silently losing it would be worse than showing that it is gone.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return favourites
            .findByCustomerLoginOrderByAddedAtDesc(me())
            .stream()
            .map(f -> {
                ProfessionalCard card = marketplace.findProfessional(f.getProfessionalRef()).map(d -> d.card()).orElse(null);
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("professionalRef", f.getProfessionalRef());
                row.put("addedAt", f.getAddedAt());
                row.put("professional", card);
                return row;
            })
            .toList();
    }

    /**
     * {@code NOT_SUPPORTED} so each repository call runs in its own transaction.
     *
     * <p>Sharing one is what made the first two attempts at this wrong. Inside a transaction a
     * constraint violation marks it rollback-only, so catching the exception and returning 204 still
     * fails — at commit, after the handler has returned, as an
     * {@code UnexpectedRollbackException} that reads as a 500 with no obvious cause. Flushing early
     * moves *when* it throws but not that the transaction is already poisoned.
     *
     * <p>Outside one, the ordinary duplicate never reaches the database at all (the check below
     * catches it), and a genuine race throws in isolation and is caught here.
     */
    @PostMapping
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<Void> add(@RequestBody SaveFavourite body) {
        if (body == null || body.professionalRef() == null || body.professionalRef().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a professionalRef is required");
        }
        String login = me();
        if (!marketplace.exists(body.professionalRef())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such professional");
        }
        if (favourites.findByCustomerLoginAndProfessionalRef(login, body.professionalRef()).isPresent()) {
            return ResponseEntity.noContent().build();
        }
        try {
            favourites.save(new Favourite().customerLogin(login).professionalRef(body.professionalRef()).addedAt(Instant.now()));
        } catch (DataIntegrityViolationException raced) {
            // Two tabs, same instant. The unique constraint is the backstop the check above cannot
            // be: saving twice is still the same as saving once.
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Unsaving. Idempotent: removing something that is not there returns 204 rather than 404,
     * because the caller's intent — "this should not be in my list" — is satisfied either way.
     */
    @DeleteMapping("/{professionalRef}")
    public ResponseEntity<Void> remove(@PathVariable String professionalRef) {
        favourites.findByCustomerLoginAndProfessionalRef(me(), professionalRef).ifPresent(favourites::delete);
        return ResponseEntity.noContent().build();
    }

    private String me() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated customer"));
    }
}
