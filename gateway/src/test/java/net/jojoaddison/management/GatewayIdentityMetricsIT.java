package net.jojoaddison.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.User;
import net.jojoaddison.management.GatewayIdentityMeters.Outcome;
import net.jojoaddison.repository.UserRepository;
import net.jojoaddison.service.IdentityMetricsRefresher;
import net.jojoaddison.web.rest.vm.LoginVM;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The identity meters, against the real authentication manager — {@code decisions.md} D84, NEW-43.
 *
 * <p>The unit test beside {@code CountingReactiveAuthenticationManager} pins the classification against
 * errors it constructs itself. <strong>This asks the running container instead</strong>, because the
 * question that decides whether the dashboard's two halves connect cannot be answered by a mock: does
 * {@link net.jojoaddison.security.UserNotActivatedException}, thrown by {@code DomainUserDetailsService},
 * actually survive Spring's {@code UserDetailsRepositoryReactiveAuthenticationManager} and reach the
 * classifier — or is it converted to {@code BadCredentialsException} on the way, as an unknown login is?
 * A mocked delegate cannot tell you, and the answer is the difference between a "not-activated" panel
 * that works and one that reads zero for ever.
 *
 * <p><strong>Every assertion is a DELTA, never a total.</strong> These counters are the whole container's
 * and other tests in the same JVM log in — {@code AuthenticateControllerIT} does it three times. Asserting
 * an absolute would be asserting "nothing else in this JVM authenticated while I was watching", which is
 * the flake D76 spent a package removing from the fan-out IT. Each test reads the counter, acts, and
 * asserts the difference.
 *
 * <p>Logins are unique per test for the same reason: two tests sharing a login would share whatever state
 * the first left in the user collection.
 */
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_TIMEOUT)
@IntegrationTest
class GatewayIdentityMetricsIT {

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private GatewayIdentityMeters meters;

    @Autowired
    private IdentityMetricsRefresher refresher;

    @Autowired
    private ReactiveAuthenticationManager authenticationManager;

    private double count(Outcome outcome) {
        return registry
            .get(GatewayIdentityMeters.LOGINS_METER)
            .tag(GatewayIdentityMeters.OUTCOME_TAG, outcome.tagValue())
            .counter()
            .count();
    }

    private void saveUser(String login, String password, boolean activated) {
        User user = new User();
        user.setLogin(login);
        user.setEmail(login + "@example.com");
        user.setActivated(activated);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user).block();
    }

    private void login(String username, String password) throws Exception {
        LoginVM vm = new LoginVM();
        vm.setUsername(username);
        vm.setPassword(password);
        webTestClient
            .post()
            .uri("/api/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(vm))
            .exchange();
    }

    /**
     * THE WIRING ITSELF. If {@code IdentityMetricsConfiguration}'s {@code @Primary} stopped taking effect
     * — a rename of the generated bean, a second manager, the annotation dropped — every counter below
     * would stay at zero and every delta assertion would fail with no indication why. This says why.
     */
    @Test
    @DisplayName("the counting manager is the one the container injects by type")
    void theDecoratorIsInFront() {
        assertThat(authenticationManager)
            .as(
                "AuthenticateController injects ReactiveAuthenticationManager by type, so the @Primary decorator must win; " +
                "if this is the generated manager then nothing counts a login and the dashboard reads zero for ever"
            )
            .isInstanceOf(net.jojoaddison.security.CountingReactiveAuthenticationManager.class);
    }

    @Test
    @DisplayName("a successful login moves success and nothing else")
    void successMovesSuccessOnly() throws Exception {
        saveUser("metrics-success", "the-password", true);
        double before = count(Outcome.SUCCESS);
        double badBefore = count(Outcome.BAD_CREDENTIALS);

        login("metrics-success", "the-password");

        assertThat(count(Outcome.SUCCESS)).isEqualTo(before + 1);
        assertThat(count(Outcome.BAD_CREDENTIALS)).isEqualTo(badBefore);
    }

    @Test
    @DisplayName("a wrong password moves bad-credentials and not success")
    void wrongPasswordMovesBadCredentials() throws Exception {
        saveUser("metrics-badpass", "the-password", true);
        double before = count(Outcome.BAD_CREDENTIALS);
        double successBefore = count(Outcome.SUCCESS);

        login("metrics-badpass", "not-the-password");

        assertThat(count(Outcome.BAD_CREDENTIALS)).isEqualTo(before + 1);
        assertThat(count(Outcome.SUCCESS)).isEqualTo(successBefore);
    }

    /**
     * THE QUESTION A MOCK CANNOT ANSWER, and the reason this IT exists at all. If Spring's manager
     * converts {@code UserNotActivatedException} the way it converts {@code UsernameNotFoundException},
     * this attempt lands in {@code bad-credentials} and the "not-activated" series is dead — in which case
     * this test is the thing that says so, rather than a dashboard panel that reads zero and looks fine.
     */
    @Test
    @DisplayName("an unactivated account moves not-activated, which is what links the two halves of the dashboard")
    void unactivatedAccountMovesItsOwnBucket() throws Exception {
        saveUser("metrics-dormant", "the-password", false);
        double before = count(Outcome.NOT_ACTIVATED);
        double badBefore = count(Outcome.BAD_CREDENTIALS);

        login("metrics-dormant", "the-password");

        assertThat(count(Outcome.NOT_ACTIVATED))
            .as("UserNotActivatedException must reach the classifier rather than being converted on the way")
            .isEqualTo(before + 1);
        assertThat(count(Outcome.BAD_CREDENTIALS))
            .as("a dormant account is not a wrong password, and folding them together erases the panel's explanation")
            .isEqualTo(badBefore);
    }

    /**
     * The gauges, driven once rather than waited for.
     *
     * <p>Asserted as a <strong>partition</strong> and not against absolute values: the collection holds
     * whatever every other test in this JVM has left in it, so what can be asserted is that the two
     * numbers are both non-negative, that they sum to the collection's size, and that adding an
     * unactivated account moves the unactivated side by exactly one. That last delta is the assertion
     * that would fail if the query said {@code is(false)} and a document had no {@code activated} field.
     */
    @Test
    @DisplayName("the account gauges partition the collection, and a new dormant account moves one side by one")
    void gaugesPartitionTheCollection() {
        refresher.refresh().block();
        long activatedBefore = meters.activatedAccounts();
        long dormantBefore = meters.notActivatedAccounts();
        assertThat(activatedBefore).as("the first refresh must have answered").isNotNegative();
        assertThat(dormantBefore).isNotNegative();
        assertThat(activatedBefore + dormantBefore).isEqualTo(userRepository.count().block());

        saveUser("metrics-gauge-dormant", "the-password", false);
        refresher.refresh().block();

        assertThat(meters.notActivatedAccounts()).isEqualTo(dormantBefore + 1);
        assertThat(meters.activatedAccounts()).isEqualTo(activatedBefore);
        assertThat(meters.activatedAccounts() + meters.notActivatedAccounts())
            .as("the two gauges must still partition the collection, which `ne(true)` guarantees and `is(false)` would not")
            .isEqualTo(userRepository.count().block());
    }

    /**
     * NO TAG MAY CARRY A LOGIN. Asserted over the registry's own meters rather than over the source,
     * because the thing that must be true is about what is published: a login in a label is unbounded
     * cardinality and a disclosure surface that survives erasure, since nothing re-keys a metric already
     * exported and the sweep does not visit a metrics backend.
     */
    @Test
    @DisplayName("neither meter carries a per-user tag, and the tag values are the closed sets")
    void noMeterCarriesALogin() {
        saveUser("metrics-tagcheck", "the-password", true);

        var loginTags = registry
            .find(GatewayIdentityMeters.LOGINS_METER)
            .meters()
            .stream()
            .flatMap(m -> m.getId().getTags().stream())
            .toList();
        assertThat(loginTags).allSatisfy(tag -> assertThat(tag.getKey()).isEqualTo(GatewayIdentityMeters.OUTCOME_TAG));
        assertThat(loginTags.stream().map(io.micrometer.core.instrument.Tag::getValue).distinct())
            .containsOnly("success", "bad-credentials", "not-activated", "error");

        var accountTags = registry.find(GatewayIdentityMeters.ACCOUNTS_METER).meters().stream().flatMap(m -> m.getId().getTags().stream()).toList();
        assertThat(accountTags).allSatisfy(tag -> assertThat(tag.getKey()).isEqualTo(GatewayIdentityMeters.STATE_TAG));
        assertThat(accountTags.stream().map(io.micrometer.core.instrument.Tag::getValue).distinct())
            .containsOnly("activated", "not-activated");
    }
}
