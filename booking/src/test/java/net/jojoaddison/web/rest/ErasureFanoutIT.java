package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.repository.BookingRepository;
import net.jojoaddison.security.ErasureFanoutToken;
import net.jojoaddison.security.MarketplaceAuthorities;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.FanoutTokenMinter;
import net.jojoaddison.service.SubjectPseudonym;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/desk/customers/{login}/erase-everywhere} — {@code decisions.md} D37 and D38.
 *
 * <h2>Why the legs are stood up over real HTTP instead of mocked</h2>
 *
 * <p>Half of what this package delivers is a <em>credential</em>, and a mocked
 * {@code ErasureFanoutClient} would prove the orchestration while proving nothing at all about the
 * token — which is the part that has to be right, because it is signed with the key every service in
 * the estate validates against. So messaging and catalog are two throwaway {@code HttpServer}s on
 * loopback, and the assertions read the {@code Authorization} header they actually received and
 * decode it with this service's own {@link JwtDecoder}. That is as close to the real handshake as one
 * repository holding five standalone Maven projects can get: D28 records the same limitation and the
 * same conclusion, that a green suite here says nothing about the wire until something reads it.
 *
 * <h2>What the stubs are allowed to be</h2>
 *
 * <p>They answer with whatever each test sets, including a 500 and a receipt carrying the wrong
 * pseudonym, because the receipt shape on a bad day is the entire point of the endpoint. A stub that
 * only ever succeeds would test the one path that was never in doubt.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ErasureFanoutIT {

    private static final String URL = "/api/desk/customers/{login}/erase-everywhere";
    private static final String SINGLE = "/api/desk/customers/{login}/erase";
    private static final String CUSTOMER = "ama.tobeforgotten";
    private static final String BYSTANDER = "kojo.stillhere";

    /** One request a stub received, kept so the token and the payload can be read back. */
    private record Call(String path, String authorization, String body) {}

    /**
     * What a stub should answer next.
     *
     * @param hang sleep past the client's read timeout instead of answering. This is how a genuinely
     *     unreachable leg is produced from inside one test context, and it had to be done properly:
     *     the first attempt closed the connection early, which the JDK's server turns into an empty
     *     200 rather than a broken socket — so the test passed while exercising a <em>parse</em>
     *     failure under a message that said "could not be reached". A hung service is also the more
     *     realistic outage: a container that is up, accepting connections and doing nothing
     */
    private record Answer(int status, String body, boolean hang) {}

    private static final List<Call> MESSAGING_CALLS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Call> CATALOG_CALLS = Collections.synchronizedList(new ArrayList<>());

    private static volatile Answer messagingAnswer;
    private static volatile Answer catalogAnswer;

    /** The read timeout the clients are built with in this class, and what {@code hang} outlasts. */
    private static final int TIMEOUT_MS = 1500;

    private static final HttpServer MESSAGING = stub(MESSAGING_CALLS, () -> messagingAnswer);
    private static final HttpServer CATALOG = stub(CATALOG_CALLS, () -> catalogAnswer);

    /**
     * Both stubs are on ephemeral ports, so the addresses cannot be written into a properties file.
     * They are started in a static initialiser rather than {@code @BeforeAll} because the registry
     * below is consulted while the context is being built, which is earlier than either.
     */
    @DynamicPropertySource
    static void addressTheStubs(DynamicPropertyRegistry registry) {
        registry.add("healthconnect.messaging.base-url", () -> "http://127.0.0.1:" + MESSAGING.getAddress().getPort());
        registry.add("healthconnect.catalog.base-url", () -> "http://127.0.0.1:" + CATALOG.getAddress().getPort());
        // Ten seconds is right in production and useless in a test. Short enough that a hung leg is a
        // second rather than ten, long enough that a loopback stub answering in microseconds is never
        // in danger of it.
        registry.add("healthconnect.erasure.timeout-ms", () -> TIMEOUT_MS);
    }

    @AfterAll
    static void stopTheStubs() {
        MESSAGING.stop(0);
        CATALOG.stop(0);
    }

    private static HttpServer stub(List<Call> calls, Supplier<Answer> answer) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, calls, answer.get()));
            // A thread pool rather than the default in-line dispatcher: a hung answer must not still
            // be occupying the server when the next test asks it something.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException impossible) {
            throw new IllegalStateException("could not start a loopback stub", impossible);
        }
    }

    private static void respond(HttpExchange exchange, List<Call> calls, Answer answer) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(new Call(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION), body));
        byte[] out = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (answer.hang()) {
            try {
                Thread.sleep(TIMEOUT_MS * 3L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(answer.status(), out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private EntityManager em;

    @Autowired
    private SubjectPseudonym pseudonyms;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private FanoutTokenMinter minter;

    private Booking first;
    private Booking repeat;

    /**
     * Two bookings for the customer and one for somebody else.
     *
     * <p>Two because the reference list is the payload this package exists to get right — one booking
     * would let a fan-out that sent the first reference it found pass — and a second customer because
     * every erasure fixture here has carried one since D34, when the tests that seeded exactly one
     * person turned out to be unable to notice a sweep that erased the estate.
     */
    @BeforeEach
    void twoBookingsAndABystander() {
        MESSAGING_CALLS.clear();
        CATALOG_CALLS.clear();
        messagingAnswer = receipt(messagingReceipt(pseudonyms.of(CUSTOMER), 1, 2, 1, 3));
        catalogAnswer = receipt("{\"pseudonym\":\"" + pseudonyms.of(CUSTOMER) + "\",\"reviewsDeidentified\":4}");

        first = bookings.saveAndFlush(BookingResourceIT.createEntity(em).reference("b-first").customerLogin(CUSTOMER).customerName("Ama"));
        repeat = bookings.saveAndFlush(
            BookingResourceIT.createEntity(em).reference("b-repeat").customerLogin(CUSTOMER).customerName("Ama")
        );
        bookings.saveAndFlush(BookingResourceIT.createEntity(em).reference("b-other").customerLogin(BYSTANDER).customerName("Kojo"));
    }

    private static Answer receipt(String body) {
        return new Answer(200, body, false);
    }

    private static Answer hangs() {
        return new Answer(200, "", true);
    }

    private static String messagingReceipt(String pseudonym, int threads, int messages, int reKeyed, int redacted) {
        return (
            "{\"pseudonym\":\"" +
            pseudonym +
            "\",\"conversationsPseudonymised\":" +
            threads +
            ",\"messagesErased\":" +
            messages +
            ",\"notificationsReKeyed\":" +
            reKeyed +
            ",\"notificationsRedacted\":" +
            redacted +
            "}"
        );
    }

    private Jwt tokenPresentedTo(List<Call> calls) {
        String authorization = calls.get(0).authorization();
        assertThat(authorization).startsWith("Bearer ");
        return decoder.decode(authorization.substring("Bearer ".length()));
    }

    @SuppressWarnings("unchecked")
    private List<String> referencesSentToMessaging() throws Exception {
        Map<String, Object> payload = om.readValue(MESSAGING_CALLS.get(0).body(), Map.class);
        return (List<String>) payload.get("bookingReferences");
    }

    /**
     * <strong>The package, in one call.</strong> One operator action, three services erased, and a
     * receipt naming each of them — where before this it was three calls whose individual receipts
     * were indistinguishable from a complete erasure.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("one call erases booking, messaging and catalog, and says so leg by leg")
    void erasesEverywhereAndReportsEachLeg() throws Exception {
        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.complete").value(true))
            .andExpect(jsonPath("$.pseudonym").value(pseudonyms.of(CUSTOMER)))
            .andExpect(jsonPath("$.bookingReferences").value(2))
            .andExpect(jsonPath("$.services[0].service").value("booking"))
            .andExpect(jsonPath("$.services[0].status").value("ERASED"))
            .andExpect(jsonPath("$.services[0].counts.bookingsErased").value(2))
            .andExpect(jsonPath("$.services[1].service").value("messaging"))
            .andExpect(jsonPath("$.services[1].status").value("ERASED"))
            .andExpect(jsonPath("$.services[1].counts.notificationsRedacted").value(3))
            .andExpect(jsonPath("$.services[2].service").value("catalog"))
            .andExpect(jsonPath("$.services[2].status").value("ERASED"))
            .andExpect(jsonPath("$.services[2].counts.reviewsDeidentified").value(4));

        // The local leg really ran, rather than being reported as having run.
        assertThat(bookings.findById(first.getId()).orElseThrow().getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(bookings.findById(repeat.getId()).orElseThrow().getCustomerName()).isEqualTo("[erased]");
        // And the bystander is untouched, which is the assertion D34 added to every erasure fixture.
        assertThat(bookings.findAll().stream().filter(b -> BYSTANDER.equals(b.getCustomerLogin()))).hasSize(1);
    }

    /**
     * <strong>The payload D36 asked for, before WP-07 was built.</strong>
     *
     * <p>Messaging cannot find a notification about a booking it holds no thread for — a repeat with
     * one professional shares the first booking's thread, and a booking still pending has raised
     * nothing to the customer. Booking is authoritative for that list, so the fan-out hands it over
     * and the residual closes by construction. Asserted here as well as in messaging's own suite
     * because this is the side that has to <em>send</em> them, and a payload silently reduced to a
     * login is exactly the regression D36 predicted somebody would introduce.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("messaging is handed every one of the customer's booking references, and nobody else's")
    void handsMessagingTheBookingReferences() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        assertThat(referencesSentToMessaging()).containsExactlyInAnyOrder("b-first", "b-repeat");
        // Catalog gets no payload at all: nothing it holds is keyed to a booking, so the list would be
        // a person's booking history disclosed to a service with no use for it.
        assertThat(CATALOG_CALLS.get(0).body()).isEmpty();
    }

    /**
     * <strong>The credential, read off the wire.</strong>
     *
     * <p>Every narrowing D37 asked for is here and each one is checked on the token that was actually
     * presented, not on the code that built it: one authority used by nothing else, a subject that is
     * not a person, the customer's login so it cannot be replayed against anybody else, and thirty
     * seconds.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("the token both legs receive is narrow, impersonal and short-lived")
    void mintsANarrowShortLivedToken() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());

        for (List<Call> calls : List.of(MESSAGING_CALLS, CATALOG_CALLS)) {
            Jwt token = tokenPresentedTo(calls);
            assertThat(token.getClaimAsString(SecurityUtils.AUTHORITIES_CLAIM)).isEqualTo(MarketplaceAuthorities.CUSTOMER_ERASURE);
            // Not ROLE_BROKERAGE, which also resolves disputes and grants verification badges.
            assertThat(token.getClaimAsString(SecurityUtils.AUTHORITIES_CLAIM)).doesNotContain(MarketplaceAuthorities.BROKERAGE);
            // Not the operator's login: a leaked token must not be a bearer credential for a person.
            assertThat(token.getSubject()).isEqualTo(ErasureFanoutToken.SUBJECT).isNotEqualTo("desk");
            assertThat(token.getClaimAsString(ErasureFanoutToken.SUBJECT_CLAIM)).isEqualTo(CUSTOMER);
            assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(ErasureFanoutToken.LIFETIME);
        }
    }

    /**
     * <strong>The failure this whole package exists to make visible.</strong>
     *
     * <p>Messaging succeeds, catalog falls over, and before D38 the operator's only evidence was two
     * receipts they had to remember to compare. Now it is one receipt saying which leg did not happen
     * and a status code that disagrees with any caller reading nothing else — 502 rather than a 2xx,
     * because mis-reading a 502 costs a retry of an idempotent call and mis-reading a 207 costs a
     * partial erasure filed as a complete one.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a leg that fails is named in the receipt, and the call is not a success")
    void aFailedLegIsReportedAndNotSwallowed() throws Exception {
        catalogAnswer = new Answer(500, "{\"title\":\"Internal Server Error\"}", false);

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.services[1].status").value("ERASED"))
            .andExpect(jsonPath("$.services[2].service").value("catalog"))
            .andExpect(jsonPath("$.services[2].status").value("FAILED"))
            .andExpect(jsonPath("$.services[2].failure").value(containsString("500")))
            // A failed leg reports no counts. A zero and an unknown must not read the same on the
            // sheet that gets filed against a data subject request.
            .andExpect(jsonPath("$.services[2].counts").isEmpty());

        // And the legs that could run, ran. Refusing to try catalog because messaging had failed —
        // or unwinding booking's own erasure — would leave more of the person's data in place.
        assertThat(bookings.findById(first.getId()).orElseThrow().getCustomerLogin()).isEqualTo(pseudonyms.of(CUSTOMER));
        assertThat(MESSAGING_CALLS).hasSize(1);
    }

    /**
     * A leg that cannot be reached at all, which is the ordinary shape of the failure: a container
     * that is restarting, a base URL nobody set, a service that has stopped answering. Distinguished
     * from a refusal in the message, because "could not be reached" and "answered 403" send an
     * operator to different places.
     *
     * <p>Produced as a <strong>read timeout</strong>, and that detail is the lesson rather than an
     * implementation note. The first version of this test closed the connection early instead, which
     * the JDK's {@code HttpServer} turns into an empty 200 — so it went green while exercising a JSON
     * parse failure, under a message asserting the service could not be reached. Two things were wrong
     * at once and the test agreed with both of them.
     *
     * <p>Fixing the fixture then found the second one: a read timeout does <em>not</em> arrive as a
     * {@code ResourceAccessException}, so the client's "could not be reached" branch would never have
     * fired for the most likely real outage. Hence the assertion on {@code SocketTimeoutException} —
     * the root cause's type is the triage signal, and it is in the message because the classification
     * this service would otherwise have made is one it gets wrong.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("an unreachable leg is reported as unreachable, not as a refusal")
    void anUnreachableLegIsReported() throws Exception {
        messagingAnswer = hangs();

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.services[1].status").value("FAILED"))
            .andExpect(jsonPath("$.services[1].failure").value(containsString("gave no usable answer")))
            .andExpect(jsonPath("$.services[1].failure").value(containsString("SocketTimeoutException")))
            // Catalog is still attempted. This is the assertion that says "one leg down does not abort
            // the rest" in a way a comment cannot: refusing to try it would leave MORE of the person's
            // data in place, not less.
            .andExpect(jsonPath("$.services[2].status").value("ERASED"));
    }

    /**
     * <strong>Different peppers, which nothing in this estate could previously notice.</strong>
     *
     * <p>D35 requires {@code HEALTHCONNECT_PRIVACY_PEPPER} to be identical in booking, catalog and
     * messaging, and injects it three times from three compose entries. If they diverge, all three
     * services keep working perfectly and one person acquires three aliases whose rows can never be
     * reconciled again — and there is no way back, because a pseudonym does not invert. The fan-out is
     * the first thing that ever compares them, so it does.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a leg that writes a different alias is a mismatch, not a success")
    void aDifferentAliasIsReportedAsAMismatch() throws Exception {
        messagingAnswer = receipt(messagingReceipt("erased-0000000000000000", 1, 0, 0, 0));

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.services[1].status").value("ALIAS_MISMATCH"))
            .andExpect(jsonPath("$.services[1].failure").value(containsString("pepper")))
            // The rows WERE redacted, so the counts are kept — this needs a deployment fixed and a
            // reconciliation by hand, not a retry.
            .andExpect(jsonPath("$.services[1].counts.conversationsPseudonymised").value(1));
    }

    /**
     * <strong>Retried, because erasure requests are.</strong> They arrive by email and get forwarded,
     * and after a 502 the operator's instruction is simply to call this again.
     *
     * <p>The assertion that matters is not the zeroes; it is that messaging is <em>still</em> handed
     * both booking references on the second run. The list is read back under the alias after the local
     * erasure rather than collected on the way past, precisely so that a retry — which by definition
     * finds nothing under the original login — does not quietly call messaging with an empty payload
     * and reopen D36's residual on the one path most likely to hit it.
     */
    @Test
    @Transactional
    @WithMockUser(username = "desk", authorities = "ROLE_BROKERAGE")
    @DisplayName("a second run is a safe no-op that still hands over the references")
    void isIdempotent() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isOk());
        MESSAGING_CALLS.clear();
        CATALOG_CALLS.clear();
        messagingAnswer = receipt(messagingReceipt(pseudonyms.of(CUSTOMER), 0, 0, 0, 0));
        catalogAnswer = receipt("{\"pseudonym\":\"" + pseudonyms.of(CUSTOMER) + "\",\"reviewsDeidentified\":0}");

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.complete").value(true))
            .andExpect(jsonPath("$.services[0].counts.bookingsErased").value(0))
            .andExpect(jsonPath("$.services[1].counts.messagesErased").value(0))
            .andExpect(jsonPath("$.bookingReferences").value(2));

        assertThat(referencesSentToMessaging()).containsExactlyInAnyOrder("b-first", "b-repeat");
    }

    /**
     * <strong>Where the fan-out authority must not be accepted.</strong>
     *
     * <p>It permits being a <em>leg</em> of an erasure, and booking is never one — it is the service
     * that mints the token. Accepting it here would let a fan-out token trigger a fan-out, which is
     * the "any service may call anything" credential D37 says this must not become, arriving by the
     * shortest possible route.
     */
    @Test
    @Transactional
    @DisplayName("a fan-out token is refused by the service that mints them")
    void theFanOutAuthorityIsRefusedByBooking() throws Exception {
        String token = minter.forErasureOf(CUSTOMER);

        mockMvc
            .perform(post(URL, CUSTOMER).with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
        mockMvc
            .perform(post(SINGLE, CUSTOMER).with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());

        assertThat(bookings.findById(first.getId()).orElseThrow().getCustomerLogin()).isEqualTo(CUSTOMER);
        assertThat(MESSAGING_CALLS).isEmpty();
    }

    @Test
    @Transactional
    @WithMockUser(username = "ordinary.customer", authorities = "ROLE_USER")
    @DisplayName("an ordinary user cannot erase anyone anywhere")
    void ordinaryUserIsRefused() throws Exception {
        mockMvc.perform(post(URL, CUSTOMER).with(csrf())).andExpect(status().isForbidden());
        assertThat(MESSAGING_CALLS).isEmpty();
    }
}
