package net.jojoaddison.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jojoaddison.service.payment.CustomerContacts;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import net.jojoaddison.service.payment.PaymentProviderProperties;
import net.jojoaddison.service.payment.PaymentProviders;
import net.jojoaddison.service.payment.provider.HubtelPaymentProvider;
import net.jojoaddison.service.payment.provider.MtnMomoPaymentProvider;
import net.jojoaddison.service.payment.provider.PaystackPaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Every {@link PaymentProvider} bean in the estate — {@code decisions.md} D15/D31/D45.
 *
 * <p>A new file rather than an addition to the generated {@code SecurityConfiguration} or the
 * application class, for the usual reason: regeneration preserves new files and discards edits to
 * generated ones.
 *
 * <h2>There is no {@code @ConditionalOnMissingBean} here any more, and that is the point of D45</h2>
 *
 * <p>The fallback used to be supplied under that annotation, so a real provider was "one bean and no
 * edit". D37 chose <strong>three</strong> providers with the customer choosing between them, and
 * one-bean-wins cannot express that at all. D44 documented the annotation's order-sensitivity rather
 * than fixing it, on the explicit grounds that WP-13 would delete the condition — so it is deleted,
 * and the warning with it: every provider bean below is registered unconditionally or on a property,
 * and {@link PaymentProviders} decides which one a booking reaches. There is nothing left here whose
 * behaviour depends on the order two configuration classes were parsed in, so advice about it would
 * be advice about a mechanism that is gone.
 *
 * <p>The conditions that remain are {@code @ConditionalOnProperty}, which is a different animal: it
 * reads the {@code Environment} and never the bean registry, so no parse order can change its answer.
 * That was the whole of D44's hazard.
 *
 * <h2>Three adapters: one integrated, two still seams</h2>
 *
 * <p>{@link HubtelPaymentProvider} and {@link MtnMomoPaymentProvider} are seams with their
 * provider-specific halves missing — nobody here has an account or credentials for either, and would
 * have to invent the wire format. See {@code net.jojoaddison.service.payment.provider}'s package
 * documentation before implementing one. {@link PaystackPaymentProvider} is no longer among them:
 * D49 built {@code authorize} and {@code readCallback} from a working integration in a sibling
 * product, and left the other four calls refusing because the evidence does not cover them.
 *
 * <p>Each is registered only when its {@code enabled} property is true, and none is true anywhere in
 * this repository. <strong>Turning one on today still makes every priced booking that names it answer
 * 502</strong> — the two seams because they refuse to authorize anything, and Paystack because its
 * initialize call needs an email address this estate holds none of and may not guess (D49). Which of
 * the two it is is said at startup, once each: the seams say it themselves, from
 * {@code integratedCalls()}, and Paystack's second reason is said <em>here</em>, because an adapter
 * handed {@link CustomerContacts#unanswered()} cannot tell it from a real one. That is the honest
 * behaviour in both
 * cases, and it is preferable to the alternative shape — leaving the classes unregistered — because
 * the wiring between a name, a choice, a route and a callback is exactly what can be verified here,
 * and a bean that no configuration can produce is wiring nobody has run.
 */
@Configuration
public class PaymentConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentConfiguration.class);

    /**
     * The honest answer when nothing else is configured, and now one entry in a registry rather than
     * the only one.
     *
     * <p>The method name is load-bearing: {@link PaymentProviders} injects this bean by name through
     * {@link PaymentProviders#FALLBACK_BEAN}, so that it can exclude it from the customer's choices
     * without holding any opinion about what an absent provider calls itself. Renaming the method
     * without renaming the constant fails the context at startup, which is the loud direction.
     */
    @Bean
    public PaymentProvider unconfiguredPaymentProvider() {
        return new UnconfiguredPaymentProvider();
    }

    /**
     * Paystack, when {@code healthconnect.payments.paystack.enabled} is true — {@code decisions.md}
     * D49. The one adapter here that speaks to its provider.
     *
     * <p>{@code CustomerContacts} is taken through an {@link ObjectProvider} rather than as a
     * required dependency, and that is the whole of what this estate is missing: nothing here
     * implements it, so the default is {@link CustomerContacts#unanswered()} and every priced booking
     * naming Paystack answers 502 without a round trip. Required instead, the bean would fail the
     * context and take booking down for want of a decision nobody has taken; optional, the day
     * somebody registers <strong>one</strong> implementation this method needs no edit. See
     * {@code CustomerContacts} for the three candidate sources and why the account store is the only
     * defensible one.
     *
     * <p><strong>One, and exactly one.</strong> Two implementations are not "no edit": {@code
     * getIfAvailable()} answers a {@code NoUniqueBeanDefinitionException} rather than choosing between
     * them, so the context fails at startup. That is the right direction to fail in — nobody's money
     * goes through whichever bean was parsed first — and it is not what this sentence said before D49's
     * review. Whoever adds a second marks one {@code @Primary}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "healthconnect.payments.paystack", name = "enabled", havingValue = "true")
    public PaymentProvider paystackPaymentProvider(
        PaymentProviderProperties settings,
        RestClient.Builder http,
        ObjectMapper json,
        ObjectProvider<CustomerContacts> contacts
    ) {
        return new PaystackPaymentProvider(settings.getPaystack(), http, json, whoeverCanNameTheCustomer(contacts));
    }

    /**
     * The contacts implementation, and the startup line an operator needs when there is not one —
     * {@code decisions.md} D49, as reviewed.
     *
     * <p>The adapter announces at INFO that it "is enabled and implements [authorize, readCallback]",
     * which is true and reads as "it works". <strong>It does not work on this estate</strong>, and the
     * reason is not the adapter's: Paystack's initialize needs an email address nobody has decided how
     * to supply, so every priced booking naming Paystack is a 502 before any round trip. Left to
     * itself, the first report of that is a customer who could not pay — the same shape as the
     * {@code pk_} key check, which D49 put at boot for exactly this argument and then did not apply
     * here.
     *
     * <p>This is the only place that can tell. {@code PaymentProviderProperties} binds properties and
     * cannot see a bean; the adapter is handed a perfectly working {@link CustomerContacts} either way
     * and cannot compare it against {@link CustomerContacts#unanswered()} by identity, because that
     * factory returns a fresh lambda per call. Here, {@code getIfAvailable()} simply answered null.
     *
     * <p>WARN rather than a refusal to start, for D35's standing reason: an outage behind one missing
     * bean has somebody supply a plausible stand-in, which is the worse failure wearing a fix.
     */
    private static CustomerContacts whoeverCanNameTheCustomer(ObjectProvider<CustomerContacts> contacts) {
        CustomerContacts answered = contacts.getIfAvailable();
        if (answered != null) {
            return answered;
        }
        LOG.warn(
            "payments: the paystack adapter is enabled and this estate implements no CustomerContacts, so it cannot tell Paystack " +
                "who is paying — every priced booking naming paystack answers 502 without a round trip. It is a decision waiting " +
                "for somebody, not a fault: one @Component closes it (decisions.md D49)"
        );
        return CustomerContacts.unanswered();
    }

    /** Hubtel, when {@code healthconnect.payments.hubtel.enabled} is true. It refuses everything. */
    @Bean
    @ConditionalOnProperty(prefix = "healthconnect.payments.hubtel", name = "enabled", havingValue = "true")
    public PaymentProvider hubtelPaymentProvider(PaymentProviderProperties settings) {
        return new HubtelPaymentProvider(settings.getHubtel());
    }

    /** MTN MoMo, when {@code healthconnect.payments.momo.enabled} is true. It refuses everything. */
    @Bean
    @ConditionalOnProperty(prefix = "healthconnect.payments.momo", name = "enabled", havingValue = "true")
    public PaymentProvider momoPaymentProvider(PaymentProviderProperties settings) {
        return new MtnMomoPaymentProvider(settings.getMomo());
    }

    /**
     * The honest answer when no provider exists: the platform is not in the money's path.
     *
     * <h2>Why {@code authorize} succeeds and everything else throws</h2>
     *
     * <p>{@code OFF_PLATFORM} is a true statement — the customer pays the
     * professional directly and always has — so returning it lets bookings proceed exactly as they do
     * today while making the arrangement explicit rather than assumed.
     *
     * <p>{@code capture}, {@code refund}, {@code voidAuthorization} and {@code status} throw instead,
     * and the asymmetry is deliberate. There is no money to move, nothing to give back and no
     * provider to ask, so any caller reaching them holds a belief that is false. Returning a polite
     * {@code FAILED} would let that belief survive: a refund path that quietly reports failure looks
     * like a provider outage and gets retried, when what actually happened is that somebody wrote
     * code assuming the platform holds funds it has never held. Failing loudly is how that gets found
     * in a test rather than in a conversation with a customer who is owed a refund.
     *
     * <p>That includes the new one. {@code voidAuthorization} throwing is why
     * {@link net.jojoaddison.service.payment.BookingPayments#release} checks
     * {@code PaymentState.holdsMoney()} before calling it: an off-platform booking that fails to be
     * created has nothing committed, and asking this bean to release nothing would replace the real
     * failure with an {@code IllegalStateException} about payments.
     */
    static class UnconfiguredPaymentProvider implements PaymentProvider {

        private static final Logger LOG = LoggerFactory.getLogger(UnconfiguredPaymentProvider.class);

        private static final String NO_PROVIDER =
            "no payment provider is configured — this estate does not hold customer money, so there is none to move (decisions.md D15)";

        @Override
        public String name() {
            return "none";
        }

        @Override
        public PaymentOutcome authorize(PaymentIntent intent) {
            LOG.debug("booking {} is paid off-platform; nothing to authorize", intent.bookingReference());
            return PaymentOutcome.offPlatform();
        }

        @Override
        public PaymentOutcome capture(String providerReference, long amountMinor, String currency) {
            throw new IllegalStateException(NO_PROVIDER);
        }

        @Override
        public PaymentOutcome refund(String providerReference, long amountMinor, String currency, String reason) {
            throw new IllegalStateException(NO_PROVIDER);
        }

        @Override
        public PaymentOutcome voidAuthorization(String providerReference, String reason) {
            throw new IllegalStateException(NO_PROVIDER);
        }

        @Override
        public PaymentOutcome status(String providerReference) {
            throw new IllegalStateException(NO_PROVIDER);
        }

        /**
         * Refuses, and the refusal is the security answer rather than a complaint — {@code
         * decisions.md} D43.
         *
         * <p>This one does <strong>not</strong> throw {@link IllegalStateException} like its
         * neighbours, and the difference matters. The others are reached only by this platform's own
         * code holding a false belief, so failing loudly is how that gets found. This one is reached
         * by whoever posts to the webhook, and against an estate with no provider configured that is
         * by definition not a provider. {@link PaymentCallbackRefused} is what the endpoint turns into
         * a flat 401; an {@code IllegalStateException} would be a 500, which tells a stranger that
         * their request got further into the application than it should have and puts a stack trace
         * in the log for every probe of the path.
         */
        @Override
        public PaymentOutcome readCallback(PaymentCallback callback) {
            throw new PaymentCallbackRefused("no payment provider is configured, so no callback can be genuine (decisions.md D15/D43)");
        }
    }
}
