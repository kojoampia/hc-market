package net.jojoaddison.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The alias an erased customer's rows carry instead of their login — {@code decisions.md} D24/D32/D35.
 *
 * <h2>THIS FILE IS COPIED VERBATIM INTO booking, catalog AND messaging</h2>
 *
 * <p>All three must produce the <strong>same alias for the same person</strong>, because that is what
 * makes one erased customer one subject across the estate: messaging's {@code erased_subject} register
 * is keyed by it, and reconciling a redacted booking against a payout ledger row is only possible if
 * the two services agree on the string. There is no shared library here — five standalone Maven
 * projects, no aggregator pom (D6) — so the file is duplicated, and CI's {@code consistency} job
 * asserts the three copies are byte-identical rather than trusting anyone to keep them in step. Three
 * copy-pasted static methods drifting apart is exactly the failure that check exists to prevent, and
 * it would be silent: each service would keep working, and only a cross-service lookup would miss.
 *
 * <p>That duplication is also why this carries {@link #lockKey(String)}, which only messaging calls.
 * A method present in two services and used by neither costs nothing; a file that is <em>almost</em>
 * identical in three places costs the check.
 *
 * <h2>HMAC with a per-estate pepper, not a bare digest</h2>
 *
 * <p>This used to be {@code "erased-" + first 12 hex of SHA-256(login)}, and its javadoc claimed the
 * result was "not reversible without already knowing the login". That was wrong in the way that
 * matters. Logins here are short and guessable — first name, a dot, a surname — so anyone with read
 * access to any of the three databases can hash a candidate list offline, match the results against
 * the stored {@code erased-…} values, and re-identify every redacted row. Against messaging's register
 * the same attack answers a worse question: <em>was this named person erased</em>, which is itself the
 * fact erasure was asked to remove.
 *
 * <p>An HMAC keyed by a secret the databases do not contain closes that. Without the pepper the
 * candidate attack has nothing to compute against, and the pepper lives where {@code JWT_BASE64_SECRET}
 * lives — injected into the environment, absent from every committed configuration file that a real
 * deployment loads, because this repository is public.
 *
 * <p>The alias is 16 hex characters rather than the original 12. The column is {@code varchar(64)}, so
 * widening is free, and it was done in the same change as the derivation because both invalidate every
 * alias already written — one migration consequence rather than two. 64 bits of a keyed MAC makes an
 * accidental collision between two logins a non-question.
 *
 * <h2>What happens when the pepper is absent, and why it is not a startup failure</h2>
 *
 * <p><strong>The service starts; the derivation refuses.</strong> Every call to {@link #of(String)}
 * throws, so an erasure returns 503 and no unpeppered alias can reach a database — which is the
 * property that had to hold, because an alias is written into rows in place and there is no way back
 * from having written the wrong one.
 *
 * <p>Refusing to <em>start</em> was the alternative and it is worse here. It converts a missing privacy
 * secret into an outage of booking, catalog and messaging — the marketplace stops taking bookings, the
 * professional's inbox goes dark — over a value that one desk endpoint and one consumer branch read.
 * A blast radius that wide has a predictable outcome: somebody puts a plausible value in to get the
 * estate up, and a pepper chosen under that pressure is the committed-default failure arriving by
 * another route. Failing at the operation instead puts the error in front of the one person whose
 * request cannot proceed, at the moment they make it, naming the variable to set.
 *
 * <p>The startup log is an {@code ERROR} rather than a warning for the same reason: nothing else about
 * an unpeppered service looks wrong. It serves, it is healthy, and it stays that way until a data
 * subject request arrives — which may be months.
 *
 * <p>Messaging has one further guard, because it is the only service holding a register of who has
 * been erased: {@code ErasureRegisterGuard} refuses to start if that register has rows and no pepper
 * is set. Once somebody has actually been erased, running unpeppered is not a degraded desk, it is a
 * service that will answer "no" to {@code isErased} for a person it erased and go on writing their
 * login into new rows.
 */
@Component
public class SubjectPseudonym {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectPseudonym.class);

    /** Obviously deliberate when read in a database, and not a login anyone could hold. */
    private static final String PREFIX = "erased-";

    /** 64 bits of the MAC. The column is varchar(64); the limit is legibility, not storage. */
    private static final int HEX_LENGTH = 16;

    private static final String ALGORITHM = "HmacSHA256";

    private final String pepper;

    /**
     * @param pepper {@code healthconnect.privacy.pepper}, defaulting to empty. The default is empty
     *     rather than absent so the context still starts — see the class comment — and empty rather
     *     than a value because a committed pepper in a public repository is not a pepper.
     */
    public SubjectPseudonym(@Value("${healthconnect.privacy.pepper:}") String pepper) {
        this.pepper = pepper == null ? "" : pepper.trim();
        if (this.pepper.isEmpty()) {
            LOG.error(
                "privacy: no HEALTHCONNECT_PRIVACY_PEPPER is set, so erasure will refuse with 503. " +
                "Nothing else about this service is affected, and nothing else will report it (decisions.md D35)"
            );
        }
    }

    /** Whether an erasure can proceed at all. Checked by the desk so the refusal is a 503, not a 500. */
    public boolean isConfigured() {
        return !pepper.isEmpty();
    }

    /**
     * The alias for {@code login} — {@code erased-<16 hex of HMAC-SHA256(pepper, login)>}.
     *
     * <p>Deterministic, and that is deliberate: one person's rows stay grouped for accounting and
     * audit without naming them, where a fresh random value per row would make the booking history of
     * a single erased customer impossible to reconcile against a payout.
     *
     * @throws IllegalStateException if no pepper is configured. Deliberately not a fallback to an
     *     unpeppered digest: an alias is written into rows in place, so a wrong one cannot be taken
     *     back, and a re-identifiable alias filed against a completed erasure request is the failure
     *     this whole mechanism exists to prevent.
     */
    public String of(String login) {
        return PREFIX + HexFormat.of().formatHex(mac(login)).substring(0, HEX_LENGTH);
    }

    /**
     * The advisory-lock key for a subject — see messaging's {@code SubjectLockRepository}.
     *
     * <p>The first eight bytes of the same MAC the alias is built from, so an erasure and a concurrent
     * consumer compute the same number without either sending a login to the database as a query
     * parameter, where it would land in {@code pg_stat_activity} and the slow-query log.
     *
     * <p>Peppered like the alias, which is not about secrecy — the key never leaves the transaction —
     * but about there being one derivation in this class rather than two that could disagree.
     */
    public long lockKey(String login) {
        byte[] mac = mac(login);
        long key = 0;
        for (int i = 0; i < 8; i++) {
            key = (key << 8) | (mac[i] & 0xffL);
        }
        return key;
    }

    private byte[] mac(String login) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "healthconnect.privacy.pepper is not set, so no subject alias can be derived. " +
                "Set HEALTHCONNECT_PRIVACY_PEPPER — the same value in booking, catalog and messaging (decisions.md D35)"
            );
        }
        try {
            Mac hmac = Mac.getInstance(ALGORITHM);
            hmac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return hmac.doFinal(login.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable, which should not be possible", e);
        }
    }
}
