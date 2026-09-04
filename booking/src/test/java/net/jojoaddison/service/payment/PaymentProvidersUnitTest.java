package net.jojoaddison.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which provider takes a booking's money, and who decides — {@code decisions.md} D45.
 *
 * <p>The registry replaced {@code @ConditionalOnMissingBean}, so the question "which provider" went
 * from having one possible answer to being a decision taken per booking, from a name a client sent.
 * D22's rule governs it: nothing a client sends decides what a booking costs or whose it is, and a
 * provider name is a client-supplied field that decides who ends up holding the money.
 */
class PaymentProvidersUnitTest {

    private final PaymentProvider fallback = new StubProvider("none");
    private final PaymentProvider paystack = new StubProvider("paystack");
    private final PaymentProvider hubtel = new StubProvider("hubtel");

    private PaymentProviders registry(PaymentProvider... configured) {
        // The fallback is in the list AND passed by identity, which is how Spring wires it: it is one
        // of the PaymentProvider beans, and PaymentProviders is given it a second time by name.
        List<PaymentProvider> all = new java.util.ArrayList<>();
        all.add(fallback);
        all.addAll(List.of(configured));
        return new PaymentProviders(all, fallback);
    }

    // -------------------------------------------------------------- resolving a callback's name --

    @Test
    @DisplayName("a callback's provider name selects that provider and no other")
    void namedFindsTheAdapterInThePath() {
        PaymentProviders registry = registry(paystack, hubtel);

        assertThat(registry.named("hubtel")).containsSame(hubtel);
        assertThat(registry.named("paystack")).containsSame(paystack);
        // Providers spell their own names however they like, and this one arrives in a URL path.
        assertThat(registry.named("HubTel")).containsSame(hubtel);
    }

    /**
     * The refusal D43 wrote and D45 made real.
     *
     * <p>Until there was a registry, "a callback addressed to a provider this service is not
     * configured for" compared the path against the only provider there was — a check with one
     * possible right answer. With three of them it decides which adapter is handed a body, and
     * handing a Hubtel body to Paystack's verifier is how a callback signed by one provider gets
     * applied as another's.
     */
    @Test
    @DisplayName("a name nothing is configured for resolves to nothing, rather than to whatever is first")
    void anUnknownNameResolvesToNothing() {
        PaymentProviders registry = registry(paystack);

        assertThat(registry.named("someone-else")).isEmpty();
        assertThat(registry.named("")).isEmpty();
        assertThat(registry.named(null)).isEmpty();
    }

    /**
     * The fallback is reachable by name, deliberately, and it refuses.
     *
     * <p>It could have been hidden from {@link PaymentProviders#named}, which would make a callback
     * addressed to it a 404-shaped refusal instead of a 401-shaped one. Letting it answer keeps the
     * webhook's single-answer property: every way of failing to establish a provider is the same 401,
     * and an estate with no provider refuses because its adapter says so rather than because the
     * lookup was arranged to hide it.
     */
    @Test
    @DisplayName("the fallback answers a callback addressed to it, and its adapter is what refuses")
    void theFallbackIsStillAddressable() {
        assertThat(registry(paystack).named("none")).containsSame(fallback);
    }

    // ------------------------------------------------------------------- what a customer may pick --

    @Test
    @DisplayName("the fallback is never something a customer may choose")
    void theFallbackIsNotAChoice() {
        assertThat(registry(paystack, hubtel).choices()).containsExactly("paystack", "hubtel");
        // And an estate with nothing configured offers nothing at all, rather than offering "none" —
        // which would let a client ask for a booking with no money behind it and be told yes.
        assertThat(registry().choices()).isEmpty();
    }

    @Test
    @DisplayName("an estate with no provider takes bookings exactly as it did before")
    void nothingConfiguredMeansTheFallbackAnswers() {
        assertThat(registry().chosen(null)).isSameAs(fallback);
        assertThat(registry().chosen("  ")).isSameAs(fallback);
    }

    /**
     * A named provider against an estate that has none is refused rather than quietly ignored.
     *
     * <p>The caller believes this estate collects money and it does not. Answering the booking with
     * an {@code OFF_PLATFORM} success would create a booking whose customer thinks they have paid.
     */
    @Test
    @DisplayName("naming a provider an estate does not have is refused, not ignored")
    void namingAProviderThatIsNotThereIsRefused() {
        assertThatThrownBy(() -> registry().chosen("paystack"))
            .isInstanceOf(PaymentChoiceRefused.class)
            .extracting(refused -> ((PaymentChoiceRefused) refused).reason())
            .isEqualTo(PaymentChoiceRefused.Reason.NOT_OFFERED);
    }

    @Test
    @DisplayName("one configured provider is the default, so a client that names nothing still books")
    void oneProviderIsTheDefault() {
        assertThat(registry(paystack).chosen(null)).isSameAs(paystack);
        assertThat(registry(paystack).chosen("paystack")).isSameAs(paystack);
        assertThat(registry(paystack).chosen(" PAYSTACK ")).isSameAs(paystack);
    }

    /**
     * The decision this class exists for.
     *
     * <p>With more than one provider, an unnamed request cannot be honoured: picking the first would
     * make who takes the customer's money depend on bean registration order, which is precisely the
     * property the registry was built to remove, and picking the fallback would create a booking with
     * no money behind it.
     */
    @Test
    @DisplayName("more than one provider and no choice is refused, naming what there is to choose from")
    void aChoiceIsRequiredWhenThereIsOne() {
        assertThatThrownBy(() -> registry(paystack, hubtel).chosen(null))
            .isInstanceOf(PaymentChoiceRefused.class)
            .hasMessageContaining("paystack")
            .hasMessageContaining("hubtel")
            .extracting(refused -> ((PaymentChoiceRefused) refused).reason())
            .isEqualTo(PaymentChoiceRefused.Reason.CHOICE_REQUIRED);
    }

    /**
     * Asking for the fallback by name is refused like any other name that is not on offer.
     *
     * <p>Otherwise "pay nothing" is a payment method: a client naming {@code none} against a
     * configured estate would get a booking created, {@code booking.requested} published and the
     * professional told, with no provider ever asked for the money.
     */
    @Test
    @DisplayName("a customer cannot choose to pay through no provider at all")
    void chooseNoneIsNotAChoice() {
        assertThatThrownBy(() -> registry(paystack).chosen("none"))
            .isInstanceOf(PaymentChoiceRefused.class)
            .extracting(refused -> ((PaymentChoiceRefused) refused).reason())
            .isEqualTo(PaymentChoiceRefused.Reason.NOT_OFFERED);
    }

    /**
     * The refusal says what is on offer and never what was asked for.
     *
     * <p>{@code offered()} is this service's own configuration, and a client that has to name a
     * provider has no other way to learn the names — there is no endpoint that publishes them. The
     * <em>request</em> is a stranger's string on its way to a response body, which is the route D44
     * closed for a provider's own prose.
     */
    @Test
    @DisplayName("the refusal names the offer, never the request")
    void theRefusalDoesNotEchoTheRequest() {
        assertThatThrownBy(() -> registry(paystack).chosen("<script>alert(1)</script>"))
            .isInstanceOf(PaymentChoiceRefused.class)
            .hasMessageContaining("paystack")
            .hasMessageNotContaining("script");
    }

    // ------------------------------------------------------------- adapters that will not behave --

    /**
     * An adapter that cannot name itself is not selectable, and does not break the registry either.
     *
     * <p>D44 found what an unwrapped {@code name()} costs — an exception from an adapter landing on
     * the 500 that the payment seam's {@code catch} exists to remove. The registry asks that question
     * more often than anything else does, so it asks it safely, and a provider nobody can name is
     * excluded rather than offered: no callback could ever be routed back to it, so a booking made
     * through it would wait for a confirmation with nowhere to arrive.
     */
    @Test
    @DisplayName("an adapter that cannot name itself is neither offered nor able to break a lookup")
    void anAdapterThatWillNotNameItself() {
        PaymentProvider broken = new StubProvider("paystack") {
            @Override
            public String name() {
                throw new IllegalStateException("no merchant id configured");
            }
        };
        PaymentProvider blank = new StubProvider("   ");
        PaymentProviders registry = new PaymentProviders(List.of(fallback, broken, blank), fallback);

        assertThat(registry.choices()).isEmpty();
        assertThat(registry.named("paystack")).isEmpty();
        // And the name written to payment_attempt.provider is a placeholder rather than nothing: that
        // column is not-null and the row is worth more than the name.
        assertThat(registry.nameOf(broken)).isEqualTo("unnamed");
        assertThat(registry.nameOf(blank)).isEqualTo("unnamed");
        assertThat(registry.nameOf(null)).isEqualTo("unnamed");
        // "unnamed" is what we call it, not a name it can be reached by.
        assertThat(registry.named("unnamed")).isEmpty();
    }

    /** A provider that exists, names itself and does nothing else. */
    private static class StubProvider implements PaymentProvider {

        private final String name;

        StubProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public PaymentOutcome authorize(PaymentIntent intent) {
            return PaymentOutcome.offPlatform();
        }

        @Override
        public PaymentOutcome capture(String providerReference, long amountMinor, String currency) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentOutcome refund(String providerReference, long amountMinor, String currency, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentOutcome voidAuthorization(String providerReference, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentOutcome status(String providerReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentOutcome readCallback(PaymentCallback callback) {
            throw new PaymentCallbackRefused("stub");
        }
    }
}
