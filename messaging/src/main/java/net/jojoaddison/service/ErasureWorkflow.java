package net.jojoaddison.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.MessagingQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasing a customer from the messaging service — {@code decisions.md} D24/D31.
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
 * the same deterministic pseudonym the booking service uses — {@code erased-<12 hex of SHA-256>} —
 * so one person's rows stay reconcilable across services without naming them.
 *
 * <p>Both directions are redacted, not just the customer's own messages. A professional's reply
 * quotes what it is replying to often enough that leaving one side intact would leave the other
 * side's content sitting in it.
 */
@Service
public class ErasureWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureWorkflow.class);

    static final String REDACTED_BODY = "[message erased at the customer's request]";

    private final MessagingQueryRepository conversations;
    private final MessageRepository messages;

    public ErasureWorkflow(MessagingQueryRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    /** Identical rule to booking's, so the same person carries the same alias in both. */
    public static String pseudonym(String login) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(login.getBytes(StandardCharsets.UTF_8));
            return "erased-" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable, which should not be possible", e);
        }
    }

    /**
     * Redacts this customer's messages and re-keys their conversations.
     *
     * <p>Returns <strong>both</strong> counts, and that is a correction rather than a nicety. It
     * returned the message count alone until a real erasure on the quality box answered
     * {@code messagesErased: 0} for a customer whose conversation it had just pseudonymised — the
     * booking had raised a thread that nobody had written in yet. An operator recording that zero
     * against a data subject request would conclude messaging held nothing for that person, when it
     * held a row keyed to their login. A receipt that under-reports what was done is worse than a
     * verbose one, because it is the thing somebody files.
     */
    @Transactional
    public Erased eraseCustomer(String login) {
        // professionalRef "" so the query's professional half matches nothing and only this
        // customer's own conversations come back.
        List<Conversation> mine = conversations.findVisibleTo(login, "");
        String alias = pseudonym(login);
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
        LOG.info("erased {} message(s) across {} conversation(s), now {}", redacted, mine.size(), alias);
        return new Erased(mine.size(), redacted);
    }

    /**
     * @param conversationsPseudonymised threads re-keyed to the pseudonym — can be non-zero while
     *     {@code messagesRedacted} is zero, which is exactly the case that made this record necessary
     * @param messagesRedacted message bodies replaced
     */
    public record Erased(int conversationsPseudonymised, int messagesRedacted) {}
}
