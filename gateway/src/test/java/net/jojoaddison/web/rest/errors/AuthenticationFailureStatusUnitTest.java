package net.jojoaddison.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.security.UserNotActivatedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;

/**
 * Which {@code AuthenticationException}s this estate answers 401 to, and which one it must NOT —
 * {@code decisions.md} D106, backlog NEW-61.
 *
 * <p>{@code ExceptionTranslator} has two halves that disagreed about their subject. The <strong>body</strong>
 * mapping has always branched on the superclass — {@code if (ex instanceof AuthenticationException)} →
 * title {@code "Unauthorized"}, detail {@code "Invalid credentials"} — while {@code getMappedStatus}
 * <strong>enumerated subclasses</strong> and did not name {@code UserNotActivatedException}, this estate's
 * one custom member of the family. So the status fell through to 500 inside a body that said
 * {@code "Unauthorized"}. D106 makes the status half generalise, with one deliberate exclusion.
 *
 * <p><strong>The exclusion is the part to read before widening anything here.</strong>
 * {@code AuthenticationServiceException} and its subclass {@code InternalAuthenticationServiceException} —
 * which the servlet stack uses to wrap a user-store <em>failure</em> — are {@code AuthenticationException}s
 * that mean <em>the estate is broken</em> and not <em>that credential is wrong</em>: an unreachable user
 * store, a Mongo timeout. Answering 401 for one would disguise an outage as a credential refusal, and
 * {@code deploy/observability/hc-market-rules.yaml} keys its alerting on <strong>5xx rates</strong>, so it
 * would silence the alert as well as mislead the caller. They keep falling through to 500.
 *
 * <p><strong>This is a unit test and not an IT, on purpose.</strong> No code in this gateway throws either
 * service exception today and Spring's reactive {@code AbstractUserDetailsReactiveAuthenticationManager}
 * constructs none — so there is no request that would produce one and no IT could reach the arm. What is
 * being pinned is the mapping's own rule, which is where the next custom subclass will meet it.
 *
 * <p>⚠ <strong>It constructs SIX exceptions, and an earlier version of this comment said one.</strong>
 * Measured by disassembling it on {@code spring-security-core} 7.0.6: {@code BadCredentialsException},
 * {@code DisabledException}, {@code LockedException}, {@code AccountExpiredException},
 * {@code CredentialsExpiredException} and {@code CompromisedPasswordException}. The conclusion is
 * unchanged — none is an {@code AuthenticationServiceException}, so the exclusion really is prospective —
 * but the evidence was wrong, and it was wrong because the grep behind it anchored on the
 * {@code org/springframework/security/authentication/} package and {@code CompromisedPasswordException}
 * lives one package down in {@code .../authentication/password/}. <strong>Suspect the instrument</strong>:
 * a pattern that names a package cannot see a subpackage, and the sixth was invisible to two readers for
 * that one reason.
 *
 * <p>⚠ <strong>What the {@code null} return actually rests on, which is not obvious from reading it.</strong>
 * {@code toStatus} falls from {@code getMappedStatus}'s {@code null} to {@code resolveResponseStatus},
 * which <strong>recurses into {@code getCause()}</strong> — and
 * {@code InternalAuthenticationServiceException} exists precisely to wrap something. So "excluded means
 * 500" holds only while no cause in the chain carries {@code @ResponseStatus}. It holds today: the one
 * annotated throwable in this gateway is {@code AccountResource}'s private
 * {@code AccountResourceException}, thrown nowhere near authentication, and a realistic cause here is a
 * Mongo or socket failure carrying no annotation — which is why
 * {@link #theWrappedUserStoreFailureIsStillAServerErrorToo()} wraps an {@code IllegalStateException}
 * rather than something exotic. A cause that did carry an annotation would win, and that would be correct
 * rather than a defect.
 *
 * <p>Both halves of the behaviour are read off the real {@code ProblemDetailWithCause} the shipped
 * translator composes, rather than from a mock or a reimplementation of the branch.
 */
class AuthenticationFailureStatusUnitTest {

    private final ExceptionTranslator translator = new ExceptionTranslator(new MockEnvironment());

    /**
     * THE HEADLINE, at the mapping. Red before D106 with {@code 401} replaced by {@code 500} — in the
     * body's own {@code status} field as well as in the status line the caller sees.
     */
    @Test
    void anUnactivatedAccountIsAnUnauthorizedAndNotAServerError() {
        ProblemDetailWithCause problem = translate(new UserNotActivatedException("User someone was not activated"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
        assertThat(problem.getDetail()).isEqualTo("Invalid credentials");
        // Status-derived today, and asserted for the reason `title` had to be — see
        // `theThreeFailureBucketsAreIndistinguishable`.
        assertThat(problem.getProperties()).containsEntry("message", "error.http.401");
    }

    /**
     * THE CONTROL FOR IT. The two buckets the generator did enumerate were already 401 and still are, so
     * the test above is not passing because everything became 401.
     */
    @Test
    void theTwoBucketsTheGeneratorEnumeratedAreUnchanged() {
        assertThat(translate(new BadCredentialsException("wrong password")).getStatus()).isEqualTo(401);
        assertThat(translate(new UsernameNotFoundException("no such login")).getStatus()).isEqualTo(401);
    }

    /**
     * All three failure buckets are indistinguishable across EVERY serialised member, which is what
     * closes the oracle: a translator free to vary any part of the body would be the same disclosure one
     * layer in.
     *
     * <p><strong>The whole property map is compared, not a list of names.</strong> The body has five
     * serialised members — {@code status}, {@code title}, {@code detail}, and the two properties
     * {@code message} and {@code path} — and an assertion naming three of them cannot see a fourth being
     * added. {@code message} is the live risk: it is {@code "error.http." + status} today, so it moved to
     * {@code 401} with the status and needed no fix, but {@code getMappedMessageKey} is a per-exception
     * branch, and one arm there returning {@code "error.account.notActivated"} would name the bucket on
     * the wire with status, title and detail all identical. <strong>That was precisely the state
     * {@code title} was in before D106</strong> — status-derived, therefore unable to drift without an
     * edit, therefore asserted by nobody, therefore leaking. Comparing the map rather than enumerating
     * keys is what stops this test being one field behind the next one.
     */
    @Test
    void theThreeFailureBucketsAreIndistinguishable() {
        ProblemDetailWithCause notActivated = translate(new UserNotActivatedException("User someone was not activated"));
        ProblemDetailWithCause wrongPassword = translate(new BadCredentialsException("wrong password"));
        ProblemDetailWithCause unknownLogin = translate(new UsernameNotFoundException("no such login"));

        assertThat(notActivated.getStatus()).isEqualTo(wrongPassword.getStatus()).isEqualTo(unknownLogin.getStatus());
        assertThat(notActivated.getTitle()).isEqualTo(wrongPassword.getTitle()).isEqualTo(unknownLogin.getTitle());
        assertThat(notActivated.getDetail()).isEqualTo(wrongPassword.getDetail()).isEqualTo(unknownLogin.getDetail());
        // Every property, in one comparison. All three are translated from the same exchange, so `path`
        // is equal by construction and the only member that can differ is one keyed on the exception.
        assertThat(notActivated.getProperties()).isEqualTo(wrongPassword.getProperties()).isEqualTo(unknownLogin.getProperties());
    }

    /**
     * THE DIRECTION THE SUPERCLASS ARM MUST NOT TRAVEL. A broken user store is a 5xx, so the alert that
     * keys on 5xx rates still fires and nobody is told their password is wrong about an outage.
     */
    @Test
    void aBrokenUserStoreIsStillAServerError() {
        assertThat(translate(new AuthenticationServiceException("the user store could not be reached")).getStatus()).isEqualTo(500);
    }

    /**
     * And its subclass, which is the spelling the servlet stack produces by wrapping whatever a
     * {@code UserDetailsService} threw — so a migration to that stack, or any decorator that adopts the
     * same convention, lands on the excluded arm rather than on 401.
     */
    @Test
    void theWrappedUserStoreFailureIsStillAServerErrorToo() {
        assertThat(
            translate(new InternalAuthenticationServiceException("Mongo timed out", new IllegalStateException("socket closed"))).getStatus()
        ).isEqualTo(500);
    }

    private ProblemDetailWithCause translate(Throwable ex) {
        return translator.wrapAndCustomizeProblem(
            ex,
            MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, "/api/authenticate"))
        );
    }
}
