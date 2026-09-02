package net.jojoaddison.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * One handle a payment provider gave this platform, kept — {@code decisions.md} D41.
 *
 * <h2>Why this exists at all, against "derived, never stored"</h2>
 *
 * <p>This repository's central rule is that a figure is computed from its source rather than kept
 * beside it, because a kept copy can drift. A {@code providerReference} is the opposite kind of value:
 * it is <strong>issued by somebody else</strong> and is derivable from nothing this estate holds. If
 * it is not written down at the moment it arrives it is gone, and with it the only means of capturing,
 * refunding, voiding or even asking after money the customer has already committed. Storing it does
 * not reverse the rule; losing it loses money. {@code Professional.verification} (D16) is the existing
 * precedent for a stated exception of this shape.
 *
 * <h2>A table rather than a column on {@link Booking}, and the reason is a sequence</h2>
 *
 * <p>The authorization happens <em>before</em> the booking row is written, deliberately — D31 put it
 * there so that a third-party call is not inside the transaction that publishes {@code
 * booking.requested}. So at the instant the handle arrives <strong>there is no booking row to put it
 * on</strong>, and the case where that matters most is precisely the one this package was opened for:
 * {@code creator.create} throws, the money is committed, and a column on a row that was never written
 * would hold nothing to void it with.
 *
 * <p>Three other things a column could not hold, all of them already scheduled: a payment that is
 * authorized and later captured in part (two movements, one booking); a customer who is declined by
 * one provider and pays with another (WP-13 — two handles, one booking, and the abandoned one still
 * has to be voidable in case its provider confirms late); and a webhook that arrives naming only the
 * provider's own reference (WP-11 — which needs to look a payment up <em>by</em> this column, not by
 * the booking).
 *
 * <p>What it costs: a table, an index, and a join for anyone who wants a booking and its money in one
 * read. Nothing in the estate wants that today — no screen shows a payment — so the cost is paid in
 * the future and the alternative would have had to be torn up by WP-11 in any case.
 *
 * <h2>It holds no personal data, and that is a decision rather than an accident</h2>
 *
 * <p>The customer appears nowhere on this row: not their login, not their name, not a contact detail a
 * provider might have needed to raise a prompt. The only thing tying a payment to a person is
 * {@code bookingReference}, which erasure keeps on purpose (D24/D31 — the money and the reference
 * survive an erasure; the person does not). So {@code ErasureWorkflow} does not sweep this table, and
 * that is correct <em>as long as the columns stay as they are</em>. Two rules follow, and they are the
 * whole reason this paragraph is here: never add a customer field to this entity without adding it to
 * the sweep in the same commit, and note that {@link #getAttentionNote()} is composed by this platform
 * from a provider name, a reference and an exception class — never copied from a provider's message
 * text, which is exactly where a customer's name would arrive unannounced (D39's stored receipt had
 * the same hazard, by way of a URL).
 *
 * <h2>{@code state} is a String</h2>
 *
 * <p>It carries a {@code PaymentState} name, but not the enum: {@code PaymentState} lives in
 * {@code service.payment} and {@code TechnicalStructureTest} forbids {@code domain} from reaching
 * {@code service}. A copy of the enum under {@code domain.enumeration} would be a second vocabulary to
 * keep in step with the port's, which is worse than a column of names the service converts.
 *
 * <p>Hand-written rather than generated from JDL, exactly as {@link OutboxEvent} is: the JDL models
 * what the product is about, and this is infrastructure. Its changelog needs an include in
 * {@code master.xml} that a regeneration will drop — see the hazard table in {@code CLAUDE.md}.
 */
@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A UUID. Not the booking reference: WP-13's second attempt must not overwrite the first. */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** What was being paid for. Indexed, and deliberately not unique — see the class comment. */
    @Column(name = "booking_reference", nullable = false, length = 64)
    private String bookingReference;

    /** {@code PaymentProvider.name()} at the time. {@code "none"} never reaches this table. */
    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    /** The provider's own handle. The reason this table exists; never null in a stored row. */
    @Column(name = "provider_reference", nullable = false, length = 255)
    private String providerReference;

    /** A {@code PaymentState} name. See the class comment for why it is not the enum. */
    @Column(name = "state", nullable = false, length = 32)
    private String state;

    /** Pesewas, as everywhere else in this estate. Never a double. */
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** When the platform was given the handle. Never moved — same discipline as {@code erasedAt}. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** When the platform last changed its belief about this payment. Null while nothing has moved. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Money this platform committed and could not release. The one column an operator queries for.
     *
     * <p>True means: the provider holds the customer's money, the booking it was for does not exist,
     * and the call to give it back did not succeed. Nothing retries it — a second automatic attempt
     * against a provider that has just failed is how one stuck payment becomes several — so this is a
     * person's job, and this column is how they find it.
     */
    @Column(name = "needs_attention", nullable = false)
    private boolean needsAttention;

    /** Composed here, never copied from a provider's message. See the class comment. */
    @Column(name = "attention_note", length = 255)
    private String attentionNote;

    public PaymentAttempt() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public boolean isNeedsAttention() {
        return needsAttention;
    }

    public void setNeedsAttention(boolean needsAttention) {
        this.needsAttention = needsAttention;
    }

    public String getAttentionNote() {
        return attentionNote;
    }

    public void setAttentionNote(String attentionNote) {
        this.attentionNote = attentionNote;
    }
}
