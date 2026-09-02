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
 * <h2>Why booking needed one</h2>
 *
 * <p>Messaging has had this table since D32, where it earns its place by being <em>consulted</em>: a
 * lagging {@code booking.requested} would otherwise re-create a conversation under a login erased
 * seconds earlier, so the consumer asks the register before it writes anything keyed to a person.
 * Booking had no such consumer and therefore no register, and recorded an erasure only in a log line
 * and an HTTP response that ends with the request.
 *
 * <p>That is thin for an irreversible act with legal significance. An erasure receipt is not a
 * convenience — it is the artefact an operator files against a data subject request, and the case
 * where they most need it is the one where the fan-out returned 502 and they have to be able to say
 * afterwards which legs ran. A log line is not a record an audit can be shown.
 *
 * <h2>Pseudonyms only, and never the login</h2>
 *
 * <p>The principle is messaging's and it is copied here deliberately rather than reinvented: a
 * register of erased people <em>that names them</em> is precisely the thing erasure was asked to
 * remove, and unlike every row being redacted it would have to be kept for ever. So this holds the
 * alias, which is an HMAC of the login under a per-estate pepper ({@code SubjectPseudonym}), and a
 * timestamp. Anyone who already holds a login can hash it and ask whether it is here; nobody can go
 * the other way. The consequence is the same one messaging accepted: this cannot answer "who has been
 * erased", only "has <em>this</em> person been", and that is the only question anything asks.
 *
 * <h2>One row per subject, written once</h2>
 *
 * <p>{@code erasedAt} is the fact an audit will ask for, so a re-run must not move it — data subject
 * requests arrive by email, get forwarded, and get retried, and {@code save()} on an existing primary
 * key would replace the original timestamp with the date of whoever ran the erasure a second time.
 * D35 hit exactly that. The account of a single <em>attempt</em>, which there can be several of and
 * which must not overwrite each other, is {@link ErasureRun} in its own table.
 *
 * <p><strong>Nothing reads this to make a decision yet</strong>, which is why booking has no
 * equivalent of messaging's {@code ErasureRegisterGuard}: an unpeppered service refuses to erase at
 * all (D35), so no row here can have been written under a derivation this service cannot reproduce.
 * WP-08 is the change that alters that — scoping the register by the age of the booking, so that
 * somebody who books again after being erased is stored under their real login while everything that
 * existed before stays pseudonymised. The day something here answers a question rather than merely
 * recording one, it needs the guard messaging has.
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
