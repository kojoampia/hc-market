package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The receipt asks payout for the split at a <strong>moment</strong>, and sends the day beside it —
 * {@code decisions.md} D56, backlog NEW-16.
 *
 * <h2>Against a real socket, not a mocked client</h2>
 *
 * <p>{@code PaystackPaymentProviderUnitTest}'s pattern, for its reason and one more. What this
 * package changes is a <em>query string</em> that crosses a service boundary: payout binds
 * {@code at} as an {@code Instant} and {@code on} as a {@code LocalDate}, and a client test that
 * substitutes the request factory asserts the arguments a method was called with rather than what
 * went over the wire. The formats are the contract here — {@code 2020-06-01T14:00:00Z} and
 * {@code 2020-06-01} — and only a socket can show them.
 *
 * <h2>What the second assertion is really guarding</h2>
 *
 * <p>That {@code on} is still sent. It is redundant against the payout in this repository and it is
 * the compatibility half of D56: a new booking against an older payout, which ignores {@code at} as
 * an unknown parameter, would fall through to that version's {@code Instant.now()} if the day were
 * dropped — NEW-13 rebuilt in the other service, on a customer's financial statement. Nothing about
 * a green build in either project can see that, which is why it is asserted here and checked again
 * in CI against payout's own parameter list.
 */
class TheReceiptAsksForAMomentAndADayUnitTest {

    /** 2020, so no clock this ever runs under can equal it — the trap D51 recorded. */
    private static final Instant COMPLETED_AT = LocalDateTime.of(2020, 6, 1, 14, 0)
        .atZone(MarketCalendar.MARKET_ZONE)
        .toInstant();

    private static final LocalDate THE_DAY = LocalDate.of(2020, 6, 1);

    private static final String A_SPLIT =
        """
        {"grossMinor":28000,"commissionMinor":8400,"netMinor":19600,
         "commissionRate":"0.30","currency":"GHS","freeCancellationHours":24,"lateCancellationPct":"0.50"}
        """;

    private StubPayout payout;
    private BrokerageClient client;

    @BeforeEach
    void aPayoutServiceIsListening() throws IOException {
        payout = new StubPayout();
        payout.willAnswer(200, A_SPLIT);
        client = new BrokerageClient(RestClient.builder(), payout.baseUrl(), 3000);
    }

    @AfterEach
    void andIsStopped() {
        payout.stop();
    }

    @Test
    @DisplayName("a completed booking sends the completion instant AND the day it fell on")
    void bothParametersGoOnTheWire() {
        client.splitFor(28_000L, COMPLETED_AT, THE_DAY, "Bearer a.customers.token");

        String query = payout.only().query();
        assertThat(query).contains("amountMinor=28000");
        // ISO-8601, which is what payout's Instant binding parses. Asserted as the literal rather
        // than as `COMPLETED_AT.toString()`, so a change to how it is rendered is visible here.
        assertThat(query).contains("at=2020-06-01T14:00:00Z");
        assertThat(query).contains("on=2020-06-01");
    }

    @Test
    @DisplayName("a booking that has not completed sends the day alone — no manufactured midnight")
    void noMomentMeansNoMomentIsSent() {
        client.splitFor(28_000L, null, THE_DAY, "Bearer a.customers.token");

        String query = payout.only().query();
        assertThat(query).contains("on=2020-06-01");
        // Not `at=` either: a parameter present and empty reads as a caller that lost a value.
        assertThat(query).doesNotContain("at=");
        assertThat(query).doesNotContain("at&");
        assertThat(query).doesNotEndWith("at");
    }

    @Test
    @DisplayName("the customer's own token is carried, because payout is the one being asked")
    void theTokenIsForwarded() {
        client.splitFor(28_000L, COMPLETED_AT, THE_DAY, "Bearer a.customers.token");

        assertThat(payout.only().authorization()).isEqualTo("Bearer a.customers.token");
    }

    @Test
    @DisplayName("a payout that refuses is PayoutUnavailable, never a guessed split")
    void aRefusalIsNotAReceipt() {
        payout.willAnswer(503, "{}");

        assertThatThrownBy(() -> client.splitFor(28_000L, COMPLETED_AT, THE_DAY, "Bearer t")).isInstanceOf(
            BrokerageClient.PayoutUnavailable.class
        );
    }

    /** Payout, as far as a socket on loopback can stand in for it. Records every request. */
    private static final class StubPayout {

        private final HttpServer server;
        private final List<Received> received = new ArrayList<>();

        private int status = 200;
        private String body = "{}";

        StubPayout() throws IOException {
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
                received.add(
                    new Received(
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getQuery() == null ? "" : exchange.getRequestURI().getQuery(),
                        exchange.getRequestHeaders().getFirst("Authorization")
                    )
                );
                byte[] answer = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, answer.length);
                exchange.getResponseBody().write(answer);
            }
        }

        record Received(String path, String query, String authorization) {}
    }
}
