package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import net.jojoaddison.security.ContactLookupToken;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints the credentials this estate issues to itself — {@code decisions.md} D37, D38, D74.
 *
 * <p>hc-market's five services share one signing secret, so a token booking signs is a token catalog
 * and messaging validate exactly as they validate a user's. That is what makes an orchestrated
 * erasure possible at all, and D37 had to correct the premise it was proposed on: the platform-wide
 * key in {@code ~/webroot/01-healthconnect/.env} belongs to hc-admin, hc-patient and hc-professional,
 * and hc-market is not in that set. Its own {@code JWT_BASE64_SECRET} is the only key any of this
 * works with.
 *
 * <p>The token is deliberately the weakest one that does the job: a subject that is not a person, one
 * authority that appears on one endpoint per service, the customer's login as a claim so it cannot be
 * replayed against anybody else, and thirty seconds. {@link ErasureFanoutToken} states that contract
 * and the receiving side enforces it — this class is only where it is written down.
 *
 * <p><strong>There are TWO now, and the second is not the first with a different caller</strong>
 * ({@code decisions.md} D74). {@link #forContactLookupOf(String)} asks the gateway for one customer's
 * email address so Paystack can be told who is paying. It was tempting to spend {@link #forErasureOf}
 * on it — the shape is identical, the key is the same, and it would have been one line — and that is
 * exactly the move D74 refused: a token minted by a method called {@code forErasureOf} carries
 * {@code ROLE_CUSTOMER_ERASURE}, so what would have been presented to the gateway is a credential
 * claiming to authorise an erasure, and the scope everybody believes in would have been the name of a
 * method in this file. <strong>A scope that is a Java method name is not a scope.</strong> The two
 * tokens therefore share the mechanism and nothing else: different authority, different subject,
 * different claim, each checked on the accepting side.
 *
 * <p><strong>Not named {@code TokenService} or {@code JwtService}.</strong> Nothing in this repository
 * generates either name today, but {@code service X with serviceClass} in the JDL generates
 * {@code XService}, and the wall of "cannot find symbol" that follows a regeneration eating a
 * hand-written class is the reason {@code BookingWorkflow} is called that. A minter is a minter.
 */
@Component
public class FanoutTokenMinter {

    /**
     * {@code iss}. Nothing validates it — {@code NimbusJwtDecoder} checks timestamps and the
     * signature and no more — so this is for whoever pastes a captured token into a decoder and needs
     * to know which of the five services issued it.
     */
    static final String ISSUER = "hc-market-booking";

    private final JwtEncoder encoder;

    public FanoutTokenMinter(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * A bearer token authorising the erasure of {@code login}, and of nobody else, for thirty seconds.
     *
     * <p>Its shape — the {@code auth} claim, HS512, {@code iat}/{@code exp} — is {@link #mint}'s, and
     * the four narrowings that make this credential <em>this</em> credential are the arguments on the
     * line below.
     */
    public String forErasureOf(String login) {
        return mint(
            ErasureFanoutToken.SUBJECT,
            MarketplaceAuthorities.CUSTOMER_ERASURE,
            ErasureFanoutToken.SUBJECT_CLAIM,
            login,
            ErasureFanoutToken.LIFETIME
        );
    }

    /**
     * A bearer token authorising a read of {@code login}'s contact details, and of nobody else's, for
     * thirty seconds — {@code decisions.md} D74.
     *
     * <p>Presented to the gateway's {@code GET /internal/customers/{login}/email} by
     * {@code GatewayCustomerContacts}, so that a Paystack authorization can name who is paying. See
     * {@link ContactLookupToken}, which is the contract and which the gateway enforces: the subject is
     * not a person, the authority appears on that one endpoint, and the login is a claim.
     *
     * <p><strong>Deliberately not {@link #forErasureOf}.</strong> Booking now holds two capabilities
     * against the estate key and they are not interchangeable — see the class comment. If a third
     * arrives, it gets its own method, its own authority and its own subject for the same reason, and
     * {@link #mint} is where the shape they share lives.
     */
    public String forContactLookupOf(String login) {
        return mint(
            ContactLookupToken.SUBJECT,
            ContactLookupToken.AUTHORITY,
            ContactLookupToken.SUBJECT_CLAIM,
            login,
            ContactLookupToken.LIFETIME
        );
    }

    /**
     * The shape both tokens share, and nothing either of them decides.
     *
     * <p>Every narrowing is a parameter: the caller names the subject, the authority, the claim that
     * carries the login and the lifetime, so a reader of {@link #forErasureOf} or
     * {@link #forContactLookupOf} sees the whole of what that credential is on one line. Factoring the
     * <em>values</em> in here — a default authority, a shared subject, a default lifetime — is how two
     * capabilities become one by accident, which is what D74 refused at the call site.
     *
     * <p>{@code auth} is the space-delimited claim the whole estate reads —
     * {@link SecurityUtils#AUTHORITIES_CLAIM} — carrying exactly one authority. Signed HS512, because
     * that is what every decoder here is built with and a token signed with anything else is refused
     * with a message about the signature rather than about the algorithm.
     */
    private String mint(String subject, String authority, String subjectClaim, String login, Duration lifetime) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(now.plus(lifetime))
            .subject(subject)
            .claim(SecurityUtils.AUTHORITIES_CLAIM, authority)
            .claim(subjectClaim, login)
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
