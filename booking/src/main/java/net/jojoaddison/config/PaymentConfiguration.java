package net.jojoaddison.config;

import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies a {@link PaymentProvider} when nothing else does — {@code decisions.md} D15/D31.
 *
 * <p>{@code @ConditionalOnMissingBean}, so adding a real provider is one bean and no edit here. The
 * seam is designed to be filled from outside rather than extended from inside.
 *
 * <p>A new file rather than an addition to the generated {@code SecurityConfiguration} or the
 * application class, for the usual reason: regeneration preserves new files and discards edits to
 * generated ones.
 *
 * <h2>Read this before adding the real provider — {@code decisions.md} D44</h2>
 *
 * <p><strong>{@code @ConditionalOnMissingBean} is only reliable in an auto-configuration, and this is
 * not one.</strong> In a user {@code @Configuration} the condition is evaluated when this class is
 * parsed, against the bean definitions registered <em>so far</em> — so whether it sees a
 * component-scanned {@code PaymentProvider} depends on the order the scanner happened to reach the
 * two classes in, which is a property of the filesystem. The failure it produces is two beans of one
 * type and a {@code NoUniqueBeanDefinitionException} at startup, and it can differ between a laptop
 * and CI on identical code, which is the worst place to first meet it.
 *
 * <p>So a real provider must be either {@code @Primary} or, better, declared as an explicit
 * {@code @Bean} that this class's condition can see — and <strong>not</strong> merely a
 * {@code @Component} sitting in the package hoping to win. WP-13 replaces the condition outright with
 * a registry keyed by provider name, which is the shape three providers need anyway: the fallback
 * becomes the entry named {@code none} rather than the only entry, and the webhook's "a callback
 * addressed to a provider this service is not configured for" refusal starts doing real work.
 */
@Configuration
public class PaymentConfiguration {

    /**
     * The fallback, and the ordering caveat is on the class — see its javadoc before relying on this
     * condition to step aside for a real provider.
     */
    @Bean
    @ConditionalOnMissingBean(PaymentProvider.class)
    public PaymentProvider unconfiguredPaymentProvider() {
        return new UnconfiguredPaymentProvider();
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
