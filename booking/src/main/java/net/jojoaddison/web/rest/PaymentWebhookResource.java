package net.jojoaddison.web.rest;

import java.util.Map;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentConfirmations;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import net.jojoaddison.service.payment.PaymentProviders;
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
 * <p><strong>Today every caller gets 401</strong>, and after D45 there are two ways to get it rather
 * than one. A name this service is not configured for resolves to no adapter at all and is refused
 * before anything reads the body — with three providers that is a real check rather than a comparison
 * against the only entry there was. A name that does resolve reaches an adapter, and every adapter in
 * this estate refuses: the fallback because there is no provider (D15), and the three named ones
 * because their signature schemes are not implemented (D45). That is the correct behaviour for an
 * estate that collects no money, not a placeholder.
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
 * <h2>This is reachable from the internet now, and that is D45</h2>
 *
 * <p>Until WP-13 it was not, and the thing keeping it private was D28's property rather than any rule
 * in a security file: the gateway's four route predicates matched {@code /services/<service>/api/**}
 * and this path is not under {@code /api}, so no request from outside could be routed here in any
 * environment. A provider that cannot reach the webhook cannot confirm a payment, so exposing it is
 * the last thing WP-13 owed, and it is <strong>two</strong> things in each environment rather than
 * one:
 *
 * <pre>
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_ID: healthconnectbooking-webhooks
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_URI: http://&lt;booking&gt;:8080
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_PREDICATES_0: Path=/services/healthconnectbooking/webhooks/**
 *   SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_4_FILTERS_0: StripPrefix=2
 * </pre>
 *
 * <p>— in all three compose files, <em>and</em> a permit in the gateway's own security, because the
 * generated gateway chain ends with {@code .pathMatchers("/services/**").authenticated()} and
 * authenticates before routing. The route alone is a webhook that silently never arrives, which reads
 * as a broken provider integration rather than as a missing line. That permit is
 * {@code PaymentWebhookRouteConfiguration} in the gateway, a new file beside
 * {@code MarketplacePublicRouteConfiguration} and for the same regeneration reason.
 *
 * <p><strong>So the signature check is now the only thing in front of this endpoint</strong>, which is
 * what {@link PaymentProvider#readCallback} was always for. Two narrowings remain and both are worth
 * keeping: the gateway permits only {@code POST}, and
 * {@code PaymentWebhookSecurityConfiguration} inside booking denies everything under
 * {@code /webhooks/} that is not one, so the prefix cannot quietly acquire a readable endpoint.
 *
 * <p>D28's property is untouched for everything else: catalog's {@code /internal/**} is still
 * unreachable, and this is the one path in the estate deliberately routed outside {@code /api/**}.
 * Widening the new predicate to {@code /services/healthconnectbooking/**} would publish booking's
 * whole surface, so it is written as {@code /webhooks/**} and CI asserts that shape in all three
 * files.
 *
 * <p><strong>Through the gateway was never the same as from outside.</strong> Booking's own port is
 * published on all interfaces by {@code docker-compose.dev.yml} ({@code ${HC_BOOKING_PORT:-8082}:8080}),
 * so on a development host anyone who can reach that port could always post here directly. Quality
 * publishes on {@code 127.0.0.1} only and production publishes booking's port not at all.
 */
@RestController
@RequestMapping("/webhooks/payments")
public class PaymentWebhookResource {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentWebhookResource.class);

    private final PaymentProviders providers;
    private final PaymentConfirmations confirmations;

    public PaymentWebhookResource(PaymentProviders providers, PaymentConfirmations confirmations) {
        this.providers = providers;
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
     * <p>The name in the path selects the adapter, through {@link PaymentProviders#named(String)}.
     * That is not security — the signature is — but it stops a callback signed by one provider being
     * applied as another's, and it means a verifier is never handed a body from a provider it does not
     * speak for. <strong>D45 is where that started doing real work</strong>: until there was a
     * registry the name was compared against the single configured provider, which was a comparison
     * with only one possible right answer. A name nothing here is configured for resolves to nothing
     * and is refused without any adapter being asked, so an unconfigured provider cannot be reached
     * even by a caller who signs perfectly.
     *
     * <p><strong>Every way of failing to establish the provider gives the same answer</strong>, which
     * is why the second catch is here. {@link PaymentCallbackRefused} is what an adapter throws when it
     * has read a request and decided against it; it is not what a <em>malformed</em> request produces.
     * A missing field is a {@code NullPointerException}, a body that is not this provider's shape is a
     * {@code JsonProcessingException}, and a relayed {@code javascript:} URL is an
     * {@code IllegalArgumentException} from {@code PaymentNextAction} — the last two are reachable from
     * code in this package. Letting those escape as 500 while a well-formed forgery got 401 published
     * the difference between the two, which tells a prober which of their attempts is structurally
     * closer to one this service would accept. The 401 belongs to the endpoint, not to the manners of
     * whichever adapter is configured.
     *
     * <p><strong>And an outcome that names no payment is not an establishment either</strong> —
     * {@code decisions.md} D49, as reviewed. {@link PaymentProvider#readCallback}'s javadoc has always
     * said that an implementation which cannot extract a reference must refuse rather than return, and
     * until now nothing enforced it: {@code PaymentOutcome.failed(reason)} nulls the handle, it is
     * public, and it is the obvious line to write for a declined payment. D49 found exactly that
     * mistake in the Paystack adapter and fixed the instance; this is the rule. An outcome with no
     * handle finds no {@code payment_attempt} row, so the booking it is about stays in
     * {@code PENDING_PAYMENT} for ever — a state nothing re-enters and nothing sweeps — while the log
     * blames the provider for naming a reference this service never issued. Refused here instead, with
     * the same 401 and an ERROR naming the adapter, so the next adapter's author meets it on their
     * first callback.
     */
    private PaymentOutcome verified(String named, Map<String, String> headers, String body) {
        PaymentOutcome outcome = established(named, headers, body);
        if (outcome.providerReference() == null || outcome.providerReference().isBlank()) {
            // ERROR rather than the WARN above it, and worded at the adapter rather than at the
            // caller: nothing a stranger can post produces this. It is this estate's own code
            // returning something that cannot be applied to anything, and the log line is the only
            // place that distinction can be drawn — from outside it is the same 401 as a forgery,
            // deliberately, because an endpoint that says "your signature was fine but our adapter is
            // broken" has told a prober that their signature was fine.
            LOG.error(
                "the {} adapter returned a verified callback outcome naming no payment — readCallback must refuse rather than return " +
                    "one, and PaymentOutcome.failed() drops the handle. The callback is refused (decisions.md D49)",
                // Looked up again rather than threaded out of the call above: this is the one path
                // that needs it, and the adapter's own name is worth more in the log than the
                // spelling whoever posted the request happened to use in the URL.
                providers.nameOf(providers.named(named).orElse(null))
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return outcome;
    }

    /**
     * The adapter's own answer, or 401 — everything up to and not including what the answer says.
     *
     * <p>Split from {@link #verified} so that the contract check above runs <em>outside</em> this
     * try/catch. Inside it, the {@link ResponseStatusException} it throws would be caught by the
     * {@code RuntimeException} arm and logged as an adapter that threw, which is the opposite of what
     * happened.
     */
    private PaymentOutcome established(String named, Map<String, String> headers, String body) {
        // Declared out here so the second catch can name the adapter that broke. It is null only on
        // the path that throws PaymentCallbackRefused, which the first catch takes.
        PaymentProvider provider = null;
        try {
            provider = providers
                .named(named)
                .orElseThrow(() -> new PaymentCallbackRefused("callback addressed to a provider this service is not configured for"));
            return provider.readCallback(new PaymentCallback(named, headers, body));
        } catch (PaymentCallbackRefused refused) {
            // The cause goes to the log, where an operator reads it; the caller is told only that it
            // was not authenticated. Nothing from the request is echoed — see PaymentCallbackRefused.
            LOG.warn("refused a payment callback: {}", refused.getMessage());
        } catch (RuntimeException broken) {
            // An adapter that threw rather than refused. The class and message go to the log because
            // this is also how a genuine provider integration reports being broken, and "401s, no
            // reason given" is not something anybody can debug. The message may quote the request,
            // which is why it goes to a log an operator reads and never into the response.
            LOG.warn(
                "a payment callback could not be read by the {} adapter: {}: {}",
                providers.nameOf(provider),
                broken.getClass().getName(),
                broken.getMessage()
            );
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
