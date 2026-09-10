package net.jojoaddison.service.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.Optional;
import net.jojoaddison.service.FanoutTokenMinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The customer's email address, from the gateway's account store — {@code decisions.md} D72 §3, D74.
 *
 * <h2>The one implementation of {@link CustomerContacts}, and why it is this one</h2>
 *
 * <p>Paystack's {@code /transaction/initialize} needs an email address, {@code PaymentIntent} carries a
 * login and no contact details, and D50 rejected the two cheaper sources — a field on the booking
 * request (D22 verbatim) and the login when it happens to be email-shaped (works for a subset, fails at
 * the moment they pay). What was left was the gateway, which owns the accounts, and that needed
 * somebody with standing to decide who may ask it. D72 §3 decided.
 *
 * <p>{@code GET /internal/customers/{login}/email}, container to container, never through the gateway's
 * public routes — the same shape as {@code CatalogClient} and {@code ErasureFanoutClient}, for the same
 * reason: the gateway is the ingress for the outside, not a hop the estate uses to talk to itself.
 * (It is the gateway being asked here, which makes that sentence read oddly and leaves it true: the
 * request goes to the container's own port, not through its route table.)
 *
 * <h2>The credential is a second one, deliberately</h2>
 *
 * <p>{@code FanoutTokenMinter.forContactLookupOf} rather than {@code forErasureOf}. Reusing the erasure
 * token would have been one line and would have presented the gateway with a credential claiming to
 * authorise an erasure — see {@code ContactLookupToken}, which is byte-identical in booking and the
 * gateway and states the contract the gateway enforces. The token names this login, carries one
 * authority no login is ever granted, and lives thirty seconds.
 *
 * <h2>Four failures, and which of them is empty</h2>
 *
 * <p>D74 §4.4, decided rather than fallen into. All four end in the same place for the customer —
 * {@code authorize} refuses, {@code BookingPayments} turns that into {@code FAILED}, 502 and no
 * booking — so what is actually being decided is <strong>what the log says</strong>, and that decides
 * what somebody does about it:
 *
 * <table border="1">
 *   <caption>the four cases</caption>
 *   <tr><th>what happened</th><th>answer</th><th>log</th></tr>
 *   <tr><td>the account holds no email</td><td>empty</td><td>INFO — a fact about the account</td></tr>
 *   <tr><td>404 <em>naming this login</em>, so no such account</td><td>empty</td><td>WARN — a booking is
 *       being paid for under a login the account store does not know; nothing is broken here and
 *       something is odd there</td></tr>
 *   <tr><td>404 naming nothing, so something else answered</td>
 *       <td>{@link CustomerContacts.ContactsUnavailable}</td><td>ERROR naming the base URL — added by
 *       D74's review, because every Spring service in this estate 404s on a path it does not map, so a
 *       misdeployed base URL was being reported as a per-account fact for every login on the
 *       estate</td></tr>
 *   <tr><td>the gateway cannot be reached, times out, 5xx, or answers something unreadable</td>
 *       <td>{@link CustomerContacts.ContactsUnavailable}</td><td>ERROR</td></tr>
 *   <tr><td>the gateway refuses the estate's own token (401/403)</td>
 *       <td>{@link CustomerContacts.ContactsUnavailable}</td><td>ERROR, naming the status, because this
 *       one is a deployment fault: a signing key that differs between the two services, or a claim name
 *       that has drifted</td></tr>
 * </table>
 *
 * <p>The distinction that matters is the last two against the first two. Answering empty for an
 * unreachable gateway would put <em>"this estate holds no email"</em> in the log for an estate that
 * does — pointing whoever reads it at an unimplemented decision that is implemented, which is this
 * repository's most expensive recurring shape.
 *
 * <h2>The address is never logged</h2>
 *
 * <p>Not at DEBUG, not in a refusal, not in an exception message. The point of the whole arrangement is
 * that the address exists in the gateway's store and in Paystack's request and nowhere in between —
 * booking does not persist it either ({@code payment_attempt} holds a provider handle and no personal
 * data, D41). A log line is a place data is kept.
 */
@Component
public class GatewayCustomerContacts implements CustomerContacts {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayCustomerContacts.class);

    private static final String EMAIL_OF = "/internal/customers/{login}/email";

    private final RestClient http;
    private final FanoutTokenMinter tokens;

    /**
     * @param baseUrl the gateway, by container name. The default resolves nowhere useful on purpose —
     *     all three compose files set {@code HEALTHCONNECT_GATEWAY_BASE_URL}, and CI asserts they do,
     *     because a cross-service URL that falls back silently is D46's finding and this one fails
     *     closed on the path where a customer is paying
     * @param timeoutMs {@code CatalogClient}'s three seconds rather than the erasure fan-out's ten. A
     *     customer is waiting on a payment screen; this is not a redaction sweep
     */
    public GatewayCustomerContacts(
        RestClient.Builder builder,
        FanoutTokenMinter tokens,
        @Value("${healthconnect.gateway.base-url:http://healthconnectgateway}") String baseUrl,
        @Value("${healthconnect.gateway.timeout-ms:3000}") int timeoutMs
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.tokens = tokens;
    }

    @Override
    public Optional<String> emailOf(String customerLogin) {
        if (customerLogin == null || customerLogin.isBlank()) {
            // Nothing to mint a token for and nothing to ask about. Not ContactsUnavailable: the
            // estate is fine, the question was not a question.
            LOG.debug("no login to look up contact details for");
            return Optional.empty();
        }
        CustomerContact answer;
        try {
            answer = http
                .get()
                .uri(EMAIL_OF, customerLogin)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.forContactLookupOf(customerLogin))
                .retrieve()
                .body(CustomerContact.class);
        } catch (HttpClientErrorException.NotFound maybeNoSuchAccount) {
            // Separated from the arm below rather than folded into it: "there is no such account" is an
            // answer, and the estate answered it.
            //
            // BUT ONLY IF THE ACCOUNT STORE IS WHAT ANSWERED — D74's review. Every Spring service in
            // this estate 404s on a path it does not map, so a base URL misdeployed to catalog, payout
            // or messaging produces this arm for EVERY login, and reporting that as a per-account fact
            // is the same wrong diagnosis the 401/403 arm below was caught giving for a 500. So the
            // 404 has to name the login it is about, which is why the endpoint answers it with a body
            // (see InternalCustomerContactResource). A 404 that does not is ContactsUnavailable, not
            // empty: the answer came from something, and this service cannot say from what.
            if (!answersAbout(customerLogin, maybeNoSuchAccount)) {
                LOG.error(
                    "a 404 for {} did not come from this estate's account store — HEALTHCONNECT_GATEWAY_BASE_URL is " +
                        "pointed at something that does not serve /internal/customers/../email (decisions.md D74)",
                    customerLogin
                );
                throw new ContactsUnavailable("a 404 that did not come from the account store", maybeNoSuchAccount);
            }
            LOG.warn("the account store holds no account named {}, so this estate can name no contact details for it", customerLogin);
            return Optional.empty();
        } catch (RestClientResponseException refused) {
            // It answered, and said no. Two messages rather than one, because 401 and 403 name a cause
            // nothing else here can: the two services are on different signing keys, or
            // ContactLookupToken has drifted between its two copies. Everything else — a 500, a 429, a
            // 415 — is the account store's own trouble and must not be reported as a rejected
            // credential. One message covering both read "refused this service's own contact-lookup
            // token: HTTP 500" the first time this test ran, which is the wrong diagnosis in the log
            // and the exact defect the four-case split exists to avoid, one level down.
            int status = refused.getStatusCode().value();
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                LOG.error(
                    "the account store refused this service's own contact-lookup token for {}: HTTP {} — the two services are " +
                        "not on the same signing key, or ContactLookupToken has drifted between its copies (decisions.md D74)",
                    customerLogin,
                    status
                );
            } else {
                LOG.error("the account store answered HTTP {} to a contact lookup for {}", status, customerLogin);
            }
            throw new ContactsUnavailable("the account store did not answer a contact lookup", refused);
        } catch (RestClientException noUsableAnswer) {
            // Everything else under one honest heading, with the root cause's type in it because that
            // is what separates them and Spring does not: SocketTimeoutException is a gateway that
            // never answered, ConnectException is a container that is not there, and a Jackson
            // exception is one that answered something else entirely. ErasureFanoutClient's reasoning,
            // and its measurement — a read timeout arrives as a plain RestClientException from the
            // message converter, not as a ResourceAccessException, so classifying here would be wrong
            // exactly when it mattered.
            LOG.error("the account store gave no usable answer for {} ({})", customerLogin, rootCauseOf(noUsableAnswer));
            throw new ContactsUnavailable("the account store could not be asked", noUsableAnswer);
        }
        if (answer == null || !customerLogin.equals(answer.login())) {
            // An answer about somebody else, or no body at all. Refused rather than used: the endpoint
            // echoes the login precisely so that this can be checked, and an address belonging to a
            // different person is the one wrong answer that would go through unnoticed — Paystack would
            // accept it and somebody else would get the payment page.
            //
            // IT LOGS THE OTHER LOGIN, which is free text off another service's wire, and D60 declined
            // to do exactly that for catalog's zone string on this very argument. Kept, and the
            // distinction is deliberate rather than an inconsistency: D60's value was headed for a
            // COLUMN and its refusal named the row to correct, so the string bought nothing; here the
            // value IS the evidence — "it answered about somebody else" is unactionable without saying
            // who — and it goes to a log line and never to a response body or a row. It is the
            // gateway's own wire, not a payment provider's. If a third service ever answers this
            // endpoint, revisit: the argument rests on who is on the other end.
            LOG.error(
                "the account store answered a contact lookup for {} with an answer about {} — refusing it",
                customerLogin,
                answer == null ? "nothing" : answer.login()
            );
            throw new ContactsUnavailable("the account store answered about a different login", null);
        }
        String email = answer.email() == null ? "" : answer.email().trim();
        if (email.isEmpty()) {
            LOG.info("the account store holds no email address for {}", customerLogin);
            return Optional.empty();
        }
        return Optional.of(email);
    }

    /**
     * Whether a 404's body identifies it as the account store's own answer about {@code customerLogin}.
     *
     * <p>The same comparison the 200 path makes, applied to the refusal — which is the point: this
     * service establishes who answered from the answer naming the question, on both paths, rather than
     * from a status code and a base URL it was configured with.
     *
     * <p><strong>Fails closed on everything.</strong> No body, a body that is not JSON, a body with no
     * {@code login}, a body about somebody else, or a conversion this client cannot perform — all
     * false, all {@code ContactsUnavailable}. The one thing that must not happen here is an exception
     * escaping into the caller's {@code catch (RestClientException)} arm one level up, which would
     * report a misdeployment as an unreadable answer; so the conversion is wrapped.
     */
    private static boolean answersAbout(String customerLogin, RestClientResponseException answered) {
        try {
            CustomerContact body = answered.getResponseBodyAs(CustomerContact.class);
            return body != null && customerLogin.equals(body.login());
        } catch (RuntimeException notOurShape) {
            return false;
        }
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
     * What the gateway answers. Two fields, and this service reads one of them for its value.
     *
     * <p>{@code login} is not decoration: it is compared against what was asked, above. Unknown fields
     * are ignored so that the gateway may add one without a release here — but a field <em>removed</em>
     * there arrives as a null {@code login}, which the comparison refuses rather than passing over.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomerContact(String login, String email) {}
}
