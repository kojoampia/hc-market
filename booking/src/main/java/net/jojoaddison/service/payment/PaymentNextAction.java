package net.jojoaddison.service.payment;

import java.util.Locale;

/**
 * What the customer has to do next, in terms no provider dictates — {@code decisions.md} D43.
 *
 * <p>The three providers D37 chose all leave the customer holding the next move, and they leave it in
 * two different shapes: Paystack answers with an authorization URL to visit, while Hubtel and MTN MoMo
 * raise a prompt on the customer's phone and there is nowhere to send anybody. A seam that modelled
 * only the first would make the second look like a failure; one that modelled only the second would
 * silently drop the URL, which is D41's dropped handle in a new place.
 *
 * <h2>Why this is a kind and a URL rather than a message</h2>
 *
 * <p>The obvious alternative is a sentence from the provider — "a prompt has been sent to 024…". It
 * was not taken for two reasons, and the second is the stronger one. A client cannot act on prose: it
 * has to know whether to redirect or to show a wait screen, and inferring that from text is a parser
 * nobody wants. And a provider's message is <strong>the provider's</strong> text, which may name the
 * customer or their phone number; this record is handed to a client and its {@code url} is a candidate
 * for storage, and D41 records what it costs when a provider's own words leak into a place that was
 * designed to hold no personal data.
 *
 * <h2>The URL is validated here, and it is not paranoia</h2>
 *
 * <p>{@code url} comes from a third party and ends up in a browser's address bar. A
 * {@code javascript:} or {@code data:} URL would make this record a redirect gadget with a very short
 * path from "the provider was compromised, or spoofed" to "script running on the customer's session".
 * So the scheme is checked, once, at the only place one can be constructed. A provider adapter that
 * relays something else throws where it is built rather than where it is clicked.
 *
 * @param kind what the client should do. Never null
 * @param url where to send the customer, for {@link Kind#VISIT_URL} and only for it
 */
public record PaymentNextAction(Kind kind, String url) {
    /**
     * The shapes a next action comes in. Deliberately short: this is a list of things a client can
     * <em>do</em>, not a list of the ways a provider can describe itself.
     */
    public enum Kind {
        /** Nothing to do — the outcome is already final, or the platform is not in the money's path. */
        NONE,
        /** Send the customer to {@link PaymentNextAction#url()}. Paystack's redirect. */
        VISIT_URL,
        /**
         * A prompt is on the customer's phone; wait for the webhook. Hubtel and MoMo direct.
         *
         * <p>No URL, and none is invented. "Check your phone" is the whole of what the platform
         * truthfully knows, and it is enough for a client to render a wait screen.
         */
        AWAIT_DEVICE_PROMPT,
    }

    public PaymentNextAction {
        if (kind == null) {
            throw new IllegalArgumentException("a next action needs a kind; use PaymentNextAction.none()");
        }
        if (kind == Kind.VISIT_URL) {
            requireWebUrl(url);
        } else if (url != null) {
            // A URL on an action that is not a redirect is a contradiction rather than extra
            // information, and a client that reads the URL without checking the kind would follow it.
            throw new IllegalArgumentException("a " + kind + " next action carries no url");
        }
    }

    public static PaymentNextAction none() {
        return new PaymentNextAction(Kind.NONE, null);
    }

    /** Paystack's shape: the customer must visit this page to complete the payment. */
    public static PaymentNextAction visit(String url) {
        return new PaymentNextAction(Kind.VISIT_URL, url);
    }

    /** Hubtel's and MoMo's shape: the customer's phone is asking them, and we wait for the webhook. */
    public static PaymentNextAction awaitDevicePrompt() {
        return new PaymentNextAction(Kind.AWAIT_DEVICE_PROMPT, null);
    }

    /** Whether the customer has something to do. False for {@link Kind#NONE} and nothing else. */
    public boolean isRequired() {
        return kind != Kind.NONE;
    }

    private static void requireWebUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("a VISIT_URL next action needs a url to visit");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            // The message quotes nothing back. What was rejected is a string from a third party, and
            // the one place it must not be echoed to is a log line somebody later reads and clicks.
            throw new IllegalArgumentException("a payment url must be http or https — see PaymentNextAction");
        }
    }
}
