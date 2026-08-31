package net.jojoaddison.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.VerificationReview;
import net.jojoaddison.domain.enumeration.VerificationState;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.VerificationReviewQueryRepository;
import net.jojoaddison.repository.VerificationReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recording who verified a professional, when, and on what evidence — {@code decisions.md} D16/D29.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@code Professional.verification} is a bare enum shown publicly beside somebody's name. Until
 * this existed there was no record of how it got its value: nobody could answer "who decided this,
 * and what did they see" — not for a complaint, not for an audit, and not for the professional
 * asking why they were rejected. For a trust signal customers act on when choosing who comes into
 * their home, that is thin.
 *
 * <p>D16 is also clear about what the badge can honestly mean. The scope note restricts this
 * marketplace to <em>non-medical</em> professionals, who are not licensed by a statutory body the
 * way a doctor is by the Medical and Dental Council, so "verify against a register" has no target
 * for most listings. Verification here means <strong>documents seen by a person</strong>, and
 * {@code evidenceRef} exists to name which documents rather than to imply a lookup that does not
 * exist. There is no document store here; the reference points at one elsewhere.
 *
 * <h2>Append-only, and one transaction</h2>
 *
 * <p>There is no method to edit or delete a review, exactly as there is no endpoint to delete a
 * {@code Review}: a history that can be rewritten answers none of the questions it exists for. A
 * decision that was wrong is corrected by appending the one that supersedes it.
 *
 * <p>The professional's {@code verification} column and the new row are written in the <em>same</em>
 * transaction, and that pairing is the whole reason this class exists rather than a resource calling
 * two repositories. The column is strictly the projection of the latest row; the moment the two can
 * be written separately, they can disagree, and the visible one would be the one nobody can audit.
 *
 * <p>This is a real tension with the rule the rest of the design turns on — derived, never stored —
 * and it is a deliberate exception rather than an oversight. {@code verification} stays a column
 * because Browse filters on it (`verifiedOnly`) and because all eighteen seeded professionals arrive
 * with a state and no history: the prototype has no review data to extract, and inventing some would
 * be fabricating an audit trail, which is worse than not having one.
 *
 * <h2>Not named VerificationReviewService</h2>
 *
 * <p>Adding {@code VerificationReview} to the JDL's {@code service ... with serviceClass} list would
 * make JHipster generate that name, and a regeneration would then replace this file wholesale — the
 * failure being a wall of "cannot find symbol" on methods that existed minutes ago. Same reason the
 * booking lifecycle lives in {@code BookingWorkflow}.
 */
@Service
@Transactional(readOnly = true)
public class VerificationWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationWorkflow.class);

    private final ProfessionalRepository professionals;
    private final MarketplaceQueryRepository marketplace;
    private final VerificationReviewRepository reviews;
    private final VerificationReviewQueryRepository reviewQueries;

    public VerificationWorkflow(
        ProfessionalRepository professionals,
        MarketplaceQueryRepository marketplace,
        VerificationReviewRepository reviews,
        VerificationReviewQueryRepository reviewQueries
    ) {
        this.professionals = professionals;
        this.marketplace = marketplace;
        this.reviews = reviews;
        this.reviewQueries = reviewQueries;
    }

    /** The history for one professional, newest first — the order a desk reads it in. */
    public List<VerificationReview> historyOf(String professionalRef) {
        return reviewQueries.findByProfessionalReferenceOrderByReviewedAtDesc(professionalRef);
    }

    /**
     * Records a decision and moves the professional's state to match it, in one transaction.
     *
     * @param professionalRef the business reference, e.g. {@code p1}
     * @param decision what the reviewer concluded
     * @param reviewer the deciding login, taken from the JWT subject and never from the request —
     *     an audit trail whose actor the caller supplies records nothing
     * @return the row that was appended
     * @throws UnknownProfessional if no professional carries that reference
     */
    @Transactional
    public VerificationReview record(String professionalRef, VerificationState decision, String reviewer, String evidenceRef, String note) {
        Professional professional = marketplace
            .findByReference(professionalRef)
            .orElseThrow(() -> new UnknownProfessional("no professional with reference " + professionalRef));

        VerificationReview review = reviews.save(
            new VerificationReview()
                // Short and not guessable in sequence, matching how booking and dispute references
                // are minted: these appear in back-office URLs.
                .reference("v-" + UUID.randomUUID().toString().substring(0, 8))
                .decision(decision)
                .reviewer(reviewer)
                .reviewedAt(Instant.now())
                .evidenceRef(evidenceRef)
                .note(note)
                .professional(professional)
        );

        professionals.save(professional.verification(decision));
        LOG.info("verification of {} set to {} by {} ({})", professionalRef, decision, reviewer, review.getReference());
        return review;
    }

    /** The reference names nobody. Distinct from a decision that was refused. */
    public static class UnknownProfessional extends RuntimeException {

        public UnknownProfessional(String message) {
            super(message);
        }
    }
}
