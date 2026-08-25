package net.jojoaddison.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks the payout service for the brokerage split on an amount, for the receipt modal.
 *
 * <p>Sends the amount and the date, never a booking reference — payout has no way to tell whose
 * booking a reference is, so an endpoint keyed by one would let any authenticated caller read any
 * booking's price. Ownership is established here, in the service that knows, before this is called.
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

    /** @throws PayoutUnavailable when the split cannot be established — never a guessed one. */
    public Split splitFor(long amountMinor, LocalDate on, String authorizationHeader) {
        try {
            Split split = http
                .get()
                .uri(uri -> uri.path("/api/internal/brokerage/split").queryParam("amountMinor", amountMinor).queryParam("on", on).build())
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
