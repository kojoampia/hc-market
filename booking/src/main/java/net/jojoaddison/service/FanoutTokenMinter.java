package net.jojoaddison.service;

import java.time.Instant;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints the one credential this estate issues to itself — {@code decisions.md} D37, D38.
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
     * <p>{@code auth} is the space-delimited claim the whole estate reads —
     * {@link SecurityUtils#AUTHORITIES_CLAIM} — carrying exactly one authority. Signed HS512, because
     * that is what every decoder here is built with and a token signed with anything else is refused
     * with a message about the signature rather than about the algorithm.
     */
    public String forErasureOf(String login) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(now.plus(ErasureFanoutToken.LIFETIME))
            .subject(ErasureFanoutToken.SUBJECT)
            .claim(SecurityUtils.AUTHORITIES_CLAIM, MarketplaceAuthorities.CUSTOMER_ERASURE)
            .claim(ErasureFanoutToken.SUBJECT_CLAIM, login)
            .build();
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
