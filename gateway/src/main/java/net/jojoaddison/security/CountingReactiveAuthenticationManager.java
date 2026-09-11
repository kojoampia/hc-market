package net.jojoaddison.security;

import net.jojoaddison.management.GatewayIdentityMeters;
import net.jojoaddison.management.GatewayIdentityMeters.Outcome;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

/**
 * Counts what happens at {@code POST /api/authenticate} — {@code decisions.md} D84, backlog NEW-43.
 *
 * <p><strong>Why the authentication manager and not a filter.</strong> Every refusal at that endpoint
 * arrives as 401, so a {@code WebFilter} watching the response could count successes and failures and
 * could never tell a wrong password from an account that was registered and never activated. That
 * distinction is the whole reason the dashboard's two halves connect (see
 * {@link GatewayIdentityMeters}), and this is the one seam where it still exists as a type:
 * {@code DomainUserDetailsService} throws {@link UserNotActivatedException} and Spring's own manager
 * throws {@link BadCredentialsException}.
 *
 * <p><strong>Why a decorator and not an edit.</strong> The manager is a {@code @Bean} in the generated
 * {@code SecurityConfiguration}, so anything written there is discarded by the next
 * {@code jhipster jdl --force}. This is a new file, and {@code IdentityMetricsConfiguration} supplies it
 * as {@code @Primary} in front of the generated bean — the pattern this repository already uses for
 * public read access and the internal API rather than editing generated security config.
 *
 * <p><strong>It counts logins and only logins.</strong> The generated filter chain authenticates
 * requests with {@code oauth2ResourceServer(jwt)}, which never touches a
 * {@link ReactiveAuthenticationManager}, so the only caller of this is {@code AuthenticateController}.
 * If a future chain starts routing bearer tokens through an authentication manager, these counters stop
 * meaning "login attempts" and the metric name becomes a lie — which is why that fact is asserted by a
 * test rather than left as a comment.
 *
 * <p><strong>It changes no behaviour.</strong> Every signal is passed through untouched: the same
 * {@link Authentication} on success, the same error on failure. It records and re-throws; it does not
 * translate, swallow or re-wrap, because a metric is never worth changing what a caller sees. In
 * particular it does not log — the login is in the signal it is observing, and an address or a login in
 * a log line is the disclosure surface {@link GatewayIdentityMeters} refuses to put in a tag.
 */
public class CountingReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveAuthenticationManager delegate;
    private final GatewayIdentityMeters meters;

    public CountingReactiveAuthenticationManager(ReactiveAuthenticationManager delegate, GatewayIdentityMeters meters) {
        this.delegate = delegate;
        this.meters = meters;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return delegate
            .authenticate(authentication)
            .doOnNext(a -> meters.recordLogin(Outcome.SUCCESS))
            // AN EMPTY MONO IS NOT A SUCCESS. `ReactiveAuthenticationManager` may complete empty to mean
            // "this manager cannot decide", which reaches the controller as a 401 with no error — so
            // without this it would be counted as nothing at all and the outcomes would not sum to the
            // attempts. It is ERROR rather than BAD_CREDENTIALS because nothing established that the
            // credentials were wrong.
            .switchIfEmpty(Mono.fromRunnable(() -> meters.recordLogin(Outcome.ERROR)))
            .doOnError(e -> meters.recordLogin(classify(e)));
    }

    /**
     * Which bucket an error belongs in.
     *
     * <p>Ordered so the specific case is tested first: {@link UserNotActivatedException} extends
     * {@code AuthenticationException} and so does {@link BadCredentialsException}, and a check for the
     * general one would swallow the specific one — which is the defect this class exists to avoid,
     * arriving through its own classifier.
     */
    private static Outcome classify(Throwable e) {
        if (e instanceof UserNotActivatedException) {
            return Outcome.NOT_ACTIVATED;
        }
        if (e instanceof BadCredentialsException) {
            return Outcome.BAD_CREDENTIALS;
        }
        // EVERYTHING ELSE IS `ERROR`, NOT `BAD_CREDENTIALS`. A Mongo that cannot be reached, a decoder
        // that throws, a `UsernameNotFoundException` from a store that is up but empty — none of those is
        // somebody guessing a password, and counting them as one makes an outage read as an attack.
        return Outcome.ERROR;
    }
}
