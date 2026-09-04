package net.jojoaddison.service.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentProvider;
import net.jojoaddison.service.payment.PaymentProviderProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The three adapters refuse everything, and each refusal is the right shape — {@code decisions.md}
 * D45.
 *
 * <p>These tests assert a <strong>decision</strong> rather than fix a defect, and the decision is
 * that an integration nobody has read the documentation for fails closed. WP-13 had no network
 * access, no provider account and no credentials, so a signature check, a field path or a status
 * mapping written for any of these three would have been invention that passes the mocks written to
 * match it — on the one path where a customer's money is already committed. What can be pinned is
 * that none of them ever answers anything that would let a booking through.
 */
class ProviderAwaitingIntegrationUnitTest {

    private static PaymentProviderProperties.Provider settings(String secret) {
        PaymentProviderProperties.Provider provider = new PaymentProviderProperties.Provider();
        provider.setEnabled(true);
        provider.setSecret(secret);
        return provider;
    }

    static List<org.junit.jupiter.params.provider.Arguments> adapters() {
        return List.of(
            org.junit.jupiter.params.provider.Arguments.of("paystack", (PaymentProvider) new PaystackPaymentProvider(settings("s"))),
            org.junit.jupiter.params.provider.Arguments.of("hubtel", (PaymentProvider) new HubtelPaymentProvider(settings("s"))),
            org.junit.jupiter.params.provider.Arguments.of("momo", (PaymentProvider) new MtnMomoPaymentProvider(settings("s")))
        );
    }

    /**
     * The names are the contract with three other places: the value a customer chooses, the segment
     * on {@code /webhooks/payments/{provider}}, and what lands in {@code payment_attempt.provider}.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    @DisplayName("each adapter answers to the name it is chosen and addressed by")
    void eachAdapterNamesItself(String name, PaymentProvider adapter) {
        assertThat(adapter.name()).isEqualTo(name);
    }

    /**
     * Every call that would need to know how the provider speaks throws, and
     * {@code BookingPayments.take} turns that into {@code FAILED} — a 502 and no booking.
     *
     * <p>Returning a polite {@code PaymentOutcome.failed(...)} would look identical to a customer and
     * would say nothing to the log. A throw carries a stack trace to the ERROR line the seam already
     * writes, which is what somebody needs when a provider they believe they configured refuses
     * everything.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    @DisplayName("nothing about money is answered, and the refusal names the call")
    void everyMoneyCallThrows(String name, PaymentProvider adapter) {
        PaymentIntent intent = new PaymentIntent("b-1234", "kojo.customer", 15000L, "GHS", "Follow-up");

        assertThatThrownBy(() -> adapter.authorize(intent))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining(name)
            .hasMessageContaining("authorize");
        assertThatThrownBy(() -> adapter.capture("prov-1", 15000L, "GHS")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.refund("prov-1", 15000L, "GHS", "why")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.voidAuthorization("prov-1", "why")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> adapter.status("prov-1")).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * A callback is <em>refused</em>, not exploded over.
     *
     * <p>The same asymmetry the unconfigured provider draws, and for the same reason: every other
     * unimplemented call is reached by this platform's own code holding a false belief, while this
     * one is reached by whoever posts to a public endpoint. {@link PaymentCallbackRefused} is a flat
     * 401; an {@code UnsupportedOperationException} would be a 500 and a stack trace per probe, which
     * tells a stranger their request got further into the application than it did.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    @DisplayName("a callback is refused with the type the endpoint turns into a flat 401")
    void everyCallbackIsRefused(String name, PaymentProvider adapter) {
        assertThatThrownBy(() -> adapter.readCallback(new PaymentCallback(name, Map.of("x-signature", "whatever"), "{}")))
            .isInstanceOf(PaymentCallbackRefused.class)
            .hasMessageContaining("not implemented");
    }

    /**
     * D45's fail-closed rule, which is D35's rule for the pepper applied to a provider's secret:
     * <strong>absent means callbacks are refused, never trusted.</strong>
     *
     * <p>The refusal is the same type and therefore the same 401, so nothing outside can tell a
     * misconfigured estate from an unimplemented one — an endpoint that distinguishes its refusals is
     * an oracle. The <em>message</em> differs, because it goes to a log an operator reads and
     * "configure the secret" and "write the integration" are two jobs for two people.
     */
    @Test
    @DisplayName("an adapter with no signing secret refuses the same way, and says so differently")
    void noSecretIsStillARefusal() {
        PaymentProvider unconfigured = new PaystackPaymentProvider(settings(null));
        PaymentProvider blank = new PaystackPaymentProvider(settings("   "));
        PaymentCallback callback = new PaymentCallback("paystack", Map.of(), "{}");

        assertThatThrownBy(() -> unconfigured.readCallback(callback))
            .isInstanceOf(PaymentCallbackRefused.class)
            .hasMessageContaining("no signing secret");
        assertThatThrownBy(() -> blank.readCallback(callback))
            .isInstanceOf(PaymentCallbackRefused.class)
            .hasMessageContaining("no signing secret");
    }
}
