package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.VerificationState;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * ADDED, decisions.md D16/D29. Who verified this professional, when, and on what evidence.
 *
 * `Professional.verification` is a bare enum shown publicly beside somebody's name, and until this
 * existed there was no record of how it got its value. For a trust signal that is thin: nobody could
 * answer \"who decided this, and what did they see\" — not for a complaint, not for an audit, not for
 * the professional asking why they were rejected.
 *
 * APPEND-ONLY. There is no endpoint to edit or delete a review, for the same reason there is none to
 * delete a Review: a history that can be rewritten answers none of the questions it exists for. A
 * decision that was wrong is corrected by appending the new one.
 *
 * Note the tension with \"derived, never stored\": `Professional.verification` is now strictly the
 * projection of the latest row here. It stays a column because `verifiedOnly` filters Browse on it
 * and because all 18 seeded professionals arrive with a state and no review history — the prototype
 * has no such data to extract. Both are written in ONE transaction by the desk resource, which is
 * what stops them drifting.
 *
 * There is no evidence STORE. `evidenceRef` names a document held somewhere else — a case number, a
 * file reference in the back-office. Document storage is unbuilt and out of scope here (D16).
 */
@Schema(
    description = "ADDED, decisions.md D16/D29. Who verified this professional, when, and on what evidence.\n\n`Professional.verification` is a bare enum shown publicly beside somebody's name, and until this\nexisted there was no record of how it got its value. For a trust signal that is thin: nobody could\nanswer \"who decided this, and what did they see\" — not for a complaint, not for an audit, not for\nthe professional asking why they were rejected.\n\nAPPEND-ONLY. There is no endpoint to edit or delete a review, for the same reason there is none to\ndelete a Review: a history that can be rewritten answers none of the questions it exists for. A\ndecision that was wrong is corrected by appending the new one.\n\nNote the tension with \"derived, never stored\": `Professional.verification` is now strictly the\nprojection of the latest row here. It stays a column because `verifiedOnly` filters Browse on it\nand because all 18 seeded professionals arrive with a state and no review history — the prototype\nhas no such data to extract. Both are written in ONE transaction by the desk resource, which is\nwhat stops them drifting.\n\nThere is no evidence STORE. `evidenceRef` names a document held somewhere else — a case number, a\nfile reference in the back-office. Document storage is unbuilt and out of scope here (D16)."
)
@Entity
@Table(name = "verification_review")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class VerificationReview implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "reference", nullable = false, unique = true)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false)
    private VerificationState decision;

    @NotNull
    @Column(name = "reviewer", nullable = false)
    private String reviewer;

    @NotNull
    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Size(max = 200)
    @Column(name = "evidence_ref", length = 200)
    private String evidenceRef;

    @Size(max = 1000)
    @Column(name = "note", length = 1000)
    private String note;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(
        value = {
            "services",
            "availabilities",
            "rules",
            "overrides",
            "reviews",
            "credentials",
            "highlights",
            "verificationReviews",
            "category",
        },
        allowSetters = true
    )
    private Professional professional;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public VerificationReview id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReference() {
        return this.reference;
    }

    public VerificationReview reference(String reference) {
        this.setReference(reference);
        return this;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public VerificationState getDecision() {
        return this.decision;
    }

    public VerificationReview decision(VerificationState decision) {
        this.setDecision(decision);
        return this;
    }

    public void setDecision(VerificationState decision) {
        this.decision = decision;
    }

    public String getReviewer() {
        return this.reviewer;
    }

    public VerificationReview reviewer(String reviewer) {
        this.setReviewer(reviewer);
        return this;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public Instant getReviewedAt() {
        return this.reviewedAt;
    }

    public VerificationReview reviewedAt(Instant reviewedAt) {
        this.setReviewedAt(reviewedAt);
        return this;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getEvidenceRef() {
        return this.evidenceRef;
    }

    public VerificationReview evidenceRef(String evidenceRef) {
        this.setEvidenceRef(evidenceRef);
        return this;
    }

    public void setEvidenceRef(String evidenceRef) {
        this.evidenceRef = evidenceRef;
    }

    public String getNote() {
        return this.note;
    }

    public VerificationReview note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public VerificationReview professional(Professional professional) {
        this.setProfessional(professional);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VerificationReview)) {
            return false;
        }
        return getId() != null && getId().equals(((VerificationReview) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "VerificationReview{" +
            "id=" + getId() +
            ", reference='" + getReference() + "'" +
            ", decision='" + getDecision() + "'" +
            ", reviewer='" + getReviewer() + "'" +
            ", reviewedAt='" + getReviewedAt() + "'" +
            ", evidenceRef='" + getEvidenceRef() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
