package net.jojoaddison.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two things a provider adapter is not allowed to hand back — {@code decisions.md} D43.
 *
 * <p>Both are refused at construction rather than checked by whoever consumes the outcome, because the
 * consumer is a webhook or a browser and neither is a place to discover that an adapter got it wrong.
 */
class PaymentOutcomeUnitTest {

    /**
     * A pending payment with no handle can never be confirmed.
     *
     * <p>This is D41's defect arriving from the other end. The webhook finds a payment by the
     * provider's reference and by nothing else, so a {@code PENDING} outcome without one describes a
     * payment that will be confirmed by a callback nothing can match to a booking — and the booking
     * sits in {@code PENDING_PAYMENT} for ever while the customer's money goes through perfectly well.
     * Nothing downstream could detect that, which is why the constructor refuses it.
     */
    @Test
    @DisplayName("a pending payment must carry the handle its confirmation will name")
    void pendingNeedsAReference() {
        assertThatThrownBy(() -> new PaymentOutcome(PaymentState.PENDING, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PENDING");
        assertThatThrownBy(() -> new PaymentOutcome(PaymentState.PENDING, "  ", null)).isInstanceOf(IllegalArgumentException.class);
        // Every other state may have none: OFF_PLATFORM has no provider at all, and a decline that
        // hands nothing back is the ordinary shape of one.
        assertThat(PaymentOutcome.offPlatform().providerReference()).isNull();
        assertThat(PaymentOutcome.declined("insufficient funds").providerReference()).isNull();
    }

    /**
     * The URL goes into a browser's address bar, and it came from a third party.
     *
     * <p>A {@code javascript:} URL relayed from a compromised or spoofed provider response is script
     * running in the customer's session, one click after a screen that says "complete your payment".
     * The scheme is checked at the only place a next action can be built.
     */
    @Test
    @DisplayName("a payment url that is not http or https is refused where it is built")
    void onlyWebUrlsAreRelayed() {
        assertThatThrownBy(() -> PaymentNextAction.visit("javascript:alert(document.cookie)"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http or https");
        assertThatThrownBy(() -> PaymentNextAction.visit("data:text/html,<script>1</script>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentNextAction.visit(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PaymentNextAction.visit("https://checkout.example.com/pay/abc").url()).isEqualTo("https://checkout.example.com/pay/abc");
    }

    /**
     * The two shapes the three chosen providers actually produce, and the difference a client acts on.
     *
     * <p>Paystack hands back somewhere to go; Hubtel and MoMo hand back nothing, because the prompt is
     * already on the customer's phone. A client switches on the kind rather than on the URL being
     * present, so "check your phone" is a case it renders rather than a link it failed to find.
     */
    @Test
    @DisplayName("a device prompt is a next action in its own right, not a missing url")
    void aPromptIsNotAMissingUrl() {
        PaymentOutcome redirect = PaymentOutcome.pendingAt("ps-1", "https://checkout.example.com/pay/abc");
        assertThat(redirect.nextAction().kind()).isEqualTo(PaymentNextAction.Kind.VISIT_URL);
        assertThat(redirect.nextAction().isRequired()).isTrue();

        PaymentOutcome prompt = PaymentOutcome.pendingOnDevice("mtn-1");
        assertThat(prompt.nextAction().kind()).isEqualTo(PaymentNextAction.Kind.AWAIT_DEVICE_PROMPT);
        assertThat(prompt.nextAction().url()).isNull();
        assertThat(prompt.nextAction().isRequired()).isTrue();

        // And an outcome that is already decided has nothing for the customer to do. Never null:
        // a caller reading nextAction().kind() must not have to null-check first.
        assertThat(PaymentOutcome.authorized("prov-1").nextAction()).isEqualTo(PaymentNextAction.none());
        assertThat(PaymentOutcome.offPlatform().nextAction().isRequired()).isFalse();
        assertThat(new PaymentOutcome(PaymentState.FAILED, null, "timeout", null).nextAction()).isEqualTo(PaymentNextAction.none());
    }

    /** A URL on an action that is not a redirect is a contradiction a client would follow anyway. */
    @Test
    @DisplayName("only a redirect carries a url")
    void onlyARedirectCarriesAUrl() {
        assertThatThrownBy(() -> new PaymentNextAction(PaymentNextAction.Kind.AWAIT_DEVICE_PROMPT, "https://example.com"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentNextAction(PaymentNextAction.Kind.NONE, "https://example.com")).isInstanceOf(
            IllegalArgumentException.class
        );
    }
}
