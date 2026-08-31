package net.jojoaddison.web.rest;

import net.jojoaddison.service.MarketplaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The catalogue's answer to one question another service in this estate needs and no browser may
 * ask: <em>whose login owns this professional reference?</em> — {@code decisions.md} D28.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code POST /api/bookings} in the booking service stored {@code professionalLogin} exactly as
 * the client sent it. D12 put the field on the booking on purpose, so the professional's inbox — a
 * constantly-hit read path — never has to ask catalog who a {@code professionalRef} belongs to. D22
 * then closed the price hole beside it and recorded this one as still open, because the public
 * profile endpoint correctly does not expose logins and there was nothing to check against.
 *
 * <p>What it cost was not a mispriced booking but a <strong>misdelivered</strong> one: a truthful
 * {@code professionalRef} with somebody else's login puts a real booking into the wrong inbox, and
 * every derived figure downstream stays perfectly consistent with the login that was stored. This
 * endpoint is the thing to check against.
 *
 * <h2>What protects it: the path, and the gateway's routes</h2>
 *
 * <p><strong>Not a role, and not a token.</strong> There is no service-to-service authentication in
 * this estate — every service validates JWTs and none of them holds one of its own — so the
 * protection is that the gateway cannot route here at all. Its four routes match
 * {@code /services/<service>/api/**}; this lives under {@code /internal/**}, which matches no route
 * in any environment, and the gateway is the only ingress everywhere (quality binds every published
 * port to 127.0.0.1 behind one nginx vhost; production publishes the gateway alone).
 *
 * <p>So the threat model, stated rather than implied: <strong>anything already inside the estate's
 * docker network can read any professional's login.</strong> That is the same trust level as being
 * able to reach the databases, which sit on those networks too. What D28 closes is the external
 * caller, who could previously do this through a documented public endpoint with an ordinary
 * customer token.
 *
 * <p>{@link net.jojoaddison.config.InternalApiSecurityConfiguration} permits the GET and denies
 * everything else under this prefix. That chain is a second line, not the line — if the gateway's
 * predicates are ever widened back to {@code /services/<service>/**}, this endpoint is on the
 * internet and the chain will happily serve it.
 *
 * <h2>Regeneration</h2>
 *
 * <p>A new file, so {@code jhipster jdl --force} leaves it alone — unlike a method added to the
 * generated {@code ProfessionalRepository}, which is why the lookup goes through the hand-written
 * {@code MarketplaceService}. The name is safe for the same reason {@code MarketplaceResource} is:
 * the JDL has no {@code InternalProfessional} entity, so the generator will never produce a class
 * that collides with it.
 */
@RestController
public class InternalProfessionalResource {

    private final MarketplaceService marketplace;

    public InternalProfessionalResource(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    /**
     * The login that owns {@code ref}.
     *
     * <p>404 when the reference is unknown <em>or</em> when the professional's login is blank. The
     * two are one answer on purpose: a caller can do nothing different with them, and both mean
     * "this reference cannot be attributed to anybody", which is exactly the condition booking must
     * refuse to create a booking on.
     *
     * @param ref the business reference, e.g. {@code p1} — never the numeric id
     */
    @GetMapping("/internal/professionals/{ref}/login")
    public ProfessionalLogin login(@PathVariable String ref) {
        return marketplace
            .loginOf(ref)
            .map(login -> new ProfessionalLogin(ref, login))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no professional with reference " + ref));
    }

    /**
     * Deliberately an object rather than a bare string. A future caller will want the reference
     * echoed back, or a second field beside it, and a response that is already JSON can grow one
     * without every client changing how it parses the body.
     */
    public record ProfessionalLogin(String reference, String login) {}
}
