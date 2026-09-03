package net.jojoaddison.web.rest;

import java.util.Locale;
import java.util.Map;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentConfirmations;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Where a payment provider tells us what became of a payment — {@code decisions.md} D43.
 *
 * <h2>The contract, stated in full because a webhook is a public attack surface</h2>
 *
 * <p><strong>The endpoint.</strong> {@code POST /webhooks/payments/{provider}}, where {@code provider}
 * is the name the platform knows the provider by. The body is whatever the provider sends, taken as
 * text and passed on untouched — the signature covers the bytes, so parsing and re-serialising it
 * would verify against something the provider never sent.
 *
 * <p><strong>The authentication is the provider's signature, and nothing else.</strong> There is no
 * token: a provider cannot be given one, and every scheme that pretends otherwise ends in a shared
 * secret in a query string. {@link PaymentProvider#readCallback} verifies the request by the
 * provider's own scheme and is the only thing standing between a stranger's POST and a booking being
 * created or cancelled. An unauthenticated caller — no signature, a wrong signature, a body that is
 * not this provider's shape, a provider name nothing is configured for — gets <strong>401 with no
 * detail</strong>. Not 400, not 403, not a message saying which part was wrong: an endpoint that
 * explains its refusals is an oracle for constructing one that is not refused.
 *
 * <p><strong>Today every caller gets 401</strong>, because the only provider in the estate is the
 * unconfigured one and it refuses every callback by definition (D15). That is the correct behaviour
 * for an estate that collects no money, not a placeholder.
 *
 * <p><strong>What it does.</strong> A verified callback is handed to {@link PaymentConfirmations},
 * which finds the payment by the provider's handle and, if its booking is still waiting, confirms it
 * into {@code REQUESTED} — publishing the {@code booking.requested} that was withheld at creation — or
 * cancels it. Nothing else. It does not create bookings, does not read anything the callback says
 * about amounts or customers, and cannot move a booking that has left {@code PENDING_PAYMENT}: the
 * only fields taken from the callback are the provider's handle and its verdict.
 *
 * <p><strong>The same callback twice.</strong> 200, and nothing happens the second time. Idempotency
 * is decided from the booking's own state under a row lock, so a duplicate — which all three providers
 * send, and every one of them retries until it gets a 2xx — finds no legal transition and reports
 * success. The one case that answers 404 is a callback that arrives <em>before</em> the booking has
 * been written, which is a genuine race the provider's retry resolves.
 *
 * <h2>What actually keeps this off the internet today</h2>
 *
 * <p>The same thing that keeps catalog's {@code /internal/**} private — {@code decisions.md} D28. The
 * gateway's four routes match {@code /services/<service>/api/**} and nothing else, and this path is
 * not under {@code /api}, so <strong>no request from outside can be routed here in any
 * environment</strong>. That is deliberate for as long as there is no provider: an unauthenticated
 * endpoint nobody legitimate calls should not be reachable, whatever it does with what it receives.
 *
 * <p>WP-13 makes it reachable, and doing that is one line per environment beside the provider's
 * configuration:
 *
 * <pre>
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_ID: healthconnectbooking-webhooks
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_URI: http://&lt;booking&gt;:8080
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_PREDICATES_0: Path=/services/healthconnectbooking/webhooks/**
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_FILTERS_0: StripPrefix=2
 * </pre>
 *
 * <p>It also needs the gateway to stop demanding a token for it — the generated gateway security ends
 * with {@code .pathMatchers("/services/**").authenticated()}, so the route alone would return 401
 * before routing, exactly as it did for the public catalogue reads that
 * {@code MarketplacePublicRouteConfiguration} exists to let through. Whoever wires a provider does
 * both or neither; a route without the permit is a webhook that silently never arrives, which reads as
 * a broken provider integration.
 */
@RestController
@RequestMapping("/webhooks/payments")
public class PaymentWebhookResource {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentWebhookResource.class);

    private final PaymentProvider provider;
    private final PaymentConfirmations confirmations;

    public PaymentWebhookResource(PaymentProvider provider, PaymentConfirmations confirmations) {
        this.provider = provider;
        this.confirmations = confirmations;
    }

    /**
     * A provider's callback.
     *
     * <p>{@code consumes = ALL} because providers post form bodies as readily as JSON and a 415 to a
     * genuine callback is a payment stuck for as long as the provider retries. The body is read as a
     * String for the reason on {@link PaymentCallback}: it must reach the verifier as it arrived.
     *
     * @return a body naming what was done, for the provider's own logs and for whoever is watching the
     *     first live callback arrive. It says nothing about the booking or the customer — a webhook
     *     response is read by a machine that already knows what it sent
     */
    @PostMapping(path = "/{provider}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, String>> callback(
        @PathVariable("provider") String named,
        @RequestHeader Map<String, String> headers,
        @RequestBody(required = false) String body
    ) {
        PaymentOutcome outcome = verified(named, headers, body);
        PaymentConfirmations.Result result = confirmations.confirm(outcome);
        return switch (result) {
            case APPLIED -> ResponseEntity.ok(Map.of("result", "applied"));
            case ALREADY_APPLIED -> ResponseEntity.ok(Map.of("result", "already applied"));
            // 404 asks for a retry, and both cases want one: a callback that has overtaken its own
            // booking, and a handle this service does not recognise. A provider that keeps retrying
            // the second eventually gives up, which is the right outcome for a payment nothing here
            // can account for.
            case UNKNOWN_PAYMENT, BOOKING_NOT_YET -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("result", "not found"));
        };
    }

    /**
     * Establishes that this really is the provider, or refuses with 401 and no explanation.
     *
     * <p>The name in the path is checked against the configured provider first. That is not security —
     * the signature is — but it stops a callback signed by one provider being applied as another's the
     * day there is more than one (WP-13), and it means the verifier is never handed a body from a
     * provider it does not speak for.
     */
    private PaymentOutcome verified(String named, Map<String, String> headers, String body) {
        try {
            if (named == null || !named.toLowerCase(Locale.ROOT).equals(provider.name().toLowerCase(Locale.ROOT))) {
                throw new PaymentCallbackRefused("callback addressed to a provider this service is not configured for");
            }
            return provider.readCallback(new PaymentCallback(named, headers, body));
        } catch (PaymentCallbackRefused refused) {
            // The cause goes to the log, where an operator reads it; the caller is told only that it
            // was not authenticated. Nothing from the request is echoed — see PaymentCallbackRefused.
            LOG.warn("refused a payment callback: {}", refused.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
