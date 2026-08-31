package net.jojoaddison.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * A customer who has been erased — {@code decisions.md} D24/D32.
 *
 * <h2>Why erasure needed a memory at all</h2>
 *
 * <p>Erasure was a one-shot sweep, and a sweep is only correct if nothing arrives afterwards. Things
 * do: booking publishes {@code booking.requested} and messaging raises the thread from it seconds
 * later, so a desk that erased a customer in between got a clean receipt and a fresh row under the
 * original login. Verified on the quality box, where a conversation reappeared as
 * {@code t-b-a2216d8d | verify.subject} moments after that login had been erased.
 *
 * <p>This table is what makes erasure a standing fact rather than a moment. The consumer asks it
 * before writing anything keyed to a person.
 *
 * <h2>It holds the pseudonym and nothing else</h2>
 *
 * <p>There is no column for the login. A register of erased people that names them is precisely the
 * thing erasure was asked to remove, and — unlike the rows being redacted — it would have to be kept
 * forever for the check to keep working. So the check runs the other way: the consumer already holds
 * a login, hashes it with the same rule everything else uses, and looks for the result. Neither side
 * ever stores the original.
 *
 * <p>The consequence worth stating: this cannot answer "who has been erased", only "has <em>this</em>
 * person been". That is the weaker question, and it is the only one anything here needs to ask.
 */
@Entity
@Table(name = "erased_subject")
public class ErasedSubject implements Serializable {

    private static final long serialVersionUID = 1L;

    /** {@code erased-<first 12 hex of SHA-256(login)>} — see {@code ErasureWorkflow.pseudonym}. */
    @Id
    @Column(name = "pseudonym", nullable = false, length = 64)
    private String pseudonym;

    @Column(name = "erased_at", nullable = false)
    private Instant erasedAt;

    public ErasedSubject() {}

    public ErasedSubject(String pseudonym, Instant erasedAt) {
        this.pseudonym = pseudonym;
        this.erasedAt = erasedAt;
    }

    public String getPseudonym() {
        return pseudonym;
    }

    public void setPseudonym(String pseudonym) {
        this.pseudonym = pseudonym;
    }

    public Instant getErasedAt() {
        return erasedAt;
    }

    public void setErasedAt(Instant erasedAt) {
        this.erasedAt = erasedAt;
    }
}
