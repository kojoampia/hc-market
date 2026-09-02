package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentProvider;
import net.jojoaddison.service.payment.PaymentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The default provider's asymmetry, asserted — {@code decisions.md} D15/D31.
 *
 * <p>{@code authorize} succeeds and everything else throws, and that difference is a decision rather
 * than an omission: the platform holds no customer money, so a refund path that returned a polite
 * failure would look like a provider outage and get retried, when what actually happened is that
 * somebody wrote code assuming funds this estate has never held.
 */
class PaymentConfigurationUnitTest {

    private final PaymentProvider provider = new PaymentConfiguration().unconfiguredPaymentProvider();

    @Test
    @DisplayName("authorizing reports OFF_PLATFORM, which permits a booking")
    void authorizeIsOffPlatform() {
        var outcome = provider.authorize(new PaymentIntent("b-1234", "kojo.customer", 15000L, "GHS", "Follow-up"));

        assertThat(outcome.state()).isEqualTo(PaymentState.OFF_PLATFORM);
        assertThat(outcome.state().permitsBooking()).isTrue();
        assertThat(outcome.providerReference()).isNull();
    }

    @Test
    @DisplayName("moving money that was never taken fails loudly")
    void everythingElseThrows() {
        assertThatThrownBy(() -> provider.capture("prov-1", 100L, "GHS")).isInstanceOf(IllegalStateException.class).hasMessageContaining("D15");
        assertThatThrownBy(() -> provider.refund("prov-1", 100L, "GHS", "changed mind")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.status("prov-1")).isInstanceOf(IllegalStateException.class);
    }

    /**
     * The compensating call joins the same asymmetry — {@code decisions.md} D41.
     *
     * <p>It matters more than the others that this one throws rather than answering politely, because
     * it is the call {@code BookingPayments.release} makes on the failure path: an estate where
     * nothing was ever committed must not be able to report that it successfully gave money back.
     * {@code release} therefore checks {@code PaymentState.holdsMoney()} before calling it at all,
     * and this test is what keeps that check honest.
     */
    @Test
    @DisplayName("releasing an authorization that was never taken fails loudly too")
    void voidingThrows() {
        assertThatThrownBy(() -> provider.voidAuthorization("prov-1", "booking b-1 could not be created"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("D15");
    }

    @Test
    @DisplayName("only committed money permits a booking")
    void permitsBookingIsExhaustive() {
        assertThat(PaymentState.OFF_PLATFORM.permitsBooking()).isTrue();
        assertThat(PaymentState.AUTHORIZED.permitsBooking()).isTrue();
        assertThat(PaymentState.CAPTURED.permitsBooking()).isTrue();
        assertThat(PaymentState.REFUNDED.permitsBooking()).isFalse();
        assertThat(PaymentState.VOIDED.permitsBooking()).isFalse();
        assertThat(PaymentState.DECLINED.permitsBooking()).isFalse();
        assertThat(PaymentState.FAILED.permitsBooking()).isFalse();
    }

    /**
     * Which states leave the platform holding something it would have to give back — D41.
     *
     * <p>{@code OFF_PLATFORM} is the whole reason this is a method rather than a null check on the
     * reference. It permits a booking and holds no money, and those are different questions: a
     * release driven by {@code permitsBooking} would call an unconfigured provider to give back money
     * nobody took, and the {@code IllegalStateException} above would replace whatever real failure
     * had just happened.
     */
    @Test
    @DisplayName("only committed money has to be given back")
    void holdsMoneyIsNotPermitsBooking() {
        assertThat(PaymentState.AUTHORIZED.holdsMoney()).isTrue();
        assertThat(PaymentState.CAPTURED.holdsMoney()).isTrue();
        assertThat(PaymentState.OFF_PLATFORM.holdsMoney()).isFalse();
        assertThat(PaymentState.VOIDED.holdsMoney()).isFalse();
        assertThat(PaymentState.REFUNDED.holdsMoney()).isFalse();
        assertThat(PaymentState.DECLINED.holdsMoney()).isFalse();
        assertThat(PaymentState.FAILED.holdsMoney()).isFalse();
    }

    @Test
    @DisplayName("the absent provider names itself, so a log line can say so")
    void namesItself() {
        assertThat(provider.name()).isEqualTo("none");
    }
}
