package net.jojoaddison.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The credential one hc-market service presents to another so that a single operator action erases a
 * customer everywhere — {@code decisions.md} D37 and D38.
 *
 * <h2>Why a minted token at all</h2>
 *
 * <p>A complete erasure is three redactions in three services, and until D38 it was three separate
 * desk calls with three separate receipts, each of which looks like a success on its own. There was
 * no orchestrator because there was no way for one service to call another as anything but an
 * anonymous stranger: D28 says so in as many words, and catalog's {@code /internal/**} exists in the
 * shape it does precisely because booking holds no credential.
 *
 * <p>D37 supplies the mechanism, and corrected the question that was put to it. The proposal was to
 * sequence the erasure from the {@code hc-admin} desk, on the grounds that its staff token is
 * accepted across the estate. It is not: the platform-wide signing secret in
 * {@code ~/webroot/01-healthconnect/.env} is shared by hc-admin, hc-patient and hc-professional, and
 * <strong>hc-market is not in that set</strong> — it carries its own {@code JWT_BASE64_SECRET},
 * generated per estate, and an hc-admin token fails signature validation here. What hc-market does
 * have is a key its own five services already share, so one of them can mint a token the others
 * accept exactly as they accept a user's.
 *
 * <h2>What this does and does not widen</h2>
 *
 * <p>Any service holding the estate key can already mint a token for any subject with any authority,
 * including {@code ROLE_BROKERAGE}. That is a property of a shared symmetric key and it was true
 * before this file existed; making the capability deliberate does not enlarge it. What would enlarge
 * it is a fan-out credential that means "a service may call anything", so this one is narrowed three
 * ways and each narrowing is enforced on the <em>accepting</em> side rather than promised by the
 * minting one:
 *
 * <ol>
 *   <li><strong>One authority, used by nothing else.</strong>
 *       {@link MarketplaceAuthorities#CUSTOMER_ERASURE} appears on exactly one endpoint per service —
 *       the erasure desk — and on no other. It is not {@code ROLE_BROKERAGE}: that authority also
 *       resolves disputes, moves money and decides who wears the verification badge.
 *   <li><strong>One named customer.</strong> The token carries {@link #SUBJECT_CLAIM}, and a caller
 *       holding only the fan-out authority may erase that login and no other. A token minted to erase
 *       one person cannot be replayed against a second.
 *   <li><strong>Thirty seconds.</strong> {@link #LIFETIME}. A fan-out token should not outlive the
 *       request that needed it, and the receiving side checks the span between {@code iat} and
 *       {@code exp} rather than trusting the issuer to have been careful.
 * </ol>
 *
 * <p>The subject is {@link #SUBJECT} — deliberately not the operator's login. A leaked fan-out token
 * would otherwise be a bearer credential for a real person on every {@code /api/**} path in the
 * estate that only asks to be authenticated, which is most of them. {@code system:erasure-fanout}
 * matches no user in any store, so what it can read elsewhere is one person's empty thread list.
 *
 * <h2>Copied verbatim, like {@code SubjectPseudonym}</h2>
 *
 * <p>This file is <strong>byte-identical in booking, catalog and messaging</strong>, and CI diffs the
 * three copies. There is no shared library here — five standalone Maven projects with no aggregator
 * pom — and a claim name that drifts by one character between the minting service and the accepting
 * one turns the fan-out into a 403 that reads as a permissions problem. Booking uses only the
 * constants, because booking mints and never accepts; the check below is dead code in that copy on
 * purpose, so that the three files can be compared as bytes rather than as behaviour.
 */
public final class ErasureFanoutToken {

    /**
     * The {@code sub} of a fan-out token. Not a person, and not resolvable to one.
     */
    public static final String SUBJECT = "system:erasure-fanout";

    /**
     * The login this token authorises the erasure of, and only this one.
     */
    public static final String SUBJECT_CLAIM = "erasure_subject";

    /**
     * How long a fan-out token may live. Long enough for three redaction sweeps behind one operator
     * action, short enough that it is worthless by the time anything could be done with a copy of it.
     */
    public static final Duration LIFETIME = Duration.ofSeconds(30);

    private ErasureFanoutToken() {}

    /**
     * Whether the caller in the current security context may erase {@code login}.
     *
     * <p>{@code ROLE_BROKERAGE} may erase anybody: that is the desk, and a person has already
     * identity-checked the request off-system before calling it. The fan-out authority may erase the
     * one login its token names, for as long as that token is inside its stated lifetime.
     *
     * <p>Fails closed on every unexpected shape — no authentication, a principal that is not a JWT, a
     * token with no {@code iat} — because the alternative is a permissive branch guarding an
     * irreversible action.
     */
    public static boolean mayErase(String login) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || login == null || login.isBlank()) {
            return false;
        }
        boolean fanout = false;
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (MarketplaceAuthorities.BROKERAGE.equals(granted.getAuthority())) {
                return true;
            }
            fanout |= MarketplaceAuthorities.CUSTOMER_ERASURE.equals(granted.getAuthority());
        }
        if (!fanout || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return login.equals(jwt.getClaimAsString(SUBJECT_CLAIM)) && withinLifetime(jwt);
    }

    /**
     * The "short-lived" half of the contract, checked where it can be enforced.
     *
     * <p>The signing key is shared, so a service that has been taken over can mint whatever it likes
     * and this stops none of that. What it does stop is the ordinary way the constraint would decay:
     * a later caller — another service, a script, a rewritten minter — issuing a fan-out token with a
     * user token's twenty-four hours in it, which would be accepted for a day by every service in the
     * estate and would look exactly like this one.
     */
    private static boolean withinLifetime(Jwt jwt) {
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        return issued != null && expires != null && !Duration.between(issued, expires).minus(LIFETIME).isPositive();
    }
}
