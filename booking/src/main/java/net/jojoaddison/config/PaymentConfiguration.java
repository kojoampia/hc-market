package net.jojoaddison.config;

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
 */
@Configuration
public class PaymentConfiguration {

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
    }
}
