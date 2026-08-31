package net.jojoaddison.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.jojoaddison.domain.Favourite;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.FavouriteQueryRepository;
import net.jojoaddison.repository.ReviewRepository;
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
 */
@Service
public class ErasureWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureWorkflow.class);

    static final String REDACTED_NAME = "[erased]";
    static final String REDACTED_INITIALS = "··";

    private final ReviewRepository reviews;
    private final FavouriteQueryRepository favourites;

    public ErasureWorkflow(ReviewRepository reviews, FavouriteQueryRepository favourites) {
        this.reviews = reviews;
        this.favourites = favourites;
    }

    /** Identical rule to booking's and messaging's, so one person carries one alias estate-wide. */
    public static String pseudonym(String login) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(login.getBytes(StandardCharsets.UTF_8));
            return "erased-" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable, which should not be possible", e);
        }
    }

    /** @return how many reviews were de-identified */
    @Transactional
    public int eraseCustomer(String login) {
        String alias = pseudonym(login);

        List<Review> mine = reviews.findAll().stream().filter(r -> login.equals(r.getCustomerLogin())).toList();
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
