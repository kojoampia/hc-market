package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.DisputeStatusChange;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.repository.BookingStatusChangeEraseRepository;
import net.jojoaddison.repository.DisputeEraseRepository;
import net.jojoaddison.repository.DisputeStatusChangeEraseRepository;
import net.jojoaddison.repository.OutboxEraseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a customer from the booking service — {@code decisions.md} D24/D31/D34.
 *
 * <h2>Pseudonymisation, not deletion</h2>
 *
 * <p>The rows stay; the person goes. {@code customerName}, {@code visitAddress} — a home address —
 * {@code customerNote}, {@code onBehalfOf} and {@code cancellationReason} are redacted, and
 * {@code customerLogin} is replaced by a stable pseudonym. Everything the rest of the estate depends
 * on is untouched: {@code bookingReference}, the money fields, {@code professionalRef}, the status
 * history.
 *
 * <p>Deleting instead would break more than it protects. {@code Ledger} rows in payout are keyed by
 * {@code bookingReference} and financial records carry their own retention obligation that a data
 * subject's erasure request does not override; reviews are keyed by booking; and a professional's
 * earnings are aggregates over rows that must still be there to be aggregated.
 *
 * <h2>Five tables, not one — D34</h2>
 *
 * <p>This erased the {@code booking} table and nothing else until a code review went looking, and the
 * receipt said "1 booking erased" while four other tables still named the person:
 *
 * <ul>
 *   <li><strong>{@code outbox_event}</strong> — the worst of them. Every event carries
 *       {@code customerLogin} and {@code customerName} in its payload, and no purge of sent rows
 *       exists anywhere in this service, so the identity sat in one row per event ever published
 *       about them, indefinitely. Rewritten in place, <em>including unsent rows</em>: an event still
 *       waiting to go out should carry the pseudonym to its consumer rather than rely on the consumer
 *       recognising the login as erased.
 *   <li><strong>{@code dispute}</strong> — {@code raisedByLogin} is the customer's, and
 *       {@code reason} is a thousand characters of whatever they typed about what went wrong.
 *   <li><strong>{@code booking_status_change}</strong> and <strong>{@code dispute_status_change}</strong>
 *       — {@code actor} is the acting login, and requesting and cancelling are customer actions, so
 *       the audit trail of every booking they made carried their login. The {@code note} column on
 *       both is a system string ({@code "raised"}, the transition's action) rather than user text, so
 *       it is left alone.
 *   <li><strong>{@code booking.cancellation_reason}</strong> — 400 characters, usually written by
 *       whoever cancelled, and often the customer explaining something personal about why.
 * </ul>
 *
 * <p><strong>{@code Dispute.resolution} is deliberately kept</strong>, and it is the one judgement
 * here most likely to need revisiting. It is the brokerage's own record of how a financial dispute
 * was settled, it underpins a compensating ledger entry, and it is retained on the same basis the
 * ledger is. It may name the customer. That question goes to counsel beside the review-body one.
 *
 * <h2>Why this is cheap here, and would not have been</h2>
 *
 * <p>Redacting a customer requires <strong>no recomputation anywhere</strong>, and that is the
 * "derived, never stored" rule paying off in a place nobody chose it for. There is no
 * {@code professional.total_earnings} and no stored rating, so every figure in the estate is a view
 * or a query over rows this leaves in place. Had those totals been columns, erasure would have meant
 * recomputing each one and getting every rounding decision right a second time.
 *
 * <h2>The pseudonym is deterministic, and that is deliberate</h2>
 *
 * <p>{@code erased-<first 12 hex of SHA-256(login)>}. Same input, same output, so one person's rows
 * stay grouped for accounting and audit without naming them — and a fresh random value per row would
 * have made the booking history of a single erased customer impossible to reconcile against a
 * payout.
 *
 * <p><strong>It is not a secret, and the doc used to imply otherwise.</strong> Logins are short and
 * guessable, so anyone with read access to a database here can hash candidate logins offline and
 * match them against the stored aliases. That is a known limitation, recorded rather than glossed;
 * see D34's note on peppering it.
 *
 * <h2>What this does NOT do</h2>
 *
 * <p>It is on demand. Nothing schedules it, because there is no scheduler anywhere in this estate —
 * the same gap {@code Dispute.dueBy} records. {@code healthconnect.privacy.retention-days} exists as
 * configuration with <strong>no default</strong> precisely so that nothing here implies a retention
 * period nobody with legal standing has set. Counsel supplies numbers; this supplies the mechanism.
 */
@Service
public class ErasureWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureWorkflow.class);

    /** Not a name anyone can be confused for, and obviously deliberate when read in a database. */
    static final String REDACTED = "[erased]";

    private final BookingQueryRepository bookings;
    private final DisputeEraseRepository disputes;
    private final BookingStatusChangeEraseRepository bookingHistory;
    private final DisputeStatusChangeEraseRepository disputeHistory;
    private final OutboxEraseRepository outbox;
    private final ObjectMapper mapper;

    public ErasureWorkflow(
        BookingQueryRepository bookings,
        DisputeEraseRepository disputes,
        BookingStatusChangeEraseRepository bookingHistory,
        DisputeStatusChangeEraseRepository disputeHistory,
        OutboxEraseRepository outbox,
        ObjectMapper mapper
    ) {
        this.bookings = bookings;
        this.disputes = disputes;
        this.bookingHistory = bookingHistory;
        this.disputeHistory = disputeHistory;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /** {@code erased-<12 hex>} — see the class comment for why it is deterministic. */
    public static String pseudonym(String login) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(login.getBytes(StandardCharsets.UTF_8));
            return "erased-" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable, which should not be possible", e);
        }
    }

    /**
     * Redacts everything this service holds about {@code login} and returns what it touched.
     *
     * <p>Idempotent: running it twice is harmless, because the second pass finds nothing under the
     * original login. A data subject request that is retried — and they are, because they arrive by
     * email and get forwarded — must not behave differently the second time.
     *
     * <p>One transaction across all five tables. A partial erasure is the one outcome worse than a
     * failed one, because the receipt would be filed either way.
     */
    @Transactional
    public Erased eraseCustomer(String login) {
        String alias = pseudonym(login);

        List<Booking> mine = bookings.findByCustomerLoginOrderByScheduledDateDesc(login);
        List<String> references = mine.stream().map(Booking::getReference).filter(r -> r != null && !r.isBlank()).toList();

        for (Booking b : mine) {
            b.setCustomerLogin(alias);
            b.setCustomerName(REDACTED);
            // The free-text fields. visitAddress is a home address, customerNote is where people put
            // the things the schema never asked for, and cancellationReason is where they explain
            // why — which is exactly where something personal ends up.
            b.setVisitAddress(null);
            b.setCustomerNote(null);
            b.setOnBehalfOf(null);
            b.setCancellationReason(null);
        }
        bookings.saveAll(mine);

        int events = redactOutbox(references, login, alias);

        List<Dispute> raised = disputes.findByRaisedByLogin(login);
        for (Dispute d : raised) {
            d.setRaisedByLogin(alias);
            d.setReason(REDACTED);
            // resolution is left — see the class comment.
        }
        disputes.saveAll(raised);

        List<BookingStatusChange> bookingActs = bookingHistory.findByActor(login);
        bookingActs.forEach(c -> c.setActor(alias));
        bookingHistory.saveAll(bookingActs);

        List<DisputeStatusChange> disputeActs = disputeHistory.findByActor(login);
        disputeActs.forEach(c -> c.setActor(alias));
        disputeHistory.saveAll(disputeActs);

        LOG.info(
            "erased {} booking(s), {} outbox payload(s), {} dispute(s) and {} history row(s), now {}",
            mine.size(),
            events,
            raised.size(),
            bookingActs.size() + disputeActs.size(),
            alias
        );
        return new Erased(mine.size(), events, raised.size(), bookingActs.size() + disputeActs.size());
    }

    /**
     * Rewrites the customer's identity out of every event published about their bookings.
     *
     * <p>Parsed and re-serialised rather than string-replaced: a payload is JSON, and a blind
     * substitution would corrupt it the first time a login appeared inside another value. Only the
     * two identity fields are touched, so an event stays a valid event — a consumer replaying one
     * after this still gets a booking reference, a price and a professional.
     *
     * <p>A payload that will not parse is left alone and logged rather than dropped. It cannot happen
     * from this service's own writer, and guessing at the contents of something unparseable is worse
     * than reporting that one row needs a human.
     */
    private int redactOutbox(List<String> references, String login, String alias) {
        if (references.isEmpty()) {
            return 0;
        }
        int touched = 0;
        List<OutboxEvent> events = outbox.findByAggregateRefIn(references);
        for (OutboxEvent e : events) {
            if (login.equals(e.getActor())) {
                e.actor(alias);
            }
            try {
                JsonNode parsed = mapper.readTree(e.getPayload());
                if (parsed instanceof ObjectNode payload) {
                    if (login.equals(payload.path("customerLogin").asText(null))) {
                        payload.put("customerLogin", alias);
                    }
                    payload.put("customerName", REDACTED);
                    e.payload(mapper.writeValueAsString(payload));
                    touched++;
                }
            } catch (Exception parseFailure) {
                LOG.warn("outbox row {} has a payload that will not parse; left as it is", e.getId(), parseFailure);
            }
        }
        outbox.saveAll(events);
        return touched;
    }

    /**
     * @param bookingsErased rows in {@code booking}
     * @param outboxPayloadsRedacted published events whose payload named the customer — reported
     *     separately because it is the count most likely to be non-zero when every other one is zero,
     *     and it was silently zero for the whole of D31
     * @param disputesRedacted rows in {@code dispute}
     * @param historyRowsReKeyed {@code actor} columns across both status-history tables
     */
    public record Erased(int bookingsErased, int outboxPayloadsRedacted, int disputesRedacted, int historyRowsReKeyed) {}
}
