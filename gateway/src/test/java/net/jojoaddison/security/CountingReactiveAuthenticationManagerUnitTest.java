package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jojoaddison.management.GatewayIdentityMeters;
import net.jojoaddison.management.GatewayIdentityMeters.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Mono;

/**
 * {@link CountingReactiveAuthenticationManager}'s classification — {@code decisions.md} D84, NEW-43.
 *
 * <p>What is being pinned is <strong>which bucket each outcome lands in</strong>, not that something was
 * counted. The request asked for "logins aggregated by (success | failed)" and the answer is four
 * buckets, so a test asserting only that the total moved would pass for a classifier that put everything
 * in one — which is the defect the four buckets exist to avoid.
 */
class CountingReactiveAuthenticationManagerUnitTest {

    private MeterRegistry registry;
    private GatewayIdentityMeters meters;
    private final Authentication request = new UsernamePasswordAuthenticationToken("ama", "secret");

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        meters = new GatewayIdentityMeters(registry);
    }

    private double count(Outcome outcome) {
        return registry
            .get(GatewayIdentityMeters.LOGINS_METER)
            .tag(GatewayIdentityMeters.OUTCOME_TAG, outcome.tagValue())
            .counter()
            .count();
    }

    /** Every counter must exist before anything has happened — see the constructor's comment on why. */
    @Test
    @DisplayName("all four outcome counters are registered at zero, so no series is absent")
    void allFourCountersExistAtZero() {
        for (Outcome outcome : Outcome.values()) {
            assertThat(count(outcome)).as("%s starts at zero rather than being absent", outcome).isZero();
        }
    }

    @Test
    @DisplayName("a successful login counts as success, and the caller gets the same Authentication back")
    void successIsCounted() {
        Authentication authenticated = new UsernamePasswordAuthenticationToken("ama", "secret", java.util.List.of());
        var subject = manager(Mono.just(authenticated));

        assertThat(subject.authenticate(request).block()).isSameAs(authenticated);

        assertThat(count(Outcome.SUCCESS)).isEqualTo(1);
        assertThat(count(Outcome.BAD_CREDENTIALS)).isZero();
        assertThat(count(Outcome.NOT_ACTIVATED)).isZero();
        assertThat(count(Outcome.ERROR)).isZero();
    }

    @Test
    @DisplayName("a wrong password counts as bad-credentials and the same exception reaches the caller")
    void badCredentialsIsCounted() {
        var thrown = new BadCredentialsException("Invalid Credentials");
        var subject = manager(Mono.error(thrown));

        assertThatThrownBy(() -> subject.authenticate(request).block()).isSameAs(thrown);

        assertThat(count(Outcome.BAD_CREDENTIALS)).isEqualTo(1);
        assertThat(count(Outcome.SUCCESS)).isZero();
        assertThat(count(Outcome.NOT_ACTIVATED)).isZero();
    }

    /**
     * THE ONE THE DASHBOARD TURNS ON. A registered-but-never-activated account failing to log in is the
     * outcome that explains the registration panel's "not-activated" gauge, and it must not be folded in
     * with a wrong password.
     */
    @Test
    @DisplayName("an unactivated account counts as not-activated, never as bad-credentials")
    void notActivatedIsItsOwnBucket() {
        var thrown = new UserNotActivatedException("User ama was not activated");
        var subject = manager(Mono.error(thrown));

        assertThatThrownBy(() -> subject.authenticate(request).block()).isSameAs(thrown);

        assertThat(count(Outcome.NOT_ACTIVATED)).isEqualTo(1);
        assertThat(count(Outcome.BAD_CREDENTIALS)).as("a wrong password and a dormant account are different facts").isZero();
        assertThat(count(Outcome.SUCCESS)).isZero();
    }

    /**
     * ORDERING, ASSERTED. {@link UserNotActivatedException} and {@link BadCredentialsException} are both
     * {@code AuthenticationException}s, so a classifier testing the general case first would put the
     * specific one in the wrong bucket — and every other assertion here would still pass.
     */
    @Test
    @DisplayName("a store that cannot be reached counts as error, not as a password guess")
    void infrastructureFailureIsNotAPasswordGuess() {
        var subject = manager(Mono.error(new DataAccessResourceFailureException("mongo is away")));

        assertThatThrownBy(() -> subject.authenticate(request).block()).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(count(Outcome.ERROR)).isEqualTo(1);
        assertThat(count(Outcome.BAD_CREDENTIALS)).as("an outage must not read as a password-guessing spike").isZero();
    }

    @Test
    @DisplayName("an unknown login counts as bad-credentials when the delegate says so, and never as success")
    void unknownLoginFollowsTheDelegate() {
        var subject = manager(Mono.error(new UsernameNotFoundException("no such user")));

        assertThatThrownBy(() -> subject.authenticate(request).block()).isInstanceOf(UsernameNotFoundException.class);

        // Spring's own manager converts this to BadCredentialsException before it ever reaches here, to
        // avoid user enumeration; this asserts what happens if a delegate ever lets the raw type out —
        // it is ERROR, because nothing established that the credentials were wrong.
        assertThat(count(Outcome.ERROR)).isEqualTo(1);
        assertThat(count(Outcome.SUCCESS)).isZero();
    }

    /**
     * An empty {@code Mono} means "this manager cannot decide" and reaches the controller as a 401 with no
     * error. Without the {@code switchIfEmpty} it would be counted as nothing at all, so the outcomes
     * would not sum to the attempts and a silently-undeciding manager would be invisible.
     */
    @Test
    @DisplayName("a manager that completes empty counts as error rather than as nothing")
    void emptyIsNotSilence() {
        var subject = manager(Mono.empty());

        assertThat(subject.authenticate(request).block()).isNull();

        assertThat(count(Outcome.ERROR)).isEqualTo(1);
        assertThat(count(Outcome.SUCCESS)).as("completing empty is not a login").isZero();
    }

    private ReactiveAuthenticationManager manager(Mono<Authentication> answer) {
        return new CountingReactiveAuthenticationManager(authentication -> answer, meters);
    }
}
