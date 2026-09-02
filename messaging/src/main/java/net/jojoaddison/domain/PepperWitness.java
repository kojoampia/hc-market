package net.jojoaddison.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * What this service's pepper produced the first time it ran with one — {@code decisions.md} D35.
 *
 * <h2>The thing nothing could detect</h2>
 *
 * <p>D35 says it plainly: rotating the pepper is indistinguishable, from the rows' point of view,
 * from removing it, and {@code ErasureRegisterGuard} caught only the removal, "because a wrong pepper
 * looks exactly like a right one until something fails to match". Nothing fails at the time. The
 * service starts, the desk works, aliases are derived and stored — they simply stop matching the ones
 * already in {@code erased_subject}, so {@code isErased} answers {@code false} about people this
 * service erased, and the first symptom is a booking event writing an erased customer's real login
 * back months later.
 *
 * <p>This is one row holding the alias of a fixed sentinel input. Recomputing it at every startup and
 * comparing turns that into a refusal at deploy time, in front of the person who just changed the
 * variable.
 *
 * <h2>Why it is a table of its own and not a row in the register</h2>
 *
 * <p>Because {@code ErasureRegisterGuard} asks the register exactly one question — <em>has this
 * service erased anybody</em> — and answers "no pepper, empty register, start anyway" on it. That
 * allowance is load-bearing: it is what lets {@code isErased} return {@code false} and
 * {@code lockSubject} do nothing instead of throwing and stalling the booking-event consumer over a
 * variable one desk endpoint reads. A sentinel row in {@code erased_subject} would make
 * {@code count()} non-zero forever, the allowance would disappear silently, and the failure would
 * present as a service refusing to start over a person it never erased.
 *
 * <p>{@code ErasureResourceIT.theWitnessIsNotAnErasedSubject} pins that separation.
 *
 * <h2>What it does and does not give away</h2>
 *
 * <p>The alias is a MAC of a constant, so anyone reading this table gets a known input/output pair
 * for the pepper. That is not a re-identification risk — the input is not a person and no login can
 * produce it — but it does let a guessed pepper be confirmed offline, which makes the pepper's own
 * quality matter: 32 random bytes as every script here generates, never a memorable phrase.
 *
 * <p>The sentinel input contains a NUL character precisely so that no login, however chosen, can
 * collide with it, and so the row can never be mistaken for a real erased subject.
 */
@Entity
@Table(name = "privacy_pepper_witness")
public class PepperWitness implements Serializable {

    private static final long serialVersionUID = 1L;

    /** One row, one fixed key — see {@code ErasureRegisterGuard.WITNESS_ID}. */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id;

    /** {@code erased-<16 hex>} for the sentinel input, under the pepper this service started with. */
    @Column(name = "subject_alias", nullable = false, length = 64)
    private String subjectAlias;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    public PepperWitness() {}

    public PepperWitness(String id, String subjectAlias, Instant firstSeen) {
        this.id = id;
        this.subjectAlias = subjectAlias;
        this.firstSeen = firstSeen;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubjectAlias() {
        return subjectAlias;
    }

    public void setSubjectAlias(String subjectAlias) {
        this.subjectAlias = subjectAlias;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }
}
