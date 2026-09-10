package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.User;
import net.jojoaddison.repository.UserRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import net.jojoaddison.security.ContactLookupToken;
import net.jojoaddison.security.SecurityUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The gateway's answer to "who is paying" — {@code decisions.md} D72 §3, D74.
 *
 * <h2>What this file is for</h2>
 *
 * <p>The endpoint discloses a person's email address, so the cases that matter are the refusals. There
 * are five of them and each is a different way in: no credential at all, an ordinary login token, the
 * <em>erasure</em> fan-out token (D38's, which is the one this package was tempted to reuse), a token
 * carrying the right authority with a person's subject and a login token's lifetime — the shape an
 * administrator could actually produce — and a correct contact-lookup token pointed at somebody else.
 *
 * <p>Tokens are minted with the container's own {@code JwtEncoder}, so what is presented here is a
 * token the running estate would accept, not a mock. No key appears in this file.
 */
@IntegrationTest
@AutoConfigureWebTestClient
class InternalCustomerContactResourceIT {

    private static final String EMAIL_OF = "/internal/customers/{login}/email";

    private static final String WITH_EMAIL = "wp13.payer";
    private static final String WITHOUT_EMAIL = "wp13.contactless";
    private static final String ADDRESS = "wp13.payer@example.test";

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void seedTwoAccounts() {
        users.deleteAll().block();
        users.save(account(WITH_EMAIL, ADDRESS)).block();
        users.save(account(WITHOUT_EMAIL, null)).block();
    }

    @AfterEach
    void removeThem() {
        users.deleteAll().block();
    }

    // ---------------------------------------------------------------- the answers

    @Test
    @DisplayName("an account with an email answers with it, and with nothing else about the account")
    void anAccountWithAnEmailAnswersWithIt() {
        client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactTokenFor(WITH_EMAIL))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.login")
            .isEqualTo(WITH_EMAIL)
            .jsonPath("$.email")
            .isEqualTo(ADDRESS)
            // A payment adapter needs one field. Anything else here is a disclosure nobody asked for,
            // and the record has two components precisely so that this assertion can be written.
            .jsonPath("$.firstName")
            .doesNotExist()
            .jsonPath("$.authorities")
            .doesNotExist()
            .jsonPath("$.password")
            .doesNotExist()
            .jsonPath("$.activated")
            .doesNotExist();
    }

    /**
     * D74 §4.4's first case: a state, not a fault.
     *
     * <p>{@code email} carries no {@code @NotNull} on {@code User} or on {@code AdminUserDTO}, so an
     * account with no address is reachable rather than corrupt — which is why booking answers
     * {@code Optional.empty()} for it and not {@code ContactsUnavailable}.
     */
    @Test
    @DisplayName("an account with no email answers 200 and a null address, not 404 and not an error")
    void anAccountWithNoEmailAnswersANullAddress() {
        client
            .get()
            .uri(EMAIL_OF, WITHOUT_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactTokenFor(WITHOUT_EMAIL))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.login")
            .isEqualTo(WITHOUT_EMAIL)
            .jsonPath("$.email")
            .doesNotExist();
    }

    @Test
    @DisplayName("a login this estate holds no account for is 404")
    void anUnknownLoginIsNotFound() {
        client
            .get()
            .uri(EMAIL_OF, "wp13.stranger")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactTokenFor("wp13.stranger"))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    // ---------------------------------------------------------------- the five refusals

    @Test
    @DisplayName("no credential at all is 401, and the address is not in the body")
    void anAnonymousCallerIsRefused() {
        byte[] refusal = client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody()
            .returnResult()
            .getResponseBodyContent();

        assertThat(refusal == null ? "" : new String(refusal, StandardCharsets.UTF_8))
            .as("a refusal must not carry what it refused to disclose")
            .doesNotContain(ADDRESS);
    }

    @Test
    @DisplayName("an ordinary login token is 403 — the authority is granted by no login")
    void anOrdinaryUserTokenIsRefused() {
        client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(WITH_EMAIL, AuthoritiesConstants.USER, Duration.ofHours(24), null))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * The temptation this package refused, asserted — D74 §4.1.
     *
     * <p>{@code FanoutTokenMinter.forErasureOf} was one line away from being reused for this lookup.
     * Had it been, the credential presented to the gateway would have said {@code ROLE_CUSTOMER_ERASURE}
     * — a token claiming to authorise an erasure, spent on reading an email — and the "scope" would have
     * been the name of a method in booking. This is what makes the scope real: D38's token does not open
     * this door.
     */
    @Test
    @DisplayName("the erasure fan-out token does not open this door")
    void theErasureFanoutTokenIsRefused() {
        // Literals, because the gateway holds no copy of ErasureFanoutToken — it never accepted one of
        // these and D74 did not give it a reason to start. If booking's constants ever change, this
        // token stops being the erasure token and the test stops asserting what it says; that is the
        // cost of the gateway not being in that copy family, and it is smaller than joining it.
        String erasure = token("system:erasure-fanout", "ROLE_CUSTOMER_ERASURE", Duration.ofSeconds(30), "erasure_subject");

        client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + erasure)
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * The case the filter chain cannot see, and the reason {@code mayRead} exists — D74 §4.3.
     *
     * <p>{@code POST /api/admin/authorities} creates an authority by name and {@code UserResource}
     * grants it, both behind {@code ROLE_ADMIN}. So a real account can be handed
     * {@code ROLE_CUSTOMER_CONTACT_READ}, and its ordinary twenty-four-hour token would satisfy
     * {@code hasAuthority} — which is all the chain checks. The subject is what refuses it: a person's
     * login is not {@code system:contact-lookup}.
     */
    @Test
    @DisplayName("an administrator granting the authority to a real account does not make its token work")
    void aPersonHoldingTheAuthorityIsStillRefused() {
        String granted = token(WITH_EMAIL, ContactLookupToken.AUTHORITY, Duration.ofHours(24), ContactLookupToken.SUBJECT_CLAIM);

        client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + granted)
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @DisplayName("a contact-lookup token names one customer and cannot be replayed against a second")
    void aTokenForOneCustomerDoesNotReadAnother() {
        client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactTokenFor(WITHOUT_EMAIL))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * The third narrowing, and the one a rewritten minter would lose.
     *
     * <p>A fan-out credential with a user token's twenty-four hours in it would be accepted for a day
     * by every service in the estate and would look exactly like a correct one. Checked here rather
     * than promised by the issuer, which is the only place it can be enforced.
     */
    @Test
    @DisplayName("a contact-lookup token that lives longer than thirty seconds is refused")
    void aLongLivedContactTokenIsRefused() {
        String tooLong = token(
            ContactLookupToken.SUBJECT,
            ContactLookupToken.AUTHORITY,
            Duration.ofHours(24),
            ContactLookupToken.SUBJECT_CLAIM
        );

        client
            .get()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tooLong)
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * {@code /internal/**} takes reads and nothing else, so the prefix cannot quietly grow a write
     * endpoint — catalog's rule, kept in the reactive spelling.
     */
    @Test
    @DisplayName("everything that is not a GET is denied, even with a valid contact-lookup token")
    void nothingButAGetIsAllowed() {
        client
            .post()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactTokenFor(WITH_EMAIL))
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .isForbidden();

        client
            .delete()
            .uri(EMAIL_OF, WITH_EMAIL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + contactTokenFor(WITH_EMAIL))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    // ---------------------------------------------------------------- fixtures

    private static User account(String login, String email) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        user.setEmail(email);
        user.setActivated(true);
        user.setLangKey("en");
        return user;
    }

    /**
     * Exactly what {@code FanoutTokenMinter.forContactLookupOf(login)} mints, from the contract's own
     * constants rather than from literals — so a drift in {@code ContactLookupToken} moves this
     * fixture with it, and the copy diff in CI is what stops the two copies of that file drifting.
     */
    private String contactTokenFor(String login) {
        return token(
            ContactLookupToken.SUBJECT,
            ContactLookupToken.AUTHORITY,
            ContactLookupToken.LIFETIME,
            ContactLookupToken.SUBJECT_CLAIM,
            login
        );
    }

    /**
     * One token builder for all six shapes, so that each test names only the thing it varies.
     *
     * <p>{@code namedLogin} defaults to {@link #WITH_EMAIL} in the four-argument form, which is the
     * account every test but one asks about.
     */
    private String token(String subject, String authority, Duration lifetime, String subjectClaim) {
        return token(subject, authority, lifetime, subjectClaim, WITH_EMAIL);
    }

    private String token(String subject, String authority, Duration lifetime, String subjectClaim, String namedLogin) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer("hc-market-booking")
            .issuedAt(now)
            .expiresAt(now.plus(lifetime))
            .subject(subject)
            .claim(SecurityUtils.AUTHORITIES_CLAIM, authority);
        if (subjectClaim != null) {
            claims.claim(subjectClaim, namedLogin);
        }
        JwsHeader header = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
