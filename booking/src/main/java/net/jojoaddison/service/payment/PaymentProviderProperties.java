package net.jojoaddison.service.payment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * What this service has been given for each of D37's three providers — {@code decisions.md} D45.
 *
 * <h2>The third estate-wide secret, handled like the other two</h2>
 *
 * <p>{@code JWT_BASE64_SECRET} (the signing key) and {@code HC_PRIVACY_PEPPER} (D35) are required,
 * never committed, and injected by every compose file. A provider's signing secret is the same kind
 * of value and is handled the same way, with one difference that follows from what it is for: the
 * other two are required <em>always</em>, because every deployment authenticates somebody and every
 * deployment may be asked to erase somebody. A provider secret is required only where that provider
 * is turned on, and this estate turns none of them on.
 *
 * <p><strong>Absent means callbacks are refused, never trusted.</strong> That is the whole of the
 * fail-closed rule and it is the same shape D35 chose for the pepper: the service starts, everything
 * that does not depend on the missing value keeps working, and the one thing that does — here, a
 * provider vouching for a callback — answers no. A webhook that accepted an unverifiable callback
 * because nobody had configured a secret would create bookings for money that never arrived, on the
 * strength of a request anybody on the internet can send.
 *
 * <h2>Few fields, because the rest is still a documentation question</h2>
 *
 * <p>{@code secret} is "the value this provider signs its callbacks with, whatever their console
 * calls it". <strong>The credentials an outbound authorization call needs are deliberately not
 * modelled</strong>, because their shape differs per provider and — for two of the three — nobody
 * here has read the documentation or holds an account: Hubtel authenticates outbound calls with a
 * client id and secret pair, and MTN MoMo wants a subscription key plus an API user and key it issues
 * separately. Inventing plausible field sets would be this repository asserting a shape it has never
 * seen, which is the one thing {@code service.payment.provider}'s package documentation exists to
 * prevent. Whoever has the credentials adds the fields their provider actually has, in the same
 * commit as the adapter that reads them.
 *
 * <p>{@code baseUrl} and {@code timeoutMs} arrived under that rule with D49's Paystack adapter, which
 * is the first thing here that makes an outbound call. They are on {@code Provider} rather than
 * private to one adapter because every provider that speaks HTTP has a host and a patience, and
 * because a third party's hostname compiled in as an unoverridable constant is a redeploy the day
 * they move. <strong>Paystack needs no second credential</strong> — the same {@code sk_} key
 * authenticates {@code /transaction/initialize} and computes the callback HMAC — which is why its
 * arrival added no key field.
 *
 * <p>{@code enabled} is separate from {@code secret} rather than derived from it. An adapter that is
 * enabled with no secret <em>should</em> appear in the registry and refuse — that is a misconfigured
 * provider, which an operator can see and fix — while one that quietly vanished from the registry
 * would present as "the provider we configured is not offered", with nothing anywhere saying why.
 *
 * <p>{@code @Component}-annotated rather than listed on the generated application class, so a
 * regeneration leaves it alone; in {@code service} rather than {@code config} because
 * {@code TechnicalStructureTest} lets no layer reach {@code config} at all and the adapters have to
 * read it. Both are {@code PrivacyProperties}' reasons, unchanged.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.payments")
public class PaymentProviderProperties {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentProviderProperties.class);

    private final Provider paystack = new Provider();

    private final Provider hubtel = new Provider();

    private final Provider momo = new Provider();

    /**
     * Says at startup which providers this estate has been switched on for, and which of them cannot
     * verify a callback.
     *
     * <p><strong>It no longer says whether an adapter is implemented, and that is D49.</strong> It
     * used to warn that every enabled adapter "is ENABLED and is NOT IMPLEMENTED", which was true of
     * all three when D45 wrote it and became false for Paystack the moment one of them was built —
     * an estate taking real payments through a working integration while its own startup log said it
     * refuses everything. This class binds properties and cannot see a bean, so it cannot tell the
     * two apart; the adapter can, so the claim moved to {@code ProviderAwaitingIntegration}, where an
     * implemented adapter stops making it by construction rather than by somebody remembering.
     *
     * <p>What is left here is the half that really is a property question: an enabled provider with
     * no signing secret refuses every callback, whether or not anybody has written its integration.
     */
    @PostConstruct
    void announce() {
        each((name, provider) -> {
            if (!provider.isEnabled()) {
                return;
            }
            LOG.info("payments: the {} adapter is enabled", name);
            if (!provider.hasSecret()) {
                LOG.warn(
                    "payments: the {} adapter has no signing secret; every callback addressed to it is refused. " +
                        "Set it in the environment, never in this repository — it is public (decisions.md D35/D45)",
                    name
                );
            }
        });
    }

    /** Walks the three, so a fourth cannot be added without appearing in the startup account. */
    private void each(java.util.function.BiConsumer<String, Provider> visitor) {
        visitor.accept("paystack", paystack);
        visitor.accept("hubtel", hubtel);
        visitor.accept("momo", momo);
    }

    public Provider getPaystack() {
        return paystack;
    }

    public Provider getHubtel() {
        return hubtel;
    }

    public Provider getMomo() {
        return momo;
    }

    /** One provider's settings. Identical for all three today, and expected to stop being so. */
    public static class Provider {

        /** Whether the adapter is registered at all. False everywhere in this repository. */
        private boolean enabled;

        /**
         * What this provider signs its callbacks with. Never committed — this repository is public,
         * and a published secret is not a secret. Blank counts as absent, exactly as
         * {@code PrivacyProperties.controllerRegistration} treats blank.
         */
        private String secret;

        /**
         * Where this provider's API lives, or blank for the adapter's own default —
         * {@code decisions.md} D49.
         *
         * <p>Not a secret and not personal data, so unlike {@code secret} it may carry a committed
         * value; it does not, because the default belongs on the adapter that knows which host it
         * means. Overridable so that a sandbox, a proxy or a provider that has moved does not need a
         * release, and so that a test can point one at a stub without a network.
         */
        private String baseUrl;

        /**
         * How long the adapter waits on that API, in milliseconds. Zero or less means its default.
         *
         * <p>It has one because a payment call happens inside {@code POST /api/bookings}: an
         * unbounded wait on a third party is the customer's booking request held open, and a
         * connection pool spent on a provider that has stopped answering.
         */
        private int timeoutMs;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        /** Whether a callback from this provider could be verified at all. */
        public boolean hasSecret() {
            return secret != null && !secret.isBlank();
        }

        /** The configured host, or {@code fallback} when none is set. Blank counts as absent. */
        public String baseUrlOr(String fallback) {
            return baseUrl == null || baseUrl.isBlank() ? fallback : baseUrl.trim();
        }

        /** The configured timeout, or {@code fallback} when none is set. Zero and negatives count as absent. */
        public int timeoutMsOr(int fallback) {
            return timeoutMs > 0 ? timeoutMs : fallback;
        }
    }
}
