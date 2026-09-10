package net.jojoaddison.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The credential booking presents to the gateway to learn one customer's email — {@code decisions.md}
 * D72 §3 and D74.
 *
 * <h2>Why a second contract beside {@code ErasureFanoutToken}</h2>
 *
 * <p>D38 gave this estate one service-to-service credential and one authority to go with it, and the
 * whole of what makes {@code ROLE_CUSTOMER_ERASURE} honest is that it appears on exactly one endpoint
 * per service and permits exactly one act. Reusing it to read an email would have made that sentence
 * false in the worst available way: a token minted by a method called {@code forErasureOf} would be
 * spent on something that is not an erasure, and the scope everybody believes in would be a Java
 * method name rather than anything a receiving service checks. <strong>A scope that is a method name
 * is not a scope.</strong>
 *
 * <p>So the mechanism is copied and the authority is not. The three narrowings are D38's, restated
 * here rather than referenced, because a reader arriving at this file needs them and because the two
 * files are deliberately not one:
 *
 * <ol>
 *   <li><strong>One authority, used by nothing else.</strong> {@link #AUTHORITY} is granted by no
 *       login and appears on one endpoint in one service — the gateway's
 *       {@code /internal/customers/&#123;login&#125;/email}. It is not {@code ROLE_ADMIN}, which reads
 *       and writes every account in the estate, and it is not {@code ROLE_CUSTOMER_ERASURE}, which
 *       erases people.
 *   <li><strong>One named customer.</strong> The token carries {@link #SUBJECT_CLAIM}, and a caller
 *       holding it may read that login's email and no other. A token minted while one person pays
 *       cannot be replayed to enumerate everybody else.
 *   <li><strong>Thirty seconds.</strong> {@link #LIFETIME}, and the <em>accepting</em> side checks the
 *       span between {@code iat} and {@code exp} rather than trusting the issuer to have been
 *       careful — which is the only half of this that a compromised minter cannot simply skip.
 * </ol>
 *
 * <h2>Why the authority alone is not enough, and it is measurable</h2>
 *
 * <p>{@code POST /api/admin/authorities} creates an authority by name and {@code UserResource}
 * assigns it, both behind {@code ROLE_ADMIN}. So an administrator can grant {@link #AUTHORITY} to a
 * real person, and that person's ordinary twenty-four-hour login token would satisfy a filter chain
 * that asked for nothing else. The subject check is what stops it: {@link #SUBJECT} matches no user
 * in any store, so a token that authenticates a person is refused here however it is decorated.
 * {@code InternalCustomerContactResourceIT} mints exactly that token and asserts the 403.
 *
 * <h2>{@code ROLE_BROKERAGE} is deliberately NOT a bypass</h2>
 *
 * <p>{@code ErasureFanoutToken.mayErase} lets the brokerage desk erase anybody, because erasure is a
 * desk screen a person operates after an identity check off-system. There is no screen anywhere in
 * this estate that shows a customer's email address, nobody has asked for one, and D45's rule about
 * the provider list applies: an endpoint exists because a screen needs it. Adding the bypass would
 * turn a machine-to-machine lookup into a staff-readable contact directory, which is a disclosure
 * decision nobody has taken.
 *
 * <h2>The authorities are read from the claim, not from the granted authorities</h2>
 *
 * <p>Deliberate, and not a shortcut. The mapping from the {@code auth} claim to Spring's
 * {@code GrantedAuthority} set is configured by {@code authorities-claim-name: auth} in
 * {@code config/application.yml} — a <em>generated</em> file, which a regeneration rewrites and whose
 * test copy shadows it, and which is already on CLAUDE.md's restore table for other reasons. Reading
 * the claim here means this check cannot be undone by losing that line; losing it fails the filter
 * chain's {@code hasAuthority} closed, which is the right direction.
 *
 * <h2>Copied verbatim into booking, like {@code ErasureFanoutToken}</h2>
 *
 * <p>This file is <strong>byte-identical in the gateway and booking</strong>, and CI diffs the copies
 * against the gateway's — the accepting side is the reference, because it is where the contract is
 * enforced rather than merely promised. There is no shared library here (five standalone Maven
 * projects, no aggregator pom), and a claim name that drifts by one character between the minting
 * service and the accepting one turns a payment into a 403 that reads as a permissions problem.
 * Booking uses only the constants; {@link #mayRead} is dead code in that copy on purpose, so the two
 * files can be compared as bytes rather than as behaviour.
 *
 * <h2>No {@code aud}, and that is an argument rather than an omission</h2>
 *
 * <p>An audience claim nothing validates is theatre — {@code FanoutTokenMinter}'s note on {@code iss}
 * says as much. Validating one here would buy nothing today: no other service grants
 * {@link #AUTHORITY} anything, so the authority already is the audience. The day a second acceptor
 * appears, an {@code aud} check goes in with it and this paragraph is the reason it was not there
 * first.
 */
public final class ContactLookupToken {

    /**
     * The {@code sub} of a contact-lookup token. Not a person, and not resolvable to one.
     *
     * <p>Same reasoning as {@code ErasureFanoutToken.SUBJECT}: the estate signing key is shared, so a
     * token minted with a real login as its subject would be a bearer credential for that person on
     * every {@code /api/**} path that only asks to be authenticated — which is most of them.
     * {@code system:contact-lookup} matches no user in any store.
     */
    public static final String SUBJECT = "system:contact-lookup";

    /**
     * The login this token authorises a contact lookup for, and only this one.
     */
    public static final String SUBJECT_CLAIM = "contact_subject";

    /**
     * Read one named customer's contact details, and nothing else.
     *
     * <p>Named for what it permits rather than for the mechanism, exactly as
     * {@code MarketplaceAuthorities.CUSTOMER_ERASURE} is. It lives on this class rather than in
     * {@code MarketplaceAuthorities} for one reason: that file is per-service and differs between
     * booking, catalog and messaging, and the gateway has none at all — a byte-identical contract
     * cannot depend on a file that only exists on one side of it.
     */
    public static final String AUTHORITY = "ROLE_CUSTOMER_CONTACT_READ";

    /**
     * How long a contact-lookup token may live. Long enough for one HTTP call made while a customer
     * waits on a payment screen, short enough that it is worthless by the time anything could be done
     * with a copy of it.
     */
    public static final Duration LIFETIME = Duration.ofSeconds(30);

    private ContactLookupToken() {}

    /**
     * Whether {@code jwt} may read {@code login}'s contact details.
     *
     * <p>A pure function of the token and the login asked about — no security context, no reactive
     * context, no {@code Authentication}. That is what lets one file serve a servlet minter and a
     * reactive acceptor, and it is what makes every branch below testable without a container.
     *
     * <p>Fails closed on every unexpected shape — no token, a subject that is a person, a missing
     * {@code iat}, an authority claim that is not a string — because the alternative is a permissive
     * branch in front of somebody's email address.
     */
    public static boolean mayRead(Jwt jwt, String login) {
        if (jwt == null || login == null || login.isBlank()) {
            return false;
        }
        if (!SUBJECT.equals(jwt.getSubject())) {
            return false;
        }
        if (!login.equals(jwt.getClaimAsString(SUBJECT_CLAIM))) {
            return false;
        }
        return carriesAuthority(jwt) && withinLifetime(jwt);
    }

    /**
     * The {@code auth} claim, space-delimited, as the whole estate writes it — see
     * {@link SecurityUtils#AUTHORITIES_CLAIM}.
     *
     * <p>Split on whitespace rather than matched as a substring: {@code hasText} against
     * {@code "ROLE_CUSTOMER_CONTACT_READ"} would also be satisfied by
     * {@code "ROLE_CUSTOMER_CONTACT_READ_ONLY"}, and an authority somebody invents next year should
     * not inherit this one's permissions by sharing a prefix.
     */
    private static boolean carriesAuthority(Jwt jwt) {
        String claim = jwt.getClaimAsString(SecurityUtils.AUTHORITIES_CLAIM);
        if (claim == null) {
            return false;
        }
        for (String granted : claim.split("\\s+")) {
            if (AUTHORITY.equals(granted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The "short-lived" half of the contract, checked where it can be enforced.
     *
     * <p>Verbatim {@code ErasureFanoutToken}'s reasoning: the signing key is shared, so a service that
     * has been taken over can mint whatever it likes and this stops none of that. What it does stop is
     * the ordinary way the constraint decays — a later caller issuing one of these with a user token's
     * twenty-four hours in it, which every service in the estate would accept for a day and which
     * would look exactly like this one.
     */
    private static boolean withinLifetime(Jwt jwt) {
        Instant issued = jwt.getIssuedAt();
        Instant expires = jwt.getExpiresAt();
        return issued != null && expires != null && !Duration.between(issued, expires).minus(LIFETIME).isPositive();
    }
}
