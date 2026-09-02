package net.jojoaddison.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One operator action, three services erased — {@code decisions.md} D37 and D38.
 *
 * <h2>The problem this closes</h2>
 *
 * <p>A complete erasure has always been three calls: booking holds the visit address and the notes,
 * messaging the message bodies and the bell menus, catalog the review authorship and the saved list.
 * Making two of them and forgetting the third left a partially erased customer, and D24 recorded that
 * plainly rather than hiding it — but recording it does not stop it happening, and <strong>each of
 * the three receipts looks like a success on its own</strong>. There is no artefact anywhere in the
 * estate whose absence would tell anybody that the third call was never made. That is the defect:
 * not that erasure is hard, but that failing at it is invisible.
 *
 * <h2>Why it could not be built before</h2>
 *
 * <p>Nothing could call another service as anything but a stranger. D28 says so in as many words and
 * shapes catalog's {@code /internal/**} around it. D37 supplies the missing piece and corrected the
 * question it was asked: hc-market is not on the platform-wide signing key that hc-admin, hc-patient
 * and hc-professional share, so sequencing this from the hc-admin desk was never going to work — but
 * hc-market's own five services do share a key with each other, and one of them can mint a token the
 * others accept. See {@link FanoutTokenMinter} and
 * {@link net.jojoaddison.security.ErasureFanoutToken}.
 *
 * <h2>Booking first, then the others, and all three attempted whatever happens</h2>
 *
 * <p>Booking erases itself first because that leg cannot fail for a network reason and because it
 * holds the home address — a fan-out that dies half way should already have removed the worst of it.
 * Its own erasure is one transaction; the two HTTP calls are outside it, since holding a database
 * transaction open across two remote services would trade this problem for a worse one.
 *
 * <p><strong>A failed leg does not stop the next one.</strong> Refusing to try catalog because
 * messaging was unreachable would leave <em>more</em> of the customer's data in place, not less, and
 * every leg is idempotent, so there is nothing to unwind and no reason to be careful. What each leg
 * did is reported separately and {@link Receipt#complete()} is true only when all three succeeded.
 *
 * <h2>Report, do not retry, do not refuse</h2>
 *
 * <p>Three options and only one of them is honest. <em>Refusing</em> — rolling back booking's own
 * erasure when a remote leg fails — would make "erased everywhere or nowhere" true and would be
 * wrong: it delays a redaction the data subject has already asked for, on the strength of an outage
 * in a different service, and it cannot un-erase messaging if catalog is the leg that failed.
 * <em>Retrying</em> in process would make this endpoint's latency unbounded and would hide a real
 * outage behind a slow success, which is the shape of failure this repository keeps finding.
 * <em>Reporting</em> leaves the decision with the person who already owns it: the operator filed the
 * request, is holding the receipt, and re-running the whole fan-out is safe — the second pass finds
 * nothing under the original login and says so, in zeroes.
 *
 * <h2>Idempotent, including the references</h2>
 *
 * <p>The booking references are read back <strong>under the alias, after the local erasure</strong>,
 * not collected from the rows on the way past. That is the difference between a fan-out that can be
 * retried and one that cannot: a second run finds no bookings under the original login, so a
 * reference list gathered from that sweep would be empty, and the retry that exists precisely because
 * messaging failed the first time would call messaging with nothing — quietly reopening D36's
 * residual on exactly the path most likely to hit it. Reading by alias returns the same list every
 * time.
 */
@Component
public class ErasureFanout {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureFanout.class);

    /** Every leg of a complete erasure did what it was asked. */
    static final String ERASED = "ERASED";

    /** The leg was attempted and did not erase. {@link Leg#failure()} says what happened. */
    static final String FAILED = "FAILED";

    /**
     * The leg erased, and wrote a different alias than booking did.
     *
     * <p>Only one thing causes it: the three services are running different values of
     * {@code HEALTHCONNECT_PRIVACY_PEPPER}, which D35 requires to be identical and nothing until now
     * verified. It is worth a status of its own rather than being folded into {@code FAILED} because
     * the rows <em>were</em> redacted and re-running will not fix it — the operator needs the
     * deployment corrected, and then every row already written under the wrong alias reconciled by
     * hand, because there is no way back from a pseudonym to a login.
     */
    static final String ALIAS_MISMATCH = "ALIAS_MISMATCH";

    private final ErasureWorkflow erasure;
    private final BookingQueryRepository bookings;
    private final ErasureFanoutClient legs;
    private final FanoutTokenMinter tokens;

    public ErasureFanout(
        ErasureWorkflow erasure,
        BookingQueryRepository bookings,
        ErasureFanoutClient legs,
        FanoutTokenMinter tokens
    ) {
        this.erasure = erasure;
        this.bookings = bookings;
        this.legs = legs;
        this.tokens = tokens;
    }

    /**
     * Erases {@code login} here, in messaging and in catalog, and reports what each one did.
     *
     * <p>Never throws for a remote failure — see the class comment. It throws only if booking's own
     * erasure does, in which case nothing has been fanned out and the caller gets an ordinary 500.
     */
    public Receipt eraseEverywhere(String login) {
        ErasureWorkflow.Erased local = erasure.eraseCustomer(login);
        String alias = erasure.pseudonym(login);
        List<String> references = referencesUnder(alias);

        /* Minted once and used by both legs. Thirty seconds is the whole window it exists in, and it
           is minted after the local erasure rather than before so that a slow sweep here cannot spend
           the lifetime it will need for the calls. */
        String token = tokens.forErasureOf(login);

        List<Leg> outcome = new ArrayList<>();
        outcome.add(new Leg("booking", ERASED, counts(local), null));
        outcome.add(attempt("messaging", alias, () -> legs.eraseInMessaging(login, references, token)));
        outcome.add(attempt("catalog", alias, () -> legs.eraseInCatalog(login, token)));

        boolean complete = outcome.stream().allMatch(leg -> ERASED.equals(leg.status()));
        LOG.info("erasure fan-out for {} — complete={} references={} legs={}", alias, complete, references.size(), outcome);
        return new Receipt(alias, complete, references.size(), outcome);
    }

    /**
     * Every booking reference this customer has, read back under the alias the erasure just wrote.
     *
     * <p>{@code booking.customer_login} is indexed for this question — D34 added the index for the
     * erasure sweep itself and D36 identified the answer as the thing messaging cannot know.
     */
    private List<String> referencesUnder(String alias) {
        return bookings
            .findByCustomerLoginOrderByScheduledDateDesc(alias)
            .stream()
            .map(Booking::getReference)
            .filter(reference -> reference != null && !reference.isBlank())
            .distinct()
            .toList();
    }

    private Leg attempt(String service, String alias, Supplier<ErasureFanoutClient.LegReceipt> call) {
        ErasureFanoutClient.LegReceipt receipt;
        try {
            receipt = call.get();
        } catch (ErasureFanoutClient.LegFailed failed) {
            // Logged at warn rather than error: the fan-out did what it could, the receipt says so,
            // and the operator's next move is to run it again. An error line here would page somebody
            // for a condition a retry fixes.
            LOG.warn("erasure fan-out leg {} failed for {}: {}", service, alias, failed.getMessage());
            return new Leg(service, FAILED, Map.of(), failed.getMessage());
        }
        if (receipt.pseudonym() != null && !receipt.pseudonym().equals(alias)) {
            LOG.error(
                "erasure fan-out leg {} wrote alias {} where booking wrote {} — the services are running different peppers (decisions.md D35)",
                service,
                receipt.pseudonym(),
                alias
            );
            return new Leg(
                service,
                ALIAS_MISMATCH,
                receipt.counts(),
                service + " erased under a different alias — the estate's privacy pepper is not identical across services (decisions.md D35)"
            );
        }
        return new Leg(service, ERASED, receipt.counts(), null);
    }

    private static Map<String, Integer> counts(ErasureWorkflow.Erased local) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("bookingsErased", local.bookingsErased());
        counts.put("outboxPayloadsRedacted", local.outboxPayloadsRedacted());
        counts.put("disputesRedacted", local.disputesRedacted());
        counts.put("historyRowsReKeyed", local.historyRowsReKeyed());
        return counts;
    }

    /**
     * What one service did.
     *
     * @param service {@code booking}, {@code messaging} or {@code catalog}
     * @param status {@link #ERASED}, {@link #FAILED} or {@link #ALIAS_MISMATCH}
     * @param counts that service's own receipt, field by field. Empty for a leg that failed — a zero
     *     and an unknown must not be the same thing on the sheet somebody files
     * @param failure a sentence naming what went wrong, or null
     */
    public record Leg(String service, String status, Map<String, Integer> counts, String failure) {}

    /**
     * @param pseudonym the alias every service should now be carrying for this person
     * @param complete every leg erased. <strong>The field an operator reads first</strong>, and false
     *     is the answer this whole package exists to be able to give
     * @param bookingReferences how many of the customer's booking references were handed to messaging.
     *     Reported because it is the input to a redaction rather than a count of one, and a fan-out
     *     that suddenly hands over none has a bug in booking rather than in messaging
     * @param services one entry per leg, in the order they were attempted
     */
    public record Receipt(String pseudonym, boolean complete, int bookingReferences, List<Leg> services) {}
}
