package net.jojoaddison.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.User;
import net.jojoaddison.repository.UserRepository;
import net.jojoaddison.web.rest.vm.LoginVM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Every way of failing {@code POST /api/authenticate} answers the same thing — {@code decisions.md} D106,
 * backlog NEW-61.
 *
 * <p><strong>What this is red about.</strong> Before D106 an account that had registered and never
 * activated answered <strong>500</strong> with the body {@code "Invalid credentials"}, while a wrong
 * password and an unknown login answered <strong>401</strong> with the same body. So the status code was
 * an unauthenticated oracle: {@code 500} meant "registered here and never activated" and {@code 401}
 * meant "not registered", askable about any login or any address with no credential at all, because
 * {@code POST /api/register} is {@code permitAll}. The owner of such an account met a 500 telling them
 * the platform was broken <em>and</em> that their password was wrong, both false.
 *
 * <p><strong>Why the password cannot change the answer, which is what makes it an oracle rather than a
 * curiosity.</strong> {@code DomainUserDetailsService.createSpringSecurityUser} throws
 * {@code UserNotActivatedException} inside the lookup's {@code map}, and
 * {@code ReactiveUserDetailsService.findByUsername} is called by the authentication manager
 * <em>before</em> the password encoder is consulted. The outcome is therefore decided by the lookup
 * alone — hence {@link #unactivatedWithTheRightPasswordIsRefusedExactlyLikeTheWrongOne()}, which sends
 * the <em>correct</em> password and expects the same refusal.
 *
 * <p><strong>Why a real account and not a mock.</strong> The defect lives in the seam between three
 * things — what {@code DomainUserDetailsService} throws, what Spring's reactive authentication manager
 * does and does not re-wrap, and how {@code ExceptionTranslator.getMappedStatus} maps what comes out.
 * A mocked {@code ReactiveAuthenticationManager} or a mocked repository replaces one of the three and
 * asserts over the arrangement rather than over the estate. These accounts are written to the
 * Testcontainers Mongo this suite already starts, with {@code activated = false} — exactly what
 * {@code POST /api/register} writes.
 *
 * <p><strong>Both the status AND the body are asserted, on every bucket.</strong> The oracle closes only
 * if the four outcomes match on both: the status, the body's {@code title}, the body's {@code detail}
 * and the body's own embedded {@code status} field, which comes from {@code toStatus(ex)} and was
 * rendering {@code 500} inside a body whose title read {@code "Unauthorized"}.
 *
 * <p><strong>The message is deliberately unchanged.</strong> D102 §1 ratified new wording — <em>"Sorry
 * you can not log in. If your login is correct, check your email for further instructions."</em> — and
 * a resend of the activation link. D106 ships the <strong>status only</strong>, because the client that
 * serves {@code /account/activate} is deployed at no origin (D105 §3), so that sentence would tell
 * people to check an email this estate cannot usefully send. NEW-61 stays open for the remainder. If
 * you are here to change {@code "Invalid credentials"}, read D106 §2 first — the detail is asserted by
 * value in this file on purpose, so the wording change is a deliberate edit rather than a drift.
 *
 * <p>This is a new file, so {@code jhipster jdl --force} leaves it in place while it rewrites
 * {@code ExceptionTranslator}, which is a generated file. It is therefore the guard behind that row of
 * the regeneration table in {@code CLAUDE.md}.
 */
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_TIMEOUT)
@IntegrationTest
class UnactivatedLoginIsIndistinguishableIT {

    private static final String UNACTIVATED_LOGIN = "new61-never-activated";
    private static final String UNACTIVATED_EMAIL = "new61-never-activated@example.test";
    private static final String UNACTIVATED_PASSWORD = "the-right-password";

    private static final String ACTIVATED_LOGIN = "new61-activated";
    private static final String ACTIVATED_EMAIL = "new61-activated@example.test";
    private static final String ACTIVATED_PASSWORD = "also-the-right-password";

    private static final String UNKNOWN_LOGIN = "new61-no-such-login";
    private static final String UNKNOWN_EMAIL = "new61-no-such-address@example.test";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Two accounts, removed BY LOGIN rather than with {@code deleteAll()}. Every integration test in
     * this module shares one Mongo container and one Spring context, and the context's own
     * {@code InitialSetupMigration} writes {@code admin} and {@code user} once at startup — so a
     * collection-wide wipe here would delete accounts no later test class recreates, and this file
     * would redden tests it has nothing to do with.
     */
    @BeforeEach
    void seedTheTwoAccounts() {
        removeIfPresent(UNACTIVATED_LOGIN);
        removeIfPresent(ACTIVATED_LOGIN);

        // THE SUBJECT. `activated = false` is what `POST /api/register` writes, and what the owner of
        // this account meets when they try to sign in before clicking the link in the mail.
        User unactivated = new User();
        unactivated.setLogin(UNACTIVATED_LOGIN);
        unactivated.setEmail(UNACTIVATED_EMAIL);
        unactivated.setActivated(false);
        unactivated.setPassword(passwordEncoder.encode(UNACTIVATED_PASSWORD));
        unactivated.setLangKey("en");
        userRepository.save(unactivated).block();

        // THE CONTROL'S ACCOUNT. Identical in every respect but one.
        User activated = new User();
        activated.setLogin(ACTIVATED_LOGIN);
        activated.setEmail(ACTIVATED_EMAIL);
        activated.setActivated(true);
        activated.setPassword(passwordEncoder.encode(ACTIVATED_PASSWORD));
        activated.setLangKey("en");
        userRepository.save(activated).block();
    }

    private void removeIfPresent(String login) {
        userRepository.findOneByLogin(login).flatMap(userRepository::delete).block();
    }

    /**
     * The headline. Red before D106 with {@code 500}.
     */
    @Test
    void unactivatedWithAWrongPasswordIsRefusedWith401() {
        expectTheOneRefusal(UNACTIVATED_LOGIN, "not-the-right-password");
    }

    /**
     * The same, with the password that would work if the account were activated — so the supplied
     * password demonstrably does not decide the outcome, and the owner of the account is the person
     * this bucket is about.
     */
    @Test
    void unactivatedWithTheRightPasswordIsRefusedExactlyLikeTheWrongOne() {
        expectTheOneRefusal(UNACTIVATED_LOGIN, UNACTIVATED_PASSWORD);
    }

    /**
     * Probed by the address rather than by the login. {@code DomainUserDetailsService} takes a separate
     * branch for an email-shaped username ({@code findOneByEmailIgnoreCase}) and throws the same
     * exception from the same {@code map}, so this is a second route to the same disclosure and was
     * measured at 500 as well.
     */
    @Test
    void unactivatedProbedByItsEmailAddressIsRefusedTheSameWay() {
        expectTheOneRefusal(UNACTIVATED_EMAIL, UNACTIVATED_PASSWORD);
    }

    /** A login nobody registered. This bucket was always 401; it is here as the thing to match. */
    @Test
    void anUnknownLoginIsRefusedTheSameWay() {
        expectTheOneRefusal(UNKNOWN_LOGIN, "any password at all");
    }

    /** An address nobody registered — the by-email half of the bucket above. */
    @Test
    void anUnknownEmailAddressIsRefusedTheSameWay() {
        expectTheOneRefusal(UNKNOWN_EMAIL, "any password at all");
    }

    /** An account that exists and is activated, with the wrong password. Also always 401. */
    @Test
    void anActivatedAccountWithAWrongPasswordIsRefusedTheSameWay() {
        expectTheOneRefusal(ACTIVATED_LOGIN, "not-the-right-password");
    }

    /**
     * THE POSITIVE CONTROL, and without it every assertion above is satisfied by a gateway that refuses
     * everybody. An activated account with its own password still authenticates and still gets a token.
     */
    @Test
    void anActivatedAccountWithTheRightPasswordStillGetsAToken() {
        authenticate(ACTIVATED_LOGIN, ACTIVATED_PASSWORD)
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueMatches("Authorization", "Bearer .+")
            .expectBody()
            .jsonPath("$.id_token")
            .isNotEmpty();
    }

    /**
     * The one refusal all six failure cases must give, asserted on the status line AND on all three
     * parts of the body a caller can read. Asserting only the status would leave a translator free to
     * distinguish the buckets in the body, which is the same oracle one layer in.
     */
    private void expectTheOneRefusal(String username, String password) {
        authenticate(username, password)
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .doesNotExist("Authorization")
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(401)
            .jsonPath("$.title")
            .isEqualTo("Unauthorized")
            .jsonPath("$.detail")
            .isEqualTo("Invalid credentials")
            .jsonPath("$.id_token")
            .doesNotExist();
    }

    private WebTestClient.ResponseSpec authenticate(String username, String password) {
        LoginVM login = new LoginVM();
        login.setUsername(username);
        login.setPassword(password);
        try {
            return webTestClient
                .post()
                .uri("/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(om.writeValueAsBytes(login))
                .exchange();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise the login for " + username, e);
        }
    }
}
