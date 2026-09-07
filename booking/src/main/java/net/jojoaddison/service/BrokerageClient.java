package net.jojoaddison.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks the payout service for the brokerage split on an amount, for the receipt modal.
 *
 * <p>Sends the amount and when it is being asked about, never a booking reference — payout has no
 * way to tell whose booking a reference is, so an endpoint keyed by one would let any authenticated
 * caller read any booking's price. Ownership is established here, in the service that knows, before
 * this is called.
 *
 * <p><strong>Fails closed</strong>, unlike payout's own call into booking for the Overview card.
 * The difference is what a wrong answer costs: an overview missing one card is a smaller harm than a
 * receipt showing a commission that was never charged. A receipt is a financial statement, and no
 * receipt is better than a plausible wrong one.
 */
@Component
public class BrokerageClient {

    private final RestClient http;

    public BrokerageClient(
        RestClient.Builder builder,
        @Value("${healthconnect.payout.base-url:http://healthconnectpayout}") String baseUrl,
        @Value("${healthconnect.payout.timeout-ms:3000}") int timeoutMs
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * The split on an amount, struck at a stated moment and — separately — on a stated day.
     *
     * <h2>Both are sent, and that is the decision rather than an oversight</h2>
     *
     * <p>{@code decisions.md} D56, backlog NEW-16. {@code at} is the answer payout prefers: the rate
     * is versioned by an {@code effectiveFrom} that need not be midnight, so a receipt struck to the
     * day prices a booking completed at 14:00 under terms that stopped applying at noon — while the
     * ledger row behind the same booking, since D53, is priced at the instant. Two numbers about one
     * booking, disagreeing, with nothing in either service able to say afterwards which rate was
     * used.
     *
     * <p>{@code on} stays because <strong>this call crosses a deployment boundary in both
     * directions</strong>. A new booking against an <em>old</em> payout, which knows nothing of
     * {@code at} and ignores it as an unknown parameter, falls through to whatever {@code on} gives —
     * and had {@code on} been dropped, to that version's {@code Instant.now()}, which is NEW-13
     * rebuilt in the other service, on the path where a customer is reading a financial statement.
     * The two services roll independently ({@code deploy-prod.sh} takes {@code --services}), so that
     * window is real rather than theoretical. It is also the honest answer for a caller that has no
     * moment: a receipt for a booking that has not completed is struck on its scheduled day.
     *
     * <p>An old booking against a new payout is the same request this method used to send, and
     * payout's {@code on}-only branch is unchanged.
     *
     * @param at the moment to strike the split at, or {@code null} when the caller genuinely has
     *           none. Never a clock reading substituted for one.
     * @param on the day to strike it on. Always sent, never null.
     * @throws PayoutUnavailable when the split cannot be established — never a guessed one.
     */
    public Split splitFor(long amountMinor, Instant at, LocalDate on, String authorizationHeader) {
        try {
            Split split = http
                .get()
                .uri(uri -> {
                    uri.path("/api/internal/brokerage/split").queryParam("amountMinor", amountMinor);
                    // Omitted rather than sent empty when there is no moment: `at=` would bind to
                    // null on the far side anyway, and a parameter that is present and blank reads
                    // as a caller that lost a value rather than one that never had it.
                    if (at != null) {
                        uri.queryParam("at", at);
                    }
                    return uri.queryParam("on", on).build();
                })
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(Split.class);
            if (split == null) {
                throw new PayoutUnavailable("the payout service returned no split for " + amountMinor);
            }
            return split;
        } catch (RestClientException e) {
            throw new PayoutUnavailable("could not reach the payout service to price this receipt");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Split(
        long grossMinor,
        long commissionMinor,
        long netMinor,
        String commissionRate,
        String currency,
        int freeCancellationHours,
        String lateCancellationPct
    ) {}

    public static class PayoutUnavailable extends RuntimeException {
        public PayoutUnavailable(String message) {
            super(message);
        }
    }
}
