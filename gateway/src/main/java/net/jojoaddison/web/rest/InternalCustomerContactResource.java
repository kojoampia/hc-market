package net.jojoaddison.web.rest;

import net.jojoaddison.repository.UserRepository;
import net.jojoaddison.security.ContactLookupToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The one thing the estate may ask this gateway about a person — {@code decisions.md} D72 §3 and D74.
 *
 * <h2>Why it exists</h2>
 *
 * <p>Paystack's {@code /transaction/initialize} requires the customer's email address.
 * {@code PaymentIntent} carries a login and no contact details, deliberately (D22, D50), and the two
 * cheaper sources were rejected twice: a field on {@code CreateBooking} is D22 verbatim, and the login
 * when it happens to be email-shaped works for a subset of users and fails at the moment they pay. The
 * gateway owns the account store, so the gateway is the only defensible source, and D72 §3 authorised
 * standing this up. Booking's {@code GatewayCustomerContacts} is the only caller.
 *
 * <h2>{@code /internal/}, and what that prefix does NOT buy here</h2>
 *
 * <p>catalog's {@code /internal/professionals/{ref}/login} authenticates nobody, and D28's argument for
 * why that is safe is entirely about the gateway's <em>routes</em>: they match
 * {@code /services/<service>/api/**}, so nothing outside can be routed to a path under
 * {@code /internal/}. <strong>None of that applies to this endpoint.</strong> It is on the gateway
 * itself — there is no route in front of it, both nginx vhosts end in a {@code location /} that proxies
 * everything here, and the dev and quality compose files publish the gateway's port on every interface.
 * This path is reachable from outside on every estate.
 *
 * <p>So the prefix is a naming convention that says "no browser calls this", and the protection is
 * {@code InternalApiSecurityConfiguration} plus the check below. Read that class before changing
 * either; it states the claim in full.
 *
 * <h2>Two refusals and two answers, and each of the four was decided rather than fallen into</h2>
 *
 * <ul>
 *   <li><strong>403</strong> — a token carrying {@link ContactLookupToken#AUTHORITY} that is not a
 *       contact-lookup token: a person's subject, another login in {@code contact_subject}, or a
 *       lifetime longer than thirty seconds. The filter chain cannot see any of that; it checks the
 *       authority, and an administrator can grant an authority to a real account.
 *   <li><strong>404, carrying the same record with a null email</strong> — no account has that login.
 *       <strong>The body is not decoration and must not be dropped as such</strong>: a
 *       {@code HEALTHCONNECT_GATEWAY_BASE_URL} pointed at any other Spring service in the estate 404s
 *       on this path too, so a bodyless 404 cannot tell "there is no such account" from "you are not
 *       talking to the account store". Echoing the login is what makes the first claim positive
 *       evidence rather than an inference from a status code — {@code GatewayCustomerContacts} refuses
 *       any 404 that does not name the login it asked about. See D74's review section.
 *   <li><strong>200 with a null email</strong> — the account exists and holds no address. Deliberately
 *       distinct from the 404: {@code email} has no {@code @NotNull} on {@code User} or on
 *       {@code AdminUserDTO}, so it is a reachable state, and "we do not know this person" and "we know
 *       them and hold no address" are different facts that need different log lines on the calling
 *       side. It is not a user-existence oracle for a stranger — a stranger cannot get either answer
 *       without the estate signing key, and the caller named the login in a signed token to ask.
 *   <li><strong>200 with the address</strong>, and nothing else about the account. No name, no
 *       authorities, no activation flag, no id. A payment adapter needs one field.
 * </ul>
 *
 * <p><strong>The email is never logged.</strong> Not at DEBUG, not in a refusal, not in an exception
 * message — the whole point of the endpoint is that the address exists in the account store and in the
 * provider's request and nowhere in between. The refusals name the login, which the caller supplied and
 * which is already in the token they presented.
 *
 * <h2>A new file, and it reads the generated repository directly</h2>
 *
 * <p>Regeneration leaves a new file alone. {@code UserRepository.findOneByLogin} is generated and
 * already exists — {@code DomainUserDetailsService} and {@code UserService} both use it — so nothing
 * here needs adding to a generated interface, which is the mistake catalog's {@code MarketplaceService}
 * exists to have avoided. Reading the repository from {@code web} is what the ArchUnit rule permits;
 * putting a method on the generated {@code UserService} would be discarded on the next regeneration.
 */
@RestController
@RequestMapping("/internal")
public class InternalCustomerContactResource {

    private static final Logger LOG = LoggerFactory.getLogger(InternalCustomerContactResource.class);

    private final UserRepository users;

    public InternalCustomerContactResource(UserRepository users) {
        this.users = users;
    }

    /**
     * The email address held for {@code login}, for a caller that can prove it is this estate.
     *
     * @param login the account to look up. It is compared against the token's own
     *     {@code contact_subject} claim, so naming a second person needs a second token
     */
    @GetMapping("/customers/{login}/email")
    public Mono<ResponseEntity<CustomerContact>> emailOf(@PathVariable("login") String login, @AuthenticationPrincipal Jwt jwt) {
        if (!ContactLookupToken.mayRead(jwt, login)) {
            // Not a WARN. On a healthy estate this fires for a probe, and a line that a stranger can
            // make appear is a line an operator learns to ignore; what makes it findable is that the
            // filter chain has already refused everything without the authority, so reaching here at
            // all means an estate-signed token was presented for something it does not authorise.
            LOG.info("a token carrying {} was presented for {} and is not a contact-lookup token", ContactLookupToken.AUTHORITY, login);
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return users
            .findOneByLogin(login)
            .map(user -> ResponseEntity.ok(new CustomerContact(user.getLogin(), user.getEmail())))
            // The 404 carries the record too, naming the login it was asked about — see the class
            // comment. Deliberately not `notFound().build()`: a bodyless 404 is indistinguishable from
            // the one any other Spring service in the estate gives for a path it does not map, and
            // booking would then report a misdeployed base URL as a fact about somebody's account.
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).body(new CustomerContact(login, null)));
    }

    /**
     * One account's contact details, and there is exactly one field of them.
     *
     * <p>{@code login} is echoed back so the caller can assert it got an answer about the person it
     * asked about, exactly as catalog's {@code ProfessionalLogin} echoes the reference. {@code email}
     * is null when the account holds none — see the class comment for why that is not a 404.
     */
    public record CustomerContact(String login, String email) {}
}
