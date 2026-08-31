package net.jojoaddison.web.rest;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.VerificationReview;
import net.jojoaddison.domain.enumeration.VerificationState;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.VerificationWorkflow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The verification desk — {@code decisions.md} D16/D29.
 *
 * <p>Two endpoints: append a decision, and read the history. Between them they answer the question
 * {@code Professional.verification} could not: who decided this, when, and on what evidence.
 *
 * <h2>Why this class exists rather than the generated CRUD</h2>
 *
 * <p>The JDL generates a {@code VerificationReviewResource} with unscoped create, update and delete
 * on {@code /api/verification-reviews}. That is deleted, and must stay deleted after every
 * regeneration — see the checklist in {@code CLAUDE.md}. It would let <strong>any authenticated
 * user forge or erase an audit trail about somebody else's trustworthiness</strong>, which is worse
 * than the availability-editing hole the same deletion prevents for
 * {@code AvailabilityRuleResource}: a forged verification is a public claim about a real person.
 *
 * <h2>ROLE_BROKERAGE, and the actor comes from the token</h2>
 *
 * <p>Not {@code ROLE_ADMIN}: deciding whether someone carries the VERIFIED badge is a narrower and
 * more consequential power than general administration, and the same reasoning that gave the dispute
 * desk its own authority applies here.
 *
 * <p>{@code reviewer} is the JWT subject and is not accepted from the request body — not even
 * optionally. An audit trail whose actor the caller supplies records nothing, and the field would
 * look exactly as trustworthy either way.
 *
 * <h2>What the badge is allowed to mean</h2>
 *
 * <p>Documents seen by a person. The scope note restricts this marketplace to non-medical
 * professionals, who are not licensed by a statutory body, so for most listings there is no register
 * to check against — {@code evidenceRef} names a document held elsewhere rather than implying a
 * lookup that does not exist. There is no document store in this service and D16 does not ask for
 * one.
 */
@RestController
@RequestMapping("/api/desk/professionals")
@PreAuthorize("hasAuthority('" + MarketplaceAuthorities.BROKERAGE + "')")
public class VerificationDeskResource {

    private final VerificationWorkflow verification;

    public VerificationDeskResource(VerificationWorkflow verification) {
        this.verification = verification;
    }

    /**
     * The append-only history for one professional, newest first.
     *
     * <p>An empty list is a real answer, not a 404: every professional seeded before this existed
     * has a verification state and no history behind it, and saying so plainly is more useful than
     * pretending the professional is unknown.
     */
    @GetMapping("/{ref}/verification")
    public List<VerificationRecord> history(@PathVariable String ref) {
        return verification.historyOf(ref).stream().map(VerificationDeskResource::toRecord).toList();
    }

    /**
     * Records a decision and moves the professional to it.
     *
     * <p>201, because this appends a row — it is not an update of the previous decision, and a
     * response that read as one would misdescribe the whole point of the endpoint.
     */
    @PostMapping("/{ref}/verification")
    public ResponseEntity<VerificationRecord> record(@PathVariable String ref, @RequestBody @NotNull DecideVerification request) {
        if (request == null || request.decision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a decision is required");
        }
        String reviewer = SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated reviewer"));
        try {
            VerificationReview saved = verification.record(ref, request.decision(), reviewer, request.evidenceRef(), request.note());
            return ResponseEntity.status(HttpStatus.CREATED).body(toRecord(saved));
        } catch (VerificationWorkflow.UnknownProfessional unknown) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, unknown.getMessage());
        }
    }

    private static VerificationRecord toRecord(VerificationReview r) {
        return new VerificationRecord(
            r.getReference(),
            r.getDecision() == null ? null : r.getDecision().name(),
            r.getReviewer(),
            r.getReviewedAt(),
            r.getEvidenceRef(),
            r.getNote()
        );
    }

    /** No {@code reviewer} component, deliberately — a body that sends one is a 400. See the class comment. */
    public record DecideVerification(VerificationState decision, String evidenceRef, String note) {}

    public record VerificationRecord(String reference, String decision, String reviewer, Instant reviewedAt, String evidenceRef, String note) {}
}
