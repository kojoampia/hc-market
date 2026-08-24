package net.jojoaddison.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Catalog's read-only window onto the booking service, used by {@code POST /api/reviews} to prove a
 * review is earned.
 *
 * <h2>The caller's token is forwarded, not a service account</h2>
 *
 * <p>This client passes the customer's own {@code Authorization} header through to booking, so
 * booking answers as that customer and enforces its own ownership rule. A service-to-service
 * credential would let catalog read <em>any</em> booking, which would make catalog's review check
 * the only thing standing between a customer and someone else's booking history — a much worse
 * place for that guarantee to live.
 *
 * <p>Consequence worth knowing: this is a synchronous hop, so publishing a review fails while
 * booking is down. That is the right failure. The alternative is accepting reviews that may turn
 * out to be unearned, and the whole point of spec §9 is that a review is backed by a session that
 * actually happened.
 */
@Component
public class BookingClient {

    private static final Logger LOG = LoggerFactory.getLogger(BookingClient.class);

    private final RestClient http;

    public BookingClient(RestClient.Builder builder, @Value("${healthconnect.booking.base-url:http://healthconnectbooking}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
        LOG.info("booking service resolved at {}", baseUrl);
    }

    /**
     * Fetches one booking as the calling customer.
     *
     * @return empty when booking says 404 — which it does both for "no such booking" and for
     *         "not yours", deliberately, so this cannot be used to probe for other people's
     *         references.
     */
    public Optional<BookingSummary> findBooking(String reference, String authorizationHeader) {
        try {
            BookingDetailResponse response = http
                .get()
                .uri("/api/bookings/{ref}", reference)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new NotFound();
                })
                .body(BookingDetailResponse.class);
            return Optional.ofNullable(response).map(BookingDetailResponse::booking);
        } catch (NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new BookingUnavailable("could not reach the booking service to verify " + reference, e);
        }
    }

    /** Tells booking a review has landed. Returns false when booking says it is already reviewed. */
    public boolean markReviewed(String reference, String authorizationHeader) {
        try {
            http
                .post()
                .uri("/api/bookings/{ref}/reviewed", reference)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new NotFound();
                })
                .toBodilessEntity();
            return true;
        } catch (NotFound e) {
            return false;
        } catch (RestClientException e) {
            throw new BookingUnavailable("could not reach the booking service to flag " + reference, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingDetailResponse(BookingSummary booking) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingSummary(
        String reference,
        String customerLogin,
        String customerName,
        String professionalRef,
        String status,
        boolean reviewed
    ) {}

    /** Internal signal; never escapes this class. */
    private static final class NotFound extends RuntimeException {
        NotFound() {
            super(null, null, false, false);
        }
    }

    /** Booking is unreachable, which is different from the booking not existing. */
    public static class BookingUnavailable extends RuntimeException {
        public BookingUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
