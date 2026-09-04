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
 * not one.</strong> What that costs here is specific, and it is the opposite of what one might guess.
 *
 * <p><strong>A component-scanned {@code PaymentProvider} is always visible to this condition.</strong>
 * {@code ConfigurationClassParser.doProcessConfigurationClass} runs the component scan through
 * {@code ClassPathBeanDefinitionScanner.doScan}, which <em>registers</em> every scanned definition
 * before the loop that recursively parses them; the condition on a {@code @Bean} method is evaluated
 * later still, at {@code REGISTER_BEAN} phase in
 * {@code ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod}, which
 * {@code ConfigurationClassPostProcessor} does not reach until {@code parser.parse()} has finished. So
 * the scan is complete before the first {@code @Bean} condition fires, and the fallback below reliably
 * backs off for a {@code @Component}.
 *
 * <p><strong>The order-sensitive shape is an explicit {@code @Bean} in a sibling
 * {@code @Configuration}</strong> — the one an earlier version of this note recommended. Two scanned
 * configuration classes are parsed in the order the scanner found them, which is the order the
 * classpath resources came back in, which is the filesystem's business and nobody else's. This class
 * parsed first: the condition sees no {@code PaymentProvider}, the fallback is registered, the sibling
 * then registers the real one unconditionally, and every injection point has two candidates and a
 * {@code NoUniqueBeanDefinitionException}. Parsed second: it sees the real one and steps aside. Same
 * code, two outcomes, and it can differ between a laptop and CI — the worst place to first meet it.
 * {@code PaymentConfigurationUnitTest} demonstrates both halves.
 *
 * <p>So a real provider added before WP-13 should be a {@code @Component}, or anything carrying
 * {@code @Primary} — {@code @Primary} settles the ambiguity however the parse order fell, so it is
 * order-independent whichever shape declares it. The one thing to avoid is a bare {@code @Bean}
 * beside this class. The order-independent fix for that shape is to move <em>this</em> class to
 * {@code @AutoConfiguration} and list it in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}:
 * auto-configurations are processed from a deferred import selector after every user configuration
 * class, which is exactly the ordering the annotation assumes, and {@code @SpringBootApplication}'s
 * {@code AutoConfigurationExcludeFilter} keeps the class from also being component-scanned —
 * {@code HealthconnectBookingApp} carries the plain annotation, so that filter is in place.
 *
 * <p>WP-13 replaces the condition outright with a registry keyed by provider name, which is the shape
 * three providers need anyway: the fallback becomes the entry named {@code none} rather than the only
 * entry, and the webhook's "a callback addressed to a provider this service is not configured for"
 * refusal starts doing real work.
 */
@Configuration
public class PaymentConfiguration {

    /**
     * The fallback. It steps aside for a component-scanned provider every time; the shape it can
     * collide with is a bare {@code @Bean} in a sibling {@code @Configuration}. See the class javadoc
     * before adding either.
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
