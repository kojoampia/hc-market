package net.jojoaddison.service;

import java.time.Instant;
import java.util.List;
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
 * customer are re-keyed to the pseudonym. Notifications <strong>about</strong> the customer sit in
 * the <em>professional's</em> bell menu — {@code booking.requested} raises "Ama Mensah asked for a
 * home visit on 12 Sep", the customer's name in a row keyed to a different person's login. No query
 * by recipient returns those, which is exactly why they survived the first implementation. They are
 * found through {@code deepLink}, and their bodies are redacted while the row stays, so the
 * professional's timeline keeps its shape.
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
     */
    @Transactional
    public Erased eraseCustomer(String login) {
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

        int reKeyed = 0;
        for (Notification n : notifications.addressedTo(login)) {
            n.setRecipientLogin(alias);
            notifications.save(n);
            reKeyed++;
        }

        /* The ones sitting in somebody else's bell menu. Bodies only: the row belongs to the
           professional, and deleting it would take a real event out of their history to remove a
           name that can be removed on its own. */
        int aboutThem = 0;
        List<String> links = mine
            .stream()
            .map(Conversation::getBookingReference)
            .filter(ref -> ref != null && !ref.isBlank())
            .map(ref -> "/bookings/" + ref)
            .toList();
        if (!links.isEmpty()) {
            for (Notification n : notifications.linkedToAny(links)) {
                if (alias.equals(n.getRecipientLogin())) {
                    continue; // already re-keyed above, and its body names nobody
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
     * @param notificationsRedacted notifications in somebody else's list whose body named the customer
     */
    public record Erased(int conversationsPseudonymised, int messagesRedacted, int notificationsReKeyed, int notificationsRedacted) {}
}
