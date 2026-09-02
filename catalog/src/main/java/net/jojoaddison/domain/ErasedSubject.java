package net.jojoaddison.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * A customer this service has erased — {@code decisions.md} D39.
 *
 * <h2>Why catalog needed one</h2>
 *
 * <p>Messaging has had this table since D32, where it is <em>consulted</em> by the booking-event
 * consumer before anything keyed to a person is written. Catalog consumes nothing and had no
 * register, so an erasure here left a log line and an HTTP response that ends with the request — and
 * catalog is the service that <strong>deletes</strong>. A saved list is removed outright, on purpose
 * (D24), and until D39 the receipt did not even say how many rows went. An irreversible deletion with
 * no durable record of having happened is the thinnest account in the estate.
 *
 * <h2>Pseudonyms only, and never the login</h2>
 *
 * <p>Copied from messaging's {@code ErasedSubject} as a principle rather than as a schema: a register
 * of erased people that names them is exactly what erasure was asked to remove, and unlike the rows
 * being redacted it would have to be kept for ever. So this holds the alias — an HMAC of the login
 * under the estate's pepper, see {@code SubjectPseudonym} — and a timestamp. Somebody who already
 * holds a login can hash it and ask whether it is here; nothing goes the other way. This therefore
 * cannot answer "who has been erased", only "has <em>this</em> person been".
 *
 * <h2>One row per subject, written once</h2>
 *
 * <p>{@code erasedAt} must survive a retry — data subject requests arrive by email and get forwarded,
 * so they get run twice, and {@code save()} on an existing primary key would replace the original
 * timestamp with the date of the second run. D35 hit precisely that. The record of a fan-out
 * <em>attempt</em>, of which there can be several, lives in booking's {@code erasure_run} table
 * instead: booking orchestrates, and a leg that fails cannot record its own failure.
 *
 * <p><strong>Nothing reads this to decide anything yet.</strong> Catalog needs no equivalent of
 * messaging's {@code ErasureRegisterGuard} for that reason, and because an unpeppered service refuses
 * to erase at all (D35), so no row here can carry an alias this service cannot reproduce. The day
 * something consults it — WP-08 is the candidate — it needs the guard messaging has.
 */
@Entity
@Table(name = "erased_subject")
public class ErasedSubject implements Serializable {

    private static final long serialVersionUID = 1L;

    /** {@code erased-<first 16 hex of HMAC-SHA256(pepper, login)>} — see {@code SubjectPseudonym}. */
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
