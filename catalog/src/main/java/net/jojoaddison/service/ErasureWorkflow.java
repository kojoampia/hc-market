package net.jojoaddison.service;

import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.ErasedSubject;
import net.jojoaddison.domain.Favourite;
import net.jojoaddison.domain.Review;
import net.jojoaddison.repository.ErasedSubjectRepository;
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
 * <h2>And it is recorded — D39</h2>
 *
 * <p>{@link net.jojoaddison.domain.ErasedSubject} is written in the same transaction as the sweep, so
 * a record of an erasure that did not commit is impossible. This service kept no durable trace of an
 * erasure at all until then, which was the thinnest account in the estate for the service that
 * <em>deletes</em>: a saved list is removed outright, and the act left a log line and an HTTP response
 * that ended with the request. Pseudonyms only, never a login — the register's javadoc gives the
 * reasoning at length.
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
    private final ErasedSubjectRepository register;
    private final SubjectPseudonym pseudonyms;

    public ErasureWorkflow(
        ReviewEraseRepository reviews,
        FavouriteQueryRepository favourites,
        ErasedSubjectRepository register,
        SubjectPseudonym pseudonyms
    ) {
        this.reviews = reviews;
        this.favourites = favourites;
        this.register = register;
        this.pseudonyms = pseudonyms;
    }

    /**
     * {@code erased-<16 hex>} — identical rule to booking's and messaging's, so one person carries one
     * alias estate-wide. An instance method since D35, because the pepper is configuration.
     */
    public String pseudonym(String login) {
        return pseudonyms.of(login);
    }

    /**
     * Redacts this customer's reviews, deletes their saved list, and returns both counts.
     *
     * <p><strong>Both, since {@code decisions.md} D39.</strong> This returned the review count alone,
     * so a customer with no reviews and a saved list of twelve professionals produced a receipt of
     * zeroes — filed by an operator against a legal request as "catalog held nothing for this person",
     * while the one irreversible <em>deletion</em> this whole feature performs went unrecorded. It is
     * the same defect D31 found in messaging, where an empty thread was re-keyed and reported as
     * nothing; it was fixed there and never looked for here.
     *
     * <p>Both counts are of rows that <em>changed</em>: reviews are matched by the customer's login,
     * which the first pass replaces, and favourites are deleted, so a retry reports zeroes without
     * anything having to compare. See messaging's {@code ErasureWorkflow} for the case where that is
     * not automatic.
     */
    @Transactional
    public Erased eraseCustomer(String login) {
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

        /* The durable record of the act — decisions.md D39. Written once, and NEVER re-written: a
           retry must not move erasedAt, because that timestamp is the one fact an audit of an
           irreversible action asks for and save() on an existing primary key would replace it with
           the date of whoever ran the erasure a second time. D35 hit exactly that in messaging.
           Written here rather than in the resource so both callers get it — the desk and the erasure
           fan-out are the same method. */
        if (!register.existsById(alias)) {
            register.save(new ErasedSubject(alias, Instant.now()));
        }

        LOG.info("de-identified {} review(s) and removed {} favourite(s), now {}", mine.size(), saved.size(), alias);
        return new Erased(mine.size(), saved.size());
    }

    /**
     * @param reviewsDeidentified reviews whose author is now the alias. The review body is untouched —
     *     see the class comment for the decision behind that
     * @param favouritesDeleted rows removed from the saved list, and the only thing this feature
     *     deletes anywhere in the estate. Reported for exactly that reason
     */
    public record Erased(int reviewsDeidentified, int favouritesDeleted) {}
}
