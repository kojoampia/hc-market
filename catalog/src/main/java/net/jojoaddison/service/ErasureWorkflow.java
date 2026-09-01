package net.jojoaddison.service;

import java.util.List;
import net.jojoaddison.domain.Favourite;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.FavouriteQueryRepository;
import net.jojoaddison.repository.ReviewEraseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a customer from the catalog service — {@code decisions.md} D24/D31.
 *
 * <h2>The review text stays, and that is a deliberate line</h2>
 *
 * <p>{@code authorName} and {@code authorInitials} are redacted and {@code customerLogin} becomes the
 * pseudonym, but <strong>the review body is left alone</strong>. A review is public speech about a
 * professional's service, relied on by other customers and already answered in public by the
 * professional; erasing the person is not the same as retracting what they said, and D24 asks for
 * erasure of personal data rather than for a right to withdraw a published opinion.
 *
 * <p>This is a judgement, not a certainty, and it is the one thing here most likely to need revisiting
 * once counsel has an opinion. If the answer comes back that the text must go too, this is the method
 * to change — and the rating stays correct either way, because it is derived from the rows rather
 * than stored.
 *
 * <p>Favourites are deleted outright rather than pseudonymised. A saved list is purely personal: it
 * says nothing about the professional, nothing aggregates over it, and a tombstoned row would be an
 * orphan nobody can act on. Nothing else in the estate references it.
 *
 * <h2>The alias</h2>
 *
 * <p>{@link SubjectPseudonym}, identical in booking and messaging, so one person carries one alias
 * estate-wide. It is an HMAC keyed by a per-estate pepper rather than a bare digest — D34 recorded
 * that a bare digest over a short, guessable login is re-identifiable by anyone holding a database
 * dump, and D35 closed it. Without the pepper this refuses rather than writing something weaker.
 */
@Service
public class ErasureWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureWorkflow.class);

    static final String REDACTED_NAME = "[erased]";
    static final String REDACTED_INITIALS = "··";

    private final ReviewEraseRepository reviews;
    private final FavouriteQueryRepository favourites;
    private final SubjectPseudonym pseudonyms;

    public ErasureWorkflow(ReviewEraseRepository reviews, FavouriteQueryRepository favourites, SubjectPseudonym pseudonyms) {
        this.reviews = reviews;
        this.favourites = favourites;
        this.pseudonyms = pseudonyms;
    }

    /**
     * {@code erased-<16 hex>} — identical rule to booking's and messaging's, so one person carries one
     * alias estate-wide. An instance method since D35, because the pepper is configuration.
     */
    public String pseudonym(String login) {
        return pseudonyms.of(login);
    }

    /** @return how many reviews were de-identified */
    @Transactional
    public int eraseCustomer(String login) {
        String alias = pseudonym(login);

        List<Review> mine = reviews.findByCustomerLogin(login);
        for (Review r : mine) {
            r.setCustomerLogin(alias);
            r.setAuthorName(REDACTED_NAME);
            r.setAuthorInitials(REDACTED_INITIALS);
        }
        reviews.saveAll(mine);

        List<Favourite> saved = favourites.findByCustomerLoginOrderByAddedAtDesc(login);
        favourites.deleteAll(saved);

        LOG.info("de-identified {} review(s) and removed {} favourite(s), now {}", mine.size(), saved.size(), alias);
        return mine.size();
    }
}
