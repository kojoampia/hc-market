package net.jojoaddison.service.payment;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A request that arrived at the webhook claiming to be a provider — {@code decisions.md} D43.
 *
 * <p><strong>Nothing here is trusted.</strong> This is the unparsed request, headers and all, handed
 * to {@link PaymentProvider#readCallback} so that the one component which knows the provider's
 * signature scheme can decide whether it really came from them. Until that returns, the contents of
 * this record are a stranger's.
 *
 * <h2>The body is a String, and it has to stay one</h2>
 *
 * <p>Every provider signs the <em>bytes it sent</em>. Parsing this to a {@code Map} and re-serialising
 * it changes key order, whitespace and number formatting, so the signature over the re-serialised form
 * verifies against nothing — and the symptom is "every callback is rejected", which reads as a wrong
 * secret rather than as a wrong body. The resource takes the raw body and passes it through untouched;
 * the provider adapter parses it <em>after</em> it has verified it.
 *
 * <h2>"As received" rests on one property, and it is a default rather than a law</h2>
 *
 * <p>A String is not bytes, so this is a bytes → String → bytes round trip: Spring decodes the request
 * with {@code StringHttpMessageConverter} and {@code PaystackPaymentProvider.hmacSha512Hex} re-encodes
 * it as UTF-8 to sign it. That is lossless <strong>only because both ends agree on UTF-8</strong>, and
 * only one end of it is obvious. Spring Framework's converter defaults to <em>ISO-8859-1</em>; Boot
 * overrides it, because {@code spring.http.converters.string-encoding-charset} defaults to
 * {@code UTF-8} and nothing here sets it. Verified against the jars this service builds against
 * (Boot 4.0.7 {@code spring-boot-http-converter}, Framework 7 {@code StringHttpMessageConverter}).
 *
 * <p>Two things break it, and the symptom of either is the one this class already warns about —
 * <em>every</em> callback rejected, reading as a wrong secret rather than a wrong body. Setting that
 * property, and a provider declaring a non-UTF-8 charset on its {@code Content-Type}, which the
 * converter honours over the default. Nothing to fix today; a dependency to know about, because the
 * failure names neither the property nor the charset. Taking the body as {@code byte[]} would remove
 * the round trip altogether and is what to do if it ever fires.
 *
 * @param provider the name from the path, which is a claim rather than an identification: whoever
 *     posted it chose it. It selects which adapter is asked, and the adapter's own verification is
 *     what turns the claim into a fact
 * @param headers every header on the request, matched case-insensitively — HTTP says header names are
 *     case-insensitive and providers spell theirs however they like ({@code x-paystack-signature},
 *     {@code X-Hubtel-Signature}). A map with exact-match lookup would work in a test written against
 *     one spelling and fail against the wire
 * @param body the bytes as received, decoded as text and otherwise untouched — see the charset note
 *     above for what "as received" depends on
 */
public record PaymentCallback(String provider, Map<String, String> headers, String body) {
    public PaymentCallback {
        headers = caseInsensitive(headers);
    }

    /** One header, or null. Case-insensitive, for the reason on the {@code headers} parameter. */
    public String header(String name) {
        return headers.get(name);
    }

    /**
     * A copy that matches case-insensitively.
     *
     * <p>{@code Map.copyOf} is deliberately not used: it returns a hash map, which would silently
     * discard the comparator and take the lookups back to exact matching — an unmodifiable wrapper
     * round a {@link TreeMap} is the only form that keeps both properties.
     */
    private static Map<String, String> caseInsensitive(Map<String, String> given) {
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (given != null) {
            given.forEach((name, value) -> copy.put(name.toLowerCase(Locale.ROOT), value));
        }
        return java.util.Collections.unmodifiableMap(copy);
    }
}
