package net.jojoaddison.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the professional's next confirmed appointment from the booking service, for the Overview
 * screen's "next up" card.
 *
 * <p>Forwards the caller's own token rather than using a service account, exactly as catalog's
 * {@code BookingClient} does — booking then answers as that professional and applies its own
 * ownership rule, so this client cannot be used to read somebody else's schedule.
 *
 * <p><strong>Failures are swallowed on purpose</strong>, which is the opposite of the decision made
 * in catalog's review check. The difference is what is at stake: there, failing closed protects an
 * integrity rule, because accepting a review that might be unearned is worse than refusing a valid
 * one. Here the cost of failing closed is an entire earnings screen going blank because booking is
 * restarting — so the screen renders without the card, and says so via {@code nextUpAvailable}
 * rather than pretending the professional has nothing booked.
 */
@Component
public class BookingScheduleClient {

    private static final Logger LOG = LoggerFactory.getLogger(BookingScheduleClient.class);

    private final RestClient http;

    public BookingScheduleClient(
        RestClient.Builder builder,
        @Value("${healthconnect.booking.base-url:http://healthconnectbooking}") String baseUrl,
        @Value("${healthconnect.booking.timeout-ms:2000}") int timeoutMs
    ) {
        // EXPLICIT TIMEOUTS, and they are the difference between degrading and hanging.
        //
        // Without them a booking service that is down does not make this card disappear — it makes
        // the whole Overview request block until the platform default gives up, which on a
        // connection to a host that is refusing is long enough that the screen simply never loads.
        // "Graceful degradation" that takes thirty seconds to degrade is an outage with extra steps.
        //
        // Two seconds is chosen against what this call is worth: it decorates one card on a screen
        // whose other six figures are already in hand. Waiting longer than that for it is a bad
        // trade however healthy booking usually is.
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));
        this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** @return empty when booking is unreachable OR when there is genuinely nothing booked. */
    public Optional<ScheduleDay> nextConfirmedDay(String authorizationHeader) {
        try {
            List<ScheduleDay> days = http
                .get()
                .uri("/api/pro/schedule")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<ScheduleDay>>() {});
            if (days == null) {
                return Optional.empty();
            }
            return days.stream().filter(d -> d.bookings() != null && !d.bookings().isEmpty()).findFirst();
        } catch (Exception e) {
            LOG.warn("could not read the schedule for the overview: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleDay(String date, List<BookingView> bookings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingView(String reference, String customerName, String serviceName, String scheduledDate, String scheduledTime, String deliveryMode) {}
}
