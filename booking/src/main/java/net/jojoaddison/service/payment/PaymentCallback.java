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
 * @param provider the name from the path, which is a claim rather than an identification: whoever
 *     posted it chose it. It selects which adapter is asked, and the adapter's own verification is
 *     what turns the claim into a fact
 * @param headers every header on the request, matched case-insensitively — HTTP says header names are
 *     case-insensitive and providers spell theirs however they like ({@code x-paystack-signature},
 *     {@code X-Hubtel-Signature}). A map with exact-match lookup would work in a test written against
 *     one spelling and fail against the wire
 * @param body the bytes as received, decoded as text and otherwise untouched
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
