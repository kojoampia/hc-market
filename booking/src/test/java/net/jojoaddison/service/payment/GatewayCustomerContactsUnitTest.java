package net.jojoaddison.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import net.jojoaddison.security.ContactLookupToken;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.FanoutTokenMinter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

/**
 * Booking's half of the customer's email — {@code decisions.md} D72 §3, D74.
 *
 * <h2>Against a real socket, and with a real token</h2>
 *
 * <p>The stub is a JDK {@code HttpServer} on loopback, following
 * {@code PaystackPaymentProviderUnitTest} for its reason: the request asserted on is the one that went
 * over a wire, header spelling and all. The token is minted by the real {@link FanoutTokenMinter} over
 * a key generated in {@link #startStub()} — so the credential this test inspects is the credential the
 * running service sends, and no key appears in this file. The repository is public.
 *
 * <p>It is then <strong>decoded and handed to {@code ContactLookupToken.mayRead}</strong>, which is the
 * gateway's own acceptance check on booking's own copy of that file. That is the one assertion here
 * that spans the two services, and it is the reason the copy is byte-identical: a claim name drifting
 * by one character between the minter and the acceptor is a 403 that reads as a permissions problem.
 *
 * <h2>The four cases of D74 §4.4 are the point</h2>
 *
 * <p>All four end in 502 and no booking, because {@code BookingPayments.take} wraps the provider call
 * and turns any {@code RuntimeException} into {@code FAILED} (D44). So what is being decided — and what
 * is asserted below — is which of them is {@code Optional.empty()} and which is louder, and the whole
 * value of the split is what the operator's log says.
 */
class GatewayCustomerContactsUnitTest {

    private static final String LOGIN = "ama.mensah";
    private static final String ADDRESS = "ama.mensah@example.test";

    private StubAccountStore gateway;
    private SecretKey key;

    @BeforeEach
    void startStub() throws Exception {
        gateway = new StubAccountStore();
        key = KeyGenerator.getInstance("HmacSHA512").generateKey();
    }

    @AfterEach
    void stopStub() {
        gateway.stop();
    }

    // ---------------------------------------------------------------- the answer

    @Test
    @DisplayName("the address the account store holds is the address the adapter gets")
    void theAddressComesBack() {
        gateway.willAnswer(200, """
            {"login":"%s","email":"%s"}""".formatted(LOGIN, ADDRESS));

        assertThat(contacts().emailOf(LOGIN)).contains(ADDRESS);
    }

    @Test
    @DisplayName("a padded address is trimmed rather than sent to a provider with whitespace in it")
    void theAddressIsTrimmed() {
        gateway.willAnswer(200, """
            {"login":"%s","email":"  %s  "}""".formatted(LOGIN, ADDRESS));

        assertThat(contacts().emailOf(LOGIN)).contains(ADDRESS);
    }

    // ---------------------------------------------------------------- the request

    /**
     * The whole of D74 §4.1, asserted on the wire.
     *
     * <p>Reusing {@code forErasureOf} here would have been one line and would have presented the
     * gateway with a credential claiming to authorise an erasure. So this asserts what the token
     * <em>is</em> — subject, authority, named login, lifetime — and, separately, what it is
     * <strong>not</strong>: the erasure token's subject and authority, named from booking's own copies
     * of both contracts rather than from literals.
     */
    @Test
    @DisplayName("the request carries a contact-lookup token for that login, and not the erasure token")
    void theRequestCarriesAContactLookupToken() {
        gateway.willAnswer(200, """
            {"login":"%s","email":"%s"}""".formatted(LOGIN, ADDRESS));

        contacts().emailOf(LOGIN);

        StubAccountStore.Received asked = gateway.only();
        assertThat(asked.method()).isEqualTo("GET");
        assertThat(asked.path()).isEqualTo("/internal/customers/" + LOGIN + "/email");
        assertThat(asked.header("Authorization")).startsWith("Bearer ");

        Jwt presented = decode(asked.header("Authorization").substring("Bearer ".length()));
        assertThat(presented.getSubject()).isEqualTo(ContactLookupToken.SUBJECT);
        assertThat(presented.getClaimAsString(SecurityUtils.AUTHORITIES_CLAIM)).isEqualTo(ContactLookupToken.AUTHORITY);
        assertThat(presented.getClaimAsString(ContactLookupToken.SUBJECT_CLAIM)).isEqualTo(LOGIN);
        assertThat(Duration.between(presented.getIssuedAt(), presented.getExpiresAt())).isEqualTo(ContactLookupToken.LIFETIME);

        assertThat(presented.getSubject()).as("this is not the erasure credential").isNotEqualTo(ErasureFanoutToken.SUBJECT);
        assertThat(presented.getClaimAsString(SecurityUtils.AUTHORITIES_CLAIM)).isNotEqualTo(MarketplaceAuthorities.CUSTOMER_ERASURE);
        assertThat(presented.getClaims()).doesNotContainKey(ErasureFanoutToken.SUBJECT_CLAIM);

        // And booking's own copy of the gateway's acceptance check agrees, which is the only place the
        // two services' copies of that file are compared by behaviour rather than by bytes.
        assertThat(ContactLookupToken.mayRead(presented, LOGIN)).isTrue();
        assertThat(ContactLookupToken.mayRead(presented, "kofi.asante")).isFalse();
    }

    // ---------------------------------------------------------------- D74 §4.4, case by case

    /**
     * Case one: a state, not a fault. Empty, and no exception.
     *
     * <p>{@code email} carries no {@code @NotNull} in the gateway's user model, so an account with no
     * address is reachable. Paystack's {@code authorize} then refuses before its round trip and the
     * customer gets 502 and no booking — which is the correct outcome for a customer this platform
     * cannot name to a payment provider.
     */
    @Test
    @DisplayName("an account holding no email is empty, and is not an error")
    void anAccountWithNoEmailIsEmpty() {
        gateway.willAnswer(200, """
            {"login":"%s","email":null}""".formatted(LOGIN));

        assertThat(contacts().emailOf(LOGIN)).isEmpty();
    }

    @Test
    @DisplayName("a blank email is the same as none")
    void aBlankEmailIsEmpty() {
        gateway.willAnswer(200, """
            {"login":"%s","email":"   "}""".formatted(LOGIN));

        assertThat(contacts().emailOf(LOGIN)).isEmpty();
    }

    /**
     * Case two: it answered, and there is no such account. Empty, with a WARN rather than an ERROR.
     *
     * <p>The 404 has to <strong>name the login</strong> for that conclusion to be available — see the
     * three cases below, which are D74's review finding.
     */
    @Test
    @DisplayName("a 404 naming this login is empty, not unavailable")
    void anUnknownLoginIsEmpty() {
        gateway.willAnswer(404, """
            {"login":"%s","email":null}""".formatted(LOGIN));

        assertThat(contacts().emailOf(LOGIN)).isEmpty();
    }

    /**
     * The two causes of a 404, told apart — D74's review.
     *
     * <p>Every Spring service in this estate 404s on a path it does not map, so
     * {@code HEALTHCONNECT_GATEWAY_BASE_URL} misdeployed to catalog, payout or messaging produces a 404
     * for <strong>every login on the estate</strong>. Reported as "the account store holds no account
     * named X" that is a deployment fault wearing a per-account fact — the same wrong diagnosis the
     * 401/403 arm was caught giving for a 500, one arm along, which is why this is a fix and not a
     * nicety.
     *
     * <p>Three shapes, because a body can fail to identify the answerer in three ways and each is a
     * separate branch of {@code answersAbout}: no body at all (a bodyless 404, which is what this
     * endpoint itself answered until the review), a body of the wrong shape entirely, and a body about
     * somebody else.
     */
    @Test
    @DisplayName("a 404 that does not name this login is unavailable, not a fact about the account")
    void aFourOhFourFromSomethingElseIsUnavailable() {
        gateway.willAnswer(404, "");
        assertThatThrownBy(() -> contacts().emailOf(LOGIN))
            .as("a bodyless 404 could have come from any service in the estate")
            .isInstanceOf(CustomerContacts.ContactsUnavailable.class);

        gateway.willAnswer(404, """
            {"type":"https://www.jhipster.tech/problem/problem-with-message","title":"Not Found","status":404}""");
        assertThatThrownBy(() -> contacts().emailOf(LOGIN))
            .as("another Spring service's ProblemDetail is not this endpoint's answer")
            .isInstanceOf(CustomerContacts.ContactsUnavailable.class);

        gateway.willAnswer(404, """
            {"login":"kofi.asante","email":null}""");
        assertThatThrownBy(() -> contacts().emailOf(LOGIN))
            .as("a 404 about somebody else says nothing about the login that was asked for")
            .isInstanceOf(CustomerContacts.ContactsUnavailable.class);
    }

    /**
     * Case three: the question could not be put.
     *
     * <p>Louder than empty, deliberately. Answering empty here would put "this estate holds no email"
     * in the log for an estate that does, pointing whoever reads it at an unimplemented decision that
     * is implemented — which is this repository's most expensive recurring shape and the reason
     * {@code ContactsUnavailable} exists at all.
     */
    @Test
    @DisplayName("an account store that cannot be reached is louder than empty")
    void anUnreachableAccountStoreIsUnavailable() {
        CustomerContacts nowhere = contactsAt("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":1");

        assertThatThrownBy(() -> nowhere.emailOf(LOGIN)).isInstanceOf(CustomerContacts.ContactsUnavailable.class);
    }

    @Test
    @DisplayName("a 500 from the account store is louder than empty")
    void aServerErrorIsUnavailable() {
        gateway.willAnswer(500, "{}");

        assertThatThrownBy(() -> contacts().emailOf(LOGIN)).isInstanceOf(CustomerContacts.ContactsUnavailable.class);
    }

    @Test
    @DisplayName("an answer that is not JSON is louder than empty")
    void anUnreadableAnswerIsUnavailable() {
        gateway.willAnswer(200, "<html>service unavailable</html>");

        assertThatThrownBy(() -> contacts().emailOf(LOGIN)).isInstanceOf(CustomerContacts.ContactsUnavailable.class);
    }

    /**
     * Case four: the gateway refused this service's own credential.
     *
     * <p>A deployment fault rather than a caller's — the two services on different signing keys, or
     * {@code ContactLookupToken} drifted between its two copies. Both statuses, because 401 and 403 are
     * different halves of it (no credential accepted, versus accepted and not authorised) and folding
     * them would let one arm be deleted.
     */
    @Test
    @DisplayName("an account store that refuses this estate's own token is louder than empty")
    void aRefusedTokenIsUnavailable() {
        gateway.willAnswer(401, "");
        assertThatThrownBy(() -> contacts().emailOf(LOGIN)).isInstanceOf(CustomerContacts.ContactsUnavailable.class);

        gateway.willAnswer(403, "");
        assertThatThrownBy(() -> contacts().emailOf(LOGIN)).isInstanceOf(CustomerContacts.ContactsUnavailable.class);
    }

    // ---------------------------------------------------------------- two things nobody asked for

    /**
     * An answer about a different person is refused, and the refusal does not carry the address.
     *
     * <p>This is the one wrong answer that would go through unnoticed: Paystack accepts whatever email
     * it is given, so somebody else would be sent the payment page for this booking. The endpoint
     * echoes the login precisely so that it can be compared, and the comparison is here.
     */
    @Test
    @DisplayName("an answer about somebody else is refused, and the address is not in the message")
    void anAnswerAboutSomebodyElseIsRefused() {
        gateway.willAnswer(200, """
            {"login":"kofi.asante","email":"kofi.asante@example.test"}""");

        assertThatThrownBy(() -> contacts().emailOf(LOGIN))
            .isInstanceOf(CustomerContacts.ContactsUnavailable.class)
            .hasMessageNotContaining("kofi.asante@example.test");
    }

    @Test
    @DisplayName("an empty body is refused rather than read as an account with no address")
    void anEmptyBodyIsRefused() {
        gateway.willAnswer(200, "");

        assertThatThrownBy(() -> contacts().emailOf(LOGIN)).isInstanceOf(CustomerContacts.ContactsUnavailable.class);
    }

    /** No login is no question: nothing is minted, nothing is asked, and nothing is claimed. */
    @Test
    @DisplayName("a blank login asks nothing and mints nothing")
    void aBlankLoginAsksNothing() {
        assertThat(contacts().emailOf(null)).isEmpty();
        assertThat(contacts().emailOf("")).isEmpty();
        assertThat(contacts().emailOf("   ")).isEmpty();
        assertThat(gateway.received()).isEmpty();
    }

    // ---------------------------------------------------------------- fixtures

    private CustomerContacts contacts() {
        return contactsAt(gateway.baseUrl());
    }

    private CustomerContacts contactsAt(String baseUrl) {
        return new GatewayCustomerContacts(
            RestClient.builder(),
            new FanoutTokenMinter(new NimbusJwtEncoder(new ImmutableSecret<>(key))),
            baseUrl,
            2000
        );
    }

    private Jwt decode(String token) {
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(SecurityUtils.JWT_ALGORITHM).build();
        return decoder.decode(token);
    }

    /**
     * The gateway's account store, as far as a socket on loopback can stand in for it.
     *
     * <p>Records every request rather than only the last, because one test asserts that
     * <strong>no</strong> request was made — a refusal before the round trip is the whole behaviour
     * there, and a stub remembering only the most recent call cannot tell "nothing was sent" from
     * "something was sent and then something else was".
     */
    private static final class StubAccountStore {

        private final HttpServer server;
        private final List<Received> received = new ArrayList<>();

        private int status = 200;
        private String body = "{}";

        StubAccountStore() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        void willAnswer(int status, String body) {
            this.status = status;
            this.body = body;
        }

        String baseUrl() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        List<Received> received() {
            return List.copyOf(received);
        }

        Received only() {
            assertThat(received).hasSize(1);
            return received.get(0);
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.isEmpty() ? null : values.get(0)));
                received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers));

                byte[] answer = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                // -1 for an empty body, not 0: HttpServer reads 0 as "chunked, length unknown" and a
                // response promising a body that never arrives hangs until the read timeout, which is
                // a test that passes for the wrong reason after two seconds.
                exchange.sendResponseHeaders(status, answer.length == 0 ? -1 : answer.length);
                exchange.getResponseBody().write(answer);
            }
        }

        record Received(String method, String path, Map<String, String> headers) {
            String header(String name) {
                return headers.get(name);
            }
        }
    }
}
