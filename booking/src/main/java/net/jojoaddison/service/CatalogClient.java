package net.jojoaddison.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks the catalog service what a service offering actually costs.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Until decisions.md D26, {@code POST /api/bookings} took {@code priceMinor}, {@code currency}
 * and {@code serviceName} straight from the request body and stored them. The client is the
 * customer's browser. So the price of a booking was whatever the caller said it was, and because
 * {@code Ledger} derives its gross, commission and net from the completed booking, a caller could
 * have credited a professional ₵0 for a ₵280 session — or any other number — with nothing anywhere
 * disagreeing. The figures are denormalised on purpose (a receipt must not change when a price is
 * later edited) but denormalised is not the same as unverified.
 *
 * <h2>Fails closed</h2>
 *
 * <p>Same reasoning as {@link BrokerageClient}: what does a wrong answer cost? A booking is a
 * financial commitment on both sides and its price is permanent, so refusing to create one while
 * the catalogue is unreachable is better than creating one at a price nobody authoritative
 * confirmed. "No booking" is recoverable by retrying; a mispriced booking is discovered at payout.
 *
 * <p>Note this deliberately does NOT follow D12's reasoning. D12 keeps {@code professionalLogin} on
 * the booking so the professional's <em>inbox</em> never has to ask catalog — that is a read path,
 * hit constantly, which must keep working when catalog is down. Creating a booking is a single
 * write that must be correct. Availability beats correctness on the read, correctness beats
 * availability on the write.
 *
 * <p>Uses the public profile endpoint, which needs no token: the catalogue is public, and asking
 * for a price is exactly what an anonymous visitor does on the profile screen.
 */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(
        RestClient.Builder builder,
        @Value("${healthconnect.catalog.base-url:http://healthconnectcatalog}") String baseUrl,
        @Value("${healthconnect.catalog.timeout-ms:3000}") int timeoutMs
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * The catalogue's own account of one service offering.
     *
     * @throws CatalogUnavailable if the catalogue cannot be reached — never a guessed price
     * @throws UnknownOffering if the professional or the service does not exist, or the service is
     *     not active
     */
    public ServiceView priceOf(String professionalRef, String serviceRef) {
        ProfessionalDetail detail;
        try {
            detail = http.get().uri("/api/professionals/{ref}", professionalRef).retrieve().body(ProfessionalDetail.class);
        } catch (RestClientException unreachable) {
            throw new CatalogUnavailable("could not reach the catalog service to price this booking");
        }
        if (detail == null || detail.services() == null) {
            throw new UnknownOffering("no such professional: " + professionalRef);
        }
        return detail
            .services()
            .stream()
            .filter(s -> s.ref() != null && s.ref().equals(serviceRef))
            .findFirst()
            // An inactive offering is not bookable. Treated as absent rather than as a distinct
            // error: a professional who has retired a service should not have to explain why.
            .filter(ServiceView::active)
            .orElseThrow(() -> new UnknownOffering("professional " + professionalRef + " does not offer an active service " + serviceRef));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfessionalDetail(List<ServiceView> services) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceView(String ref, String name, long priceMinor, String currency, boolean active) {}

    /** The catalogue could not be asked. Distinct from "it answered and said no". */
    public static class CatalogUnavailable extends RuntimeException {

        public CatalogUnavailable(String message) {
            super(message);
        }
    }

    /** The catalogue answered, and there is no such bookable offering. */
    public static class UnknownOffering extends RuntimeException {

        public UnknownOffering(String message) {
            super(message);
        }
    }
}
