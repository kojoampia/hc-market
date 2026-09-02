package net.jojoaddison.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * One attempt at erasing a customer everywhere, kept — {@code decisions.md} D39.
 *
 * <h2>The receipt is the deliverable, and it used to evaporate</h2>
 *
 * <p>{@code POST /api/desk/customers/{login}/erase-everywhere} returns one receipt naming each of the
 * three legs, its status and its counts, and that receipt is the <em>only</em> account anywhere of
 * which legs ran. D38 states as much. It lived for the length of an HTTP response, which is fine
 * while everything succeeds and useless in the case it exists for: a 502 where two services erased
 * and the third did not, and an operator who has to be able to prove afterwards what did and did not
 * happen — to an auditor, or to the data subject.
 *
 * <h2>Why this is one table in booking and not three</h2>
 *
 * <p>Two different facts, and they belong in different places.
 *
 * <p>{@link ErasedSubject} — "this service erased this person, at this time" — is a <em>local</em>
 * fact and each service records its own, because only the service that ran a sweep can attest to it.
 * A central register would say "booking believes catalog erased X", which is not the same claim and
 * is exactly the claim that turned out to be false the day catalog was never called.
 *
 * <p>This — "a fan-out was attempted, and here is what came back from each leg" — cannot be
 * distributed at all, and the reason is the whole of the argument: <strong>a leg that fails is a leg
 * that cannot record its own failure.</strong> The service that could not be reached writes nothing,
 * by definition, so the only place the partial outcome exists is the orchestrator. Booking is the
 * orchestrator (D38) because it holds the booking references the other legs need, so this is one row
 * here rather than three rows nowhere.
 *
 * <h2>Append-only, one row per attempt</h2>
 *
 * <p>Deliberately <em>not</em> keyed by the subject. Retries are the normal case — requests arrive by
 * email, get forwarded, and the instruction after a 502 is to call again — and a row keyed by alias
 * would make the second attempt overwrite the first, destroying the evidence of the partial run that
 * is the point of keeping any of this. That is D35's {@code save()}-moves-{@code erasedAt} defect
 * arriving in a new table. The key is a UUID and rows are never updated; the sequence of them is the
 * history of the request.
 *
 * <h2>The receipt is stored as it was rendered, and scrubbed of the login</h2>
 *
 * <p>{@link #getReceipt()} holds the JSON body the operator was shown, not a re-modelling of it.
 * {@code ErasureFanoutClient} copies each leg's counts through under whatever names that service gave
 * them, precisely so that a count added or removed downstream cannot be silently misreported here; a
 * table with a column per count would undo that on the durable side. A blob of the actual answer is
 * also the thing an audit asks for.
 *
 * <p>It is scrubbed first. A leg's failure message carries the root cause, and the root cause of an
 * unreachable leg is an I/O error naming the URL it was thrown from — which contains the customer's
 * login, because the path does. Storing that verbatim would put a register of erased people that
 * names them into the estate through the back door, in the one row written specifically to be kept.
 * Every occurrence of the login is replaced by the alias before this is written.
 */
@Entity
@Table(name = "erasure_run")
public class ErasureRun implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A UUID, not the subject: several attempts for one person must not overwrite each other. */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** {@code erased-<16 hex>} — the alias, never the login. See {@link ErasedSubject}. */
    @Column(name = "pseudonym", nullable = false, length = 64)
    private String pseudonym;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    /** Every leg erased. False is the answer this record exists to be able to give afterwards. */
    @Column(name = "complete", nullable = false)
    private boolean complete;

    /** How many booking references were handed to messaging — the input to its redaction. */
    @Column(name = "booking_references", nullable = false)
    private int bookingReferences;

    /** The receipt as the operator saw it, minus the login. */
    @Lob
    @Column(name = "receipt", nullable = false)
    private String receipt;

    public ErasureRun() {}

    public ErasureRun(String id, String pseudonym, Instant ranAt, boolean complete, int bookingReferences, String receipt) {
        this.id = id;
        this.pseudonym = pseudonym;
        this.ranAt = ranAt;
        this.complete = complete;
        this.bookingReferences = bookingReferences;
        this.receipt = receipt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPseudonym() {
        return pseudonym;
    }

    public void setPseudonym(String pseudonym) {
        this.pseudonym = pseudonym;
    }

    public Instant getRanAt() {
        return ranAt;
    }

    public void setRanAt(Instant ranAt) {
        this.ranAt = ranAt;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public int getBookingReferences() {
        return bookingReferences;
    }

    public void setBookingReferences(int bookingReferences) {
        this.bookingReferences = bookingReferences;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }
}
