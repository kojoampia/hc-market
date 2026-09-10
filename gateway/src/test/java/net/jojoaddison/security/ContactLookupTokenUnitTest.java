package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Every branch of the contract, without a container — {@code decisions.md} D74.
 *
 * <p>{@code InternalCustomerContactResourceIT} asks the running gateway and is where the refusals are
 * proved end to end; this is where each of them is proved to be a separate refusal. The distinction
 * matters because {@code mayRead} is four checks that all answer {@code false}, and a battery that
 * fires as one boolean cannot tell you which of them is doing the work — three could be deleted with
 * every HTTP assertion still green.
 *
 * <p>It lives in the gateway only. Booking carries the same file byte for byte and uses only the
 * constants — {@code mayRead} is dead code in that copy on purpose, so the two can be compared as
 * bytes rather than as behaviour, exactly as {@code ErasureFanoutToken.mayErase} is.
 */
class ContactLookupTokenUnitTest {

    private static final String LOGIN = "ama.mensah";

    @Test
    @DisplayName("a contact-lookup token for this login may read it")
    void theCorrectTokenPasses() {
        assertThat(ContactLookupToken.mayRead(token().build(), LOGIN)).isTrue();
    }

    @Test
    @DisplayName("no token, and no login, are both refused")
    void nothingIsRefused() {
        assertThat(ContactLookupToken.mayRead(null, LOGIN)).isFalse();
        assertThat(ContactLookupToken.mayRead(token().build(), null)).isFalse();
        assertThat(ContactLookupToken.mayRead(token().build(), "")).isFalse();
        assertThat(ContactLookupToken.mayRead(token().build(), "   ")).isFalse();
    }

    /**
     * The subject check, which is the one the filter chain cannot make.
     *
     * <p>An administrator can create {@code ROLE_CUSTOMER_CONTACT_READ} by name and grant it to a real
     * account. {@code hasAuthority} would then pass; this is what does not.
     */
    @Test
    @DisplayName("a token whose subject is a person is refused however it is decorated")
    void aPersonsTokenIsRefused() {
        Jwt asAPerson = token().subject(LOGIN).build();

        assertThat(ContactLookupToken.mayRead(asAPerson, LOGIN)).isFalse();
    }

    @Test
    @DisplayName("the erasure fan-out's subject and authority do not open this door")
    void theErasureCredentialIsRefused() {
        Jwt erasure = Jwt.withTokenValue("t")
            .header("alg", "HS512")
            .subject("system:erasure-fanout")
            .claim(SecurityUtils.AUTHORITIES_CLAIM, "ROLE_CUSTOMER_ERASURE")
            .claim("erasure_subject", LOGIN)
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(30))
            .build();

        assertThat(ContactLookupToken.mayRead(erasure, LOGIN)).isFalse();
    }

    @Test
    @DisplayName("a token naming one customer cannot read another, and one naming nobody reads nobody")
    void theNamedCustomerIsTheOnlyOne() {
        assertThat(ContactLookupToken.mayRead(token().build(), "kofi.asante")).isFalse();
        assertThat(ContactLookupToken.mayRead(token().claims(claims -> claims.remove(ContactLookupToken.SUBJECT_CLAIM)).build(), LOGIN))
            .isFalse();
    }

    @Test
    @DisplayName("the authority must be present, and it must be the whole authority")
    void theAuthorityIsRequiredAndIsNotAPrefix() {
        assertThat(ContactLookupToken.mayRead(token().claim(SecurityUtils.AUTHORITIES_CLAIM, "ROLE_USER").build(), LOGIN)).isFalse();
        assertThat(ContactLookupToken.mayRead(token().claims(claims -> claims.remove(SecurityUtils.AUTHORITIES_CLAIM)).build(), LOGIN))
            .isFalse();

        // The reason carriesAuthority splits on whitespace instead of asking whether the claim contains
        // the name: an authority somebody invents next year must not inherit this one's permissions by
        // sharing a prefix with it.
        assertThat(
            ContactLookupToken.mayRead(
                token().claim(SecurityUtils.AUTHORITIES_CLAIM, ContactLookupToken.AUTHORITY + "_ONLY").build(),
                LOGIN
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("the authority is found among several, because auth is space-delimited")
    void theAuthorityIsFoundAmongOthers() {
        Jwt several = token().claim(SecurityUtils.AUTHORITIES_CLAIM, "ROLE_USER " + ContactLookupToken.AUTHORITY + " ROLE_ADMIN").build();

        assertThat(ContactLookupToken.mayRead(several, LOGIN)).isTrue();
    }

    /**
     * The lifetime, checked on the accepting side rather than promised by the issuer.
     *
     * <p>What this stops is not a stolen key — a shared symmetric key cannot be defended from a service
     * that has been taken over. It stops the constraint decaying: a rewritten minter issuing one of
     * these with a user token's twenty-four hours in it would be accepted for a day by every service in
     * the estate and would look exactly like a correct one.
     */
    @Test
    @DisplayName("thirty seconds exactly passes; thirty-one does not; a missing iat or exp does not")
    void theLifetimeIsChecked() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");

        assertThat(ContactLookupToken.mayRead(lived(now, ContactLookupToken.LIFETIME), LOGIN)).isTrue();
        assertThat(ContactLookupToken.mayRead(lived(now, Duration.ofSeconds(10)), LOGIN)).isTrue();
        assertThat(ContactLookupToken.mayRead(lived(now, Duration.ofSeconds(31)), LOGIN)).isFalse();
        assertThat(ContactLookupToken.mayRead(lived(now, Duration.ofHours(24)), LOGIN)).isFalse();

        assertThat(ContactLookupToken.mayRead(token().claims(claims -> claims.remove("iat")).build(), LOGIN))
            .as("a token with no iat has no stated lifetime, and an unstated one fails closed")
            .isFalse();
        assertThat(ContactLookupToken.mayRead(token().claims(claims -> claims.remove("exp")).build(), LOGIN)).isFalse();

        assertThat(ContactLookupToken.mayRead(lived(now, Duration.ofMillis(1)), LOGIN))
            .as("the shortest constructible span is well within thirty seconds and passes")
            .isTrue();
    }

    /**
     * Why {@code withinLifetime}'s {@code isNegative()} term cannot be tested, and what is pinned
     * instead — D74's review.
     *
     * <p>The review's optional was that {@code withinLifetime} accepted a negative span: {@code exp}
     * before {@code iat} trivially satisfies "no longer than thirty seconds". The term went in, and the
     * test written for it went red <strong>at the fixture rather than at the assertion</strong> —
     * {@code Jwt.Builder.build()} refuses to construct one:
     *
     * <pre>
     *   java.lang.IllegalArgumentException: expiresAt must be after issuedAt
     * </pre>
     *
     * <p>Every {@code Jwt} in existence comes through that builder, {@code NimbusJwtDecoder}'s included,
     * so a negative-span token cannot reach {@code mayRead} as a {@code Jwt} at all: the decoder throws
     * and the request is a 401 before this class is consulted. The term is therefore <strong>unreachable
     * today</strong>, and it is kept rather than reverted for the reason D60's read-side fallback is
     * kept — a guard whose premise is somebody else's assertion should not depend on that assertion
     * silently continuing to hold.
     *
     * <p>So what is asserted here is <em>the framework's refusal</em>, which is the real and measurable
     * fact. If a Spring Security upgrade relaxes it, this goes red and points at the term that then
     * starts doing work. <strong>Nothing here claims the term is covered</strong>, and this comment
     * exists so a reader counting green tests does not conclude otherwise.
     */
    @Test
    @DisplayName("a negative-span token is unconstructible, which is why withinLifetime's guard is unreachable")
    void aNegativeSpanCannotBeBuiltAtAll() {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");

        assertThatThrownBy(() -> lived(now, Duration.ofSeconds(-1)))
            .as("if this stops throwing, withinLifetime's isNegative() term becomes reachable and needs a real test")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expiresAt must be after issuedAt");

        // Zero as well: "after" is strict, so the constructible range is strictly positive. Found the
        // same way — an assertion written for a zero span went red at this same fixture.
        assertThatThrownBy(() -> lived(now, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);

        // The control: the same fixture with a positive span builds, so the assertions above are about
        // the sign and not about the fixture being broken.
        assertThat(lived(now, Duration.ofMillis(1))).isNotNull();
    }

    /** The contract's own constants, pinned — a rename here is a 403 in a running estate. */
    @Test
    @DisplayName("the subject, the claim and the authority are spelled as booking mints them")
    void theContractIsSpelledOut() {
        assertThat(ContactLookupToken.SUBJECT).isEqualTo("system:contact-lookup");
        assertThat(ContactLookupToken.SUBJECT_CLAIM).isEqualTo("contact_subject");
        assertThat(ContactLookupToken.AUTHORITY).isEqualTo("ROLE_CUSTOMER_CONTACT_READ");
        assertThat(ContactLookupToken.LIFETIME).isEqualTo(Duration.ofSeconds(30));

        assertThat(ContactLookupToken.SUBJECT)
            .as("the subject must not be resolvable to a person")
            .doesNotContain("@")
            .startsWith("system:");
    }

    private static Jwt.Builder token() {
        return builder(Instant.parse("2026-09-10T12:00:00Z"), ContactLookupToken.LIFETIME);
    }

    private static Jwt lived(Instant issued, Duration lifetime) {
        return builder(issued, lifetime).build();
    }

    private static Jwt.Builder builder(Instant issued, Duration lifetime) {
        return Jwt.withTokenValue("t")
            .header("alg", "HS512")
            .subject(ContactLookupToken.SUBJECT)
            .claim(SecurityUtils.AUTHORITIES_CLAIM, ContactLookupToken.AUTHORITY)
            .claim(ContactLookupToken.SUBJECT_CLAIM, LOGIN)
            .issuedAt(issued)
            .expiresAt(issued.plus(lifetime));
    }
}
