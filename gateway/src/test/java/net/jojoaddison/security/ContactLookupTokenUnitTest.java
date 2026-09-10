package net.jojoaddison.security;

import static org.assertj.core.api.Assertions.assertThat;

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
