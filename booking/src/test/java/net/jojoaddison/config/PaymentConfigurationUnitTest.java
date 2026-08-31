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
        assertThatThrownBy(() -> provider.capture("prov-1")).isInstanceOf(IllegalStateException.class).hasMessageContaining("D15");
        assertThatThrownBy(() -> provider.refund("prov-1", 100L, "changed mind")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider.status("prov-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("only committed money permits a booking")
    void permitsBookingIsExhaustive() {
        assertThat(PaymentState.OFF_PLATFORM.permitsBooking()).isTrue();
        assertThat(PaymentState.AUTHORIZED.permitsBooking()).isTrue();
        assertThat(PaymentState.CAPTURED.permitsBooking()).isTrue();
        assertThat(PaymentState.REFUNDED.permitsBooking()).isFalse();
        assertThat(PaymentState.DECLINED.permitsBooking()).isFalse();
        assertThat(PaymentState.FAILED.permitsBooking()).isFalse();
    }

    @Test
    @DisplayName("the absent provider names itself, so a log line can say so")
    void namesItself() {
        assertThat(provider.name()).isEqualTo("none");
    }
}
