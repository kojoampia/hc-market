package net.jojoaddison.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.ErasedSubject;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Notification;
import net.jojoaddison.repository.ErasedSubjectRepository;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.MessagingQueryRepository;
import net.jojoaddison.repository.NotificationEraseRepository;
import net.jojoaddison.repository.SubjectLockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a customer from the messaging service — {@code decisions.md} D24/D31/D32.
 *
 * <h2>This is the service that holds the worst of it</h2>
 *
 * <p>D24 singles out {@code visitAddress} and {@code Message.body} as the sensitive fields, and the
 * message bodies are the harder half: <em>people will type health details into a message thread
 * whatever the schema intends</em>. A customer describing symptoms to a nutritionist is doing the
 * obvious thing, and no amount of scope note prevents it. So bodies are redacted outright rather
 * than trimmed or truncated.
 *
 * <p>The conversation survives with its reference and its {@code professionalRef}, so the
 * professional's own thread list does not develop holes, and the customer identity is replaced by
 * the same deterministic pseudonym the booking service uses — see {@link SubjectPseudonym} — so one
 * person's rows stay reconcilable across services without naming them.
 *
 * <p>Both directions are redacted, not just the customer's own messages. A professional's reply
 * quotes what it is replying to often enough that leaving one side intact would leave the other
 * side's content sitting in it.
 *
 * <h2>Notifications, including the ones addressed to somebody else</h2>
 *
 * <p>Two kinds, and the second is the one that was missed. Notifications <strong>to</strong> the
 * customer are re-keyed to the pseudonym, and their bodies are redacted along with them — not because
 * the current templates name the customer, but so that this method stops depending on the fact that
 * they do not. Notifications <strong>about</strong> the customer sit in
 * the <em>professional's</em> bell menu — {@code booking.requested} raises "Ama Mensah asked for a
 * home visit on 12 Sep", the customer's name in a row keyed to a different person's login. No query
 * by recipient returns those, which is exactly why they survived the first implementation. They are
 * found through {@code deepLink}, and their bodies are redacted while the row stays, so the
 * professional's timeline keeps its shape.
 *
 * <p><strong>And the deep links come from two places, not one — {@code decisions.md} D36.</strong>
 * Deriving them from the customer's conversations alone was wrong, because
 * {@code BookingEventConsumer.openThreadIfNone} dedupes threads <em>by professional</em>: a second
 * booking with the same person reuses the existing thread, so that booking's reference is never any
 * conversation's {@code bookingReference} and its professional-side notification was never found. The
 * customer's own notifications supply the rest — the customer's copy and the professional's copy of
 * one booking event carry the same {@code deepLink} — and the two sets are unioned before the lookup.
 *
 * <p><strong>And, since D38, from a third source when the caller supplies one.</strong> A booking
 * still <em>pending</em> when the erasure runs has raised a notification to the professional and none
 * to the customer, and — being a repeat with that professional — shares its thread with an earlier
 * booking, so nothing this service holds points at it. That was D36's residual, and this service
 * could not close it alone: it needs to know about a booking it has no thread for, which is a fact
 * only booking has. The erasure fan-out hands it over. {@link #eraseCustomer(String, java.util.List)}
 * takes the customer's booking references and folds {@code /bookings/<ref>} for each of them into the
 * same link set, so a fan-out erasure reaches the pending row and a single-service desk call still
 * does not. Both behaviours are pinned by tests, because the difference between them is now the
 * difference between a complete erasure and a nearly complete one.
 *
 * <p>The references decide which rows are redacted, so they are worth being precise about what they
 * can and cannot do. They only ever cause a body to be replaced — never a row to be read back, never
 * a row to be created, never a login to be disclosed — so the worst a wrong reference achieves is
 * blanking a notification that should have kept its text. The authority that carries them is narrowed
 * to one named customer for that reason rather than because a disclosure is possible.
 *
 * <h2>Two invariants the completeness of that union rests on</h2>
 *
 * <p><strong>Every notification about a person carries {@code /bookings/<ref>}</strong>, which is why
 * {@code BookingEventConsumer.raise} refuses to write a row without one and why every notification
 * must be raised through that method. The column is nullable and rows with no link exist — the seeder
 * writes them — so a row built inline, without a link, is invisible here for good.
 *
 * <p><strong>Notification rows are append-only.</strong> A "clear notifications" feature must set
 * {@code readAt} and never delete: the customer's own rows are one of the union's two sources, so
 * deleting them removes the only thing pointing at the professional's copy of the same event, and the
 * defect returns with a clean receipt and nothing red. Said again beside the endpoint that would grow
 * such a feature, in {@code MessagingResource}.
 *
 * <h2>What the counts count — {@code decisions.md} D39</h2>
 *
 * <p><strong>Rows that changed, never rows that matched.</strong> The distinction is invisible for
 * three of the four counts and decides the fourth. Conversations, messages and the customer's own
 * notifications are all found <em>by the customer's login</em>, which the first pass removes, so a
 * second pass matches nothing and reports zero without anything having to be careful.
 * {@code notificationsRedacted} is different in kind: those rows are found by {@code deepLink}, which
 * is not personal data and does not change when a body is redacted, so the same rows are matched
 * every time this runs. Counting the matches meant a retried fan-out erasure reported
 * {@code notificationsRedacted: 2} for ever, over bodies that had held nothing but the placeholder
 * since the first call.
 *
 * <p>The rule generalises, and it is the thing to check when a count is added here: <em>a counter
 * keyed on the login is self-clearing; a counter keyed on anything else has to compare before it
 * counts</em>.
 *
 * <h2>And erasure is now a standing fact, not a moment</h2>
 *
 * <p>See {@link ErasedSubject}. A sweep is only correct if nothing arrives afterwards, and things do
 * — a lagging {@code booking.requested} re-created a conversation under the original login seconds
 * after that login had been erased, with a clean receipt already filed. The pseudonym is recorded
 * here and {@code BookingEventConsumer} consults it before writing anything keyed to a person.
 */
@Service
public class ErasureWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureWorkflow.class);

    static final String REDACTED_BODY = "[message erased at the customer's request]";
    static final String REDACTED_NOTIFICATION = "[details erased at the customer's request]";

    /** The prefix every deep link this service raises carries — see {@code BookingEventConsumer.raise}. */
    static final String BOOKING_LINK_PREFIX = "/bookings/";

    private final MessagingQueryRepository conversations;
    private final MessageRepository messages;
    private final NotificationEraseRepository notifications;
    private final ErasedSubjectRepository erased;
    private final SubjectLockRepository locks;
    private final SubjectPseudonym pseudonyms;
    private final ErasureRegisterGuard guard;

    public ErasureWorkflow(
        MessagingQueryRepository conversations,
        MessageRepository messages,
        NotificationEraseRepository notifications,
        ErasedSubjectRepository erased,
        SubjectLockRepository locks,
        SubjectPseudonym pseudonyms,
        ErasureRegisterGuard guard
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.notifications = notifications;
        this.erased = erased;
        this.locks = locks;
        this.pseudonyms = pseudonyms;
        this.guard = guard;
    }

    /**
     * {@code erased-<16 hex>} — identical rule to booking's and catalog's, so the same person carries
     * the same alias in all three. An instance method since D35, because the pepper is configuration.
     */
    public String pseudonym(String login) {
        return pseudonyms.of(login);
    }

    /**
     * Takes the subject lock for the current transaction — see {@link SubjectLockRepository}.
     *
     * <p>Called by the consumer before it decides what login to store, and by the erasure before it
     * sweeps. Released when the transaction ends, however it ends.
     *
     * <p>A no-op without a pepper, for the reason {@link #isErased(String)} gives: no erasure can be
     * running to serialise against, so there is nothing to wait for. Throwing here instead would stop
     * the consumer processing every event that carries a customer — a broker backlog and a dark
     * notifications bell — over a variable that affects one desk endpoint.
     */
    public void lockSubject(String login) {
        if (pseudonyms.isConfigured()) {
            locks.lock(pseudonyms.lockKey(login));
        }
    }

    /**
     * Whether this login has already been erased. The only question the register can answer.
     *
     * <p><strong>Not the question the consumer asks — {@link #covers(String, Instant)} is.</strong>
     * Since D37 an erasure is scoped to what existed when it ran, so "has this person ever been
     * erased" no longer decides what may be stored about them; it is kept because it is the honest
     * unscoped question and because the desk's tests ask it.
     *
     * <p><strong>Answers {@code false} when no pepper is configured</strong>, rather than throwing and
     * taking the booking-event consumer down with it. That is safe here and only here: without a
     * pepper an erasure refuses with 503, so nothing can have been recorded under this configuration,
     * and {@code ErasureRegisterGuard} aborts the context refresh if the register already holds rows.
     * So by the time this line runs unpeppered, the register is provably empty and {@code false} is
     * the true answer rather than a guess.
     *
     * <p><strong>That last sentence depends entirely on when the guard runs, and it was untrue until
     * the guard became a {@code SmartLifecycle}.</strong> As an {@code ApplicationRunner} it fired
     * after the refresh, and the Kafka listener container starts <em>during</em> it — so on an
     * unpeppered service with rows in the register this method really did run, really did answer
     * {@code false} about somebody it had erased, and the consumer wrote their login back before the
     * guard got its turn to object. The guard is now phased below every other lifecycle bean for that
     * reason; the claim above holds because nothing can consume an event before it has decided. See
     * {@code ErasureRegisterGuard} and {@code decisions.md} D35.
     *
     * <p><strong>And it holds for this process only.</strong> The guard decides once, at startup, so
     * an unpeppered instance sharing a database with a peppered one passed while the register was
     * empty and would answer {@code false} for ever afterwards, including about everybody the sibling
     * erased in the meantime. That is why the unpeppered branch below asks the guard rather than
     * simply returning: {@code assertRegisterStillEmpty} re-reads the register at most once every
     * thirty seconds, so the claim is re-established periodically instead of being assumed for the
     * lifetime of the process. A peppered service never reaches that line and runs no extra query.
     */
    @Transactional(readOnly = true)
    public boolean isErased(String login) {
        if (login == null || login.isBlank()) {
            return false;
        }
        if (!pseudonyms.isConfigured()) {
            guard.assertRegisterStillEmpty();
            return false;
        }
        return erased.existsById(pseudonym(login));
    }

    /**
     * Whether an erasure of this person covers a booking raised at this instant — {@code decisions.md}
     * D37, backlog WP-08.
     *
     * <h2>The register is not a permanent verdict on a person</h2>
     *
     * <p>Erasure does not touch the gateway's user store, so an erased customer can log in and book
     * again, and D37 says what that is: <em>a booking made after the erasure is stored under the real
     * login, and everything that existed before it stays pseudonymised.</em> They have chosen a new
     * relationship, and the erasure covered what existed when it ran. Without this, messaging
     * pseudonymised the new booking's thread while booking and catalog stored the real login — the
     * estate disagreeing with itself about whether somebody exists.
     *
     * <h2>The comparison is against the BOOKING's age, never the event's</h2>
     *
     * <p>{@code bookingRaisedAt} is {@code Booking.raisedAt}, put on the outbox payload by booking's
     * {@code OutboxRecorder} — when the booking was <em>created</em>, written once and never moved by a
     * transition. The envelope's {@code occurredAt} is a different fact and would give a different and
     * wrong answer: a booking that was still open when the erasure ran keeps emitting events afterwards
     * — accepted, completed, cancelled — every one of them stamped after {@code erasedAt}, so comparing
     * the event's own timestamp would write the customer's real login and name back onto an erased
     * booking one lifecycle step at a time. It would also break D36's guarantee that its residual does
     * not grow, which rests on exactly those later events staying pseudonymised.
     *
     * <h2>Absent, blank or unreadable means covered</h2>
     *
     * <p>A null {@code bookingRaisedAt} answers the same as the unconditional check this replaced.
     * That matters twice over. Events published before this field existed are still in outboxes and on
     * the broker, and the events this consumer handles that are not about one booking's creation carry
     * whatever booking sent; in both cases the safe direction is the erasure, because failing to
     * pseudonymise puts a real login into a row that nothing will ever revisit, while pseudonymising a
     * booking that need not have been costs one thread its customer's name.
     *
     * <p>Equality counts as covered for the same reason — a booking raised in the same instant as the
     * erasure is not somebody choosing to come back.
     *
     * <h2>This makes no new reader of a register, which is what WP-08 had to check</h2>
     *
     * <p>D39 left booking's and catalog's {@code erased_subject} registers written and never read, and
     * noted that whichever service WP-08 turned into a reader would have to decide whether it needed
     * messaging's {@code ErasureRegisterGuard} — an unpeppered service consulting a register answers
     * "not erased" about people it erased, which is the failure D35 exists to prevent. It is still
     * messaging, and messaging has had that guard since D35: booking only publishes a column it already
     * stores and reads nothing. So the two write-only registers stay write-only and stay unguarded.
     */
    @Transactional(readOnly = true)
    public boolean covers(String login, Instant bookingRaisedAt) {
        if (login == null || login.isBlank()) {
            return false;
        }
        if (!pseudonyms.isConfigured()) {
            // Same reasoning as isErased: unpeppered, the register is provably empty, and the guard
            // re-establishes that rather than assuming it for the life of the process.
            guard.assertRegisterStillEmpty();
            return false;
        }
        return erased
            .findById(pseudonym(login))
            .filter(subject -> bookingRaisedAt == null || !bookingRaisedAt.isAfter(subject.getErasedAt()))
            .isPresent();
    }

    /**
     * Redacts this customer's messages and notifications, re-keys their conversations, and records
     * that it happened.
     *
     * <p>Returns every count, and that is a correction rather than a nicety. It returned the message
     * count alone until a real erasure on the quality box answered {@code messagesErased: 0} for a
     * customer whose conversation it had just pseudonymised — the booking had raised a thread that
     * nobody had written in yet. An operator recording that zero against a data subject request would
     * conclude messaging held nothing for that person, when it held a row keyed to their login. A
     * receipt that under-reports what was done is worse than a verbose one, because it is the thing
     * somebody files.
     *
     * <p>The form that is told nothing about bookings this service holds no thread for, so it leaves
     * D36's residual exactly where D36 left it. Behaviourally identical to what a direct desk call
     * gets: the resource always calls the overload below and supplies an empty list when no fan-out
     * payload arrived, rather than branching between two methods over the same distinction.
     */
    @Transactional
    public Erased eraseCustomer(String login) {
        return eraseCustomer(login, List.of());
    }

    /**
     * The same, told which bookings the customer has — {@code decisions.md} D38.
     *
     * <p>Booking is authoritative for that list and {@code booking.customer_login} has been indexed
     * for the question since D34, so the erasure fan-out can hand it over for the cost of one query
     * it was going to run anyway. What it buys is the row D36 could not reach: a booking that was
     * still pending when the erasure ran has a professional-side notification and nothing keyed to the
     * customer pointing at it, and no query this service can write will find it.
     *
     * <p>These references are <em>added</em> to the two sources the union already had rather than
     * replacing them. That is not belt-and-braces. Booking's list is authoritative for bookings, and
     * messaging raises notifications for things that are not bookings — the fan-in {@code
     * notification.raised} events that D36 warns the consumer's {@code default} branch currently
     * swallows are the obvious future case — so a link set built from the references alone would be a
     * new hole the day somebody adds one.
     *
     * @param bookingReferences every booking reference the customer has, or empty when the caller has
     *     no way to know. Never null.
     */
    @Transactional
    public Erased eraseCustomer(String login, List<String> bookingReferences) {
        String alias = pseudonym(login);

        /* Serialised against the consumer for the rest of this transaction. Recording the pseudonym
           first is NOT sufficient on its own: under READ_COMMITTED the register row stays invisible
           to a concurrent consumer until this commits, so without the lock an event in flight still
           writes the original login and this sweep cannot see the row it wrote. See
           SubjectLockRepository. */
        lockSubject(login);

        /* Written once. A re-run must not move erasedAt — that timestamp is the one fact an audit of
           an irreversible action will ask for, and save() on an existing primary key would overwrite
           it with the date of whoever ran the erasure a second time. */
        if (!erased.existsById(alias)) {
            erased.save(new ErasedSubject(alias, Instant.now()));
        }

        // professionalRef "" so the query's professional half matches nothing and only this
        // customer's own conversations come back.
        List<Conversation> mine = conversations.findVisibleTo(login, "");
        int redacted = 0;
        for (Conversation c : mine) {
            /* findMessages(id), not findAll().filter(...). The repository already answers this
               question, and loading every message in the service to redact one thread's would be a
               table scan per erasure — on the one table most likely to be the largest here. */
            for (Message m : conversations.findMessages(c.getId())) {
                m.setBody(REDACTED_BODY);
                messages.save(m);
                redacted++;
            }
            c.setCustomerLogin(alias);
            conversations.save(c);
        }

        /* Deep links, collected from BOTH sides — decisions.md D36. A conversation names only the
           booking that opened it, so the customer's own notifications are the other half; the
           customer's copy and the professional's copy of one booking event share a deepLink, which
           is what makes this bridge work. Collected here, in the re-keying loop, because these rows
           are being visited anyway and one pass is cheaper than a second query. Re-keying does not
           touch deepLink, so reading it before or after the setter is the same value. */
        Set<String> links = new LinkedHashSet<>();
        for (Conversation c : mine) {
            addLink(links, c.getBookingReference());
        }
        /* The third source — decisions.md D38. Every booking the customer has, as booking knows them,
           including the ones that never opened a thread of their own. Empty for a direct desk call,
           which is why the residual is still pinned by a test for that path. */
        for (String reference : bookingReferences) {
            addLink(links, reference);
        }

        int reKeyed = 0;
        for (Notification n : notifications.addressedTo(login)) {
            String link = n.getDeepLink();
            if (identifiesOneBooking(link)) {
                links.add(link);
            }
            n.setRecipientLogin(alias);
            /* The body goes too, and that is a decoupling rather than an extra precaution —
               decisions.md D36. Leaving it made this method's correctness depend on every
               customer-facing template happening not to name the customer: "Your strength session on
               26 Sep is confirmed" does not, "Hi Ama, your strength session…" does, and the day one
               greets by name the erasure would re-key the row and leave the name sitting in it under
               an alias, for ever, with every test still green. Nothing reads these rows — the
               recipient no longer exists — so there is nothing on the other side of the scale.
               Counted as re-keyed rather than as redacted: notificationsRedacted keeps its meaning
               of "in somebody else's list", and one row must not appear in two counts. */
            n.setBody(REDACTED_NOTIFICATION);
            notifications.save(n);
            reKeyed++;
        }

        /* The ones sitting in somebody else's bell menu. Bodies only: the row belongs to the
           professional, and deleting it would take a real event out of their history to remove a
           name that can be removed on its own. */
        int aboutThem = 0;
        if (!links.isEmpty()) {
            for (Notification n : notifications.linkedToAny(List.copyOf(links))) {
                if (alias.equals(n.getRecipientLogin())) {
                    continue; // already re-keyed AND redacted above; counting it here would double-count it
                }
                if (REDACTED_NOTIFICATION.equals(n.getBody())) {
                    /* An earlier pass already took this body — decisions.md D39. Skipped rather than
                       re-written, and the count is the reason: these rows are matched by deep_link,
                       which does not change when the body does, so every later erasure of the same
                       customer found them again and reported the number it had just re-written. The
                       operator's instruction after a 502 is to call this again, and a retry answering
                       "2 notifications redacted" tells them data was still exposed when it was not. A
                       count on a receipt filed against a legal request has to mean rows that stopped
                       naming the person, not rows the sweep visited. */
                    continue;
                }
                n.setBody(REDACTED_NOTIFICATION);
                notifications.save(n);
                aboutThem++;
            }
        }

        LOG.info(
            "erased {} message(s) across {} conversation(s), re-keyed {} notification(s) and redacted {} about them, now {}",
            redacted,
            mine.size(),
            reKeyed,
            aboutThem,
            alias
        );
        return new Erased(mine.size(), redacted, reKeyed, aboutThem);
    }

    /**
     * @param conversationsPseudonymised threads re-keyed to the pseudonym — can be non-zero while
     *     {@code messagesRedacted} is zero, which is exactly the case that made this record necessary
     * @param messagesRedacted message bodies replaced
     * @param notificationsReKeyed notifications addressed to the customer, now addressed to the alias
     *     and with their bodies redacted — one row counts here or in {@code notificationsRedacted},
     *     never in both
     * @param notificationsRedacted notifications in somebody else's list whose body named the customer
     *     — and whose body this run actually replaced. A row an earlier run already redacted is
     *     matched again, because the match is on {@code deepLink}, and is deliberately not counted
     *     again: see the class comment
     */
    public record Erased(int conversationsPseudonymised, int messagesRedacted, int notificationsReKeyed, int notificationsRedacted) {}

    /** {@code /bookings/<ref>} — the shape every deep link this service raises has. */
    private static void addLink(Set<String> links, String bookingReference) {
        if (bookingReference != null && !bookingReference.isBlank()) {
            links.add(BOOKING_LINK_PREFIX + bookingReference);
        }
    }

    /**
     * Whether a deep link identifies <em>one</em> booking, and may therefore go into the {@code IN}
     * set — {@code decisions.md} D36.
     *
     * <p>Null and blank are the obvious exclusions: a blank would match every notification that has
     * no deep link at all, and rows with no deep link exist — {@code MessagingSeeder} writes them.
     * The bare {@code /bookings/} is the exclusion worth explaining. It is what
     * {@code "/bookings/" + bookingRef} produces from a blank reference, it is neither null nor
     * blank so it survives every simple check, and in the {@code IN} clause it matches every
     * <em>other</em> malformed row in the table irrespective of whose booking it was — overwriting
     * strangers' bodies while the receipt reports a larger, entirely plausible count.
     *
     * <p>{@code BookingEventConsumer.raise} now refuses to write one, which closes the source. This
     * is the other half, because a row written before it refused is still in the table and no
     * migration goes looking for it.
     */
    private static boolean identifiesOneBooking(String deepLink) {
        return deepLink != null && !deepLink.isBlank() && !BOOKING_LINK_PREFIX.equals(deepLink.strip());
    }
}
