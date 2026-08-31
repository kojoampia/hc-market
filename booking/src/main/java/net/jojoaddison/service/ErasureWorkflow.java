package net.jojoaddison.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a customer from the booking service — {@code decisions.md} D24/D31.
 *
 * <h2>Pseudonymisation, not deletion</h2>
 *
 * <p>The rows stay; the person goes. {@code customerName}, {@code visitAddress} — a home address —
 * {@code customerNote} and {@code onBehalfOf} are redacted, and {@code customerLogin} is replaced by
 * a stable pseudonym. Everything the rest of the estate depends on is untouched:
 * {@code bookingReference}, the money fields, {@code professionalRef}, the status history.
 *
 * <p>Deleting instead would break more than it protects. {@code Ledger} rows in payout are keyed by
 * {@code bookingReference} and financial records carry their own retention obligation that a data
 * subject's erasure request does not override; reviews are keyed by booking; and a professional's
 * earnings are aggregates over rows that must still be there to be aggregated.
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
 * payout. It is not reversible without already knowing the login, which is the property that matters:
 * anyone who has the login does not need this to learn anything.
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

    public ErasureWorkflow(BookingQueryRepository bookings) {
        this.bookings = bookings;
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
     * Redacts every booking belonging to {@code login} and returns how many were touched.
     *
     * <p>Idempotent: running it twice is harmless, because the second pass finds nothing under the
     * original login. A data subject request that is retried — and they are, because they arrive by
     * email and get forwarded — must not behave differently the second time.
     */
    @Transactional
    public int eraseCustomer(String login) {
        List<Booking> mine = bookings.findByCustomerLoginOrderByScheduledDateDesc(login);
        String alias = pseudonym(login);
        for (Booking b : mine) {
            b.setCustomerLogin(alias);
            b.setCustomerName(REDACTED);
            // The three free-text fields. visitAddress is a home address and customerNote is where
            // people put the things the schema never asked for.
            b.setVisitAddress(null);
            b.setCustomerNote(null);
            b.setOnBehalfOf(null);
        }
        bookings.saveAll(mine);
        LOG.info("erased {} booking(s) for a customer, now {}", mine.size(), alias);
        return mine.size();
    }
}
