package net.jojoaddison.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The two remote legs of an orchestrated erasure — {@code decisions.md} D38.
 *
 * <p>Booking erases itself in process; messaging and catalog are HTTP calls to the desk endpoints
 * they already had, presenting the short-lived token {@link FanoutTokenMinter} signs with the estate
 * key. Container to container, not through the gateway — the same shape as {@link CatalogClient}, and
 * for the same reason: the gateway is the ingress for the outside, not a hop the estate uses to talk
 * to itself.
 *
 * <p><strong>Every failure is returned, none is thrown past the orchestrator.</strong> A leg that
 * cannot be reached, one that refuses, and one that answers something unreadable are all
 * {@link LegFailed}, carrying a sentence an operator can act on — including the root cause's type,
 * which is what distinguishes them, because Spring does not. The whole point of this package is
 * that a partially erased customer used to look exactly like a fully erased one, so a failure that
 * merely aborts the request would reproduce it from the other side — the operator would know the
 * call failed and not which parts of it had already happened.
 *
 * <p><strong>The counts are copied through, not modelled.</strong> Each service's receipt is its own
 * business and they have different shapes; this pulls out the pseudonym and keeps every remaining
 * integer field under the name the remote service gave it. So a count added to messaging's receipt
 * tomorrow appears in the fan-out receipt with no change here, and — more to the point — a count
 * <em>removed</em> there cannot be silently reported as a zero by a record in this service that still
 * declares it.
 */
@Component
public class ErasureFanoutClient {

    private static final String ERASE = "/api/desk/customers/{login}/erase";

    private final RestClient messaging;
    private final RestClient catalog;

    public ErasureFanoutClient(
        RestClient.Builder builder,
        @Value("${healthconnect.messaging.base-url:http://healthconnectmessaging}") String messagingBaseUrl,
        @Value("${healthconnect.catalog.base-url:http://healthconnectcatalog}") String catalogBaseUrl,
        @Value("${healthconnect.erasure.timeout-ms:10000}") int timeoutMs
    ) {
        this.messaging = client(builder, messagingBaseUrl, timeoutMs);
        this.catalog = client(builder, catalogBaseUrl, timeoutMs);
    }

    /**
     * A generous timeout, and deliberately not {@link CatalogClient}'s three seconds. That one guards
     * a customer waiting on a booking screen; this one guards a redaction sweep over several tables,
     * run once per data subject request by somebody who is already waiting for a receipt. Timing out
     * early here would report a leg as failed while it went on succeeding, which is the one wrong
     * answer this receipt must not give.
     */
    private static RestClient client(RestClient.Builder builder, String baseUrl, int timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * Messaging's leg, carrying the customer's booking references.
     *
     * <p>Those references are the whole reason this payload exists — {@code decisions.md} D36's design
     * note. Booking is authoritative for which bookings a customer has and messaging is not: threads
     * are deduped by professional, so a repeat booking's reference appears on no conversation, and a
     * booking still pending when the erasure runs has no customer-side notification either. Handing
     * the list over closes that residual by construction rather than narrowing it again.
     */
    public LegReceipt eraseInMessaging(String login, List<String> bookingReferences, String token) {
        return call(messaging.post().uri(ERASE, login).body(Map.of("bookingReferences", bookingReferences)), token, "messaging");
    }

    /**
     * Catalog's leg. No payload: nothing catalog holds is keyed to a booking, so the reference list
     * would be a person's booking history disclosed to a service that has no use for it.
     */
    public LegReceipt eraseInCatalog(String login, String token) {
        return call(catalog.post().uri(ERASE, login), token, "catalog");
    }

    private LegReceipt call(RestClient.RequestHeadersSpec<?> request, String token, String service) {
        Map<String, Object> body;
        try {
            body = request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException refused) {
            // It answered, and said no. The status is the useful half — 403 is a token or an
            // authority, 503 is the missing pepper D35 refuses without, 500 is its own problem.
            throw new LegFailed(service + " refused the erasure: HTTP " + refused.getStatusCode().value(), refused);
        } catch (RestClientException noUsableAnswer) {
            // Everything else, under one honest heading. It was two branches for a while — "could not
            // be reached" for a ResourceAccessException and "answered something unreadable" for the
            // rest — and that boundary is in the wrong place: a read timeout, which is the single most
            // likely real failure here, comes back as a plain RestClientException from the message
            // converter rather than as a ResourceAccessException, so the confident branch would have
            // told an operator the service had answered when it had not said a word.
            //
            // The root cause's type is what actually separates them, so it goes in the message rather
            // than into a classification this code would get wrong: SocketTimeoutException is a leg
            // that never answered, ConnectException is a container that is not there, and a Jackson
            // exception is one that answered something else entirely.
            throw new LegFailed(
                service + " gave no usable answer (" + rootCauseOf(noUsableAnswer) + "): " + noUsableAnswer.getMessage(),
                noUsableAnswer
            );
        }
        if (body == null) {
            throw new LegFailed(service + " answered with no receipt", null);
        }
        Object pseudonym = body.get("pseudonym");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Object> field : body.entrySet()) {
            if (field.getValue() instanceof Number count) {
                counts.put(field.getKey(), count.intValue());
            }
        }
        return new LegReceipt(pseudonym instanceof String alias ? alias : null, counts);
    }

    /** The deepest cause's simple name — the one word that tells a timeout from a parse failure. */
    private static String rootCauseOf(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    /**
     * One service's account of what it erased.
     *
     * @param pseudonym the alias that service wrote into its rows. Compared against booking's own by
     *     the orchestrator, which is the first thing in this estate able to notice that the three
     *     services are running different peppers — D35 requires the value to be identical in booking,
     *     catalog and messaging, and until now nothing checked. Three peppers means one person with
     *     three aliases and rows that can never be reconciled again, and the symptom is nothing at all
     * @param counts every integer field of that service's receipt, under its own name
     */
    public record LegReceipt(String pseudonym, Map<String, Integer> counts) {}

    /** A leg that did not erase. Always caught by the orchestrator and reported, never propagated. */
    public static class LegFailed extends RuntimeException {

        public LegFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
