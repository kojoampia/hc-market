package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.jojoaddison.service.payment.PaymentCallback;
import net.jojoaddison.service.payment.PaymentCallbackRefused;
import net.jojoaddison.service.payment.PaymentIntent;
import net.jojoaddison.service.payment.PaymentOutcome;
import net.jojoaddison.service.payment.PaymentProvider;
import net.jojoaddison.service.payment.PaymentProviderProperties;
import net.jojoaddison.service.payment.PaymentProviders;
import net.jojoaddison.service.payment.PaymentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        assertThatThrownBy(() -> provider.capture("prov-1", 100L, "GHS"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("D15");
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

    /**
     * A callback is refused, and refusing is not the same as failing — {@code decisions.md} D43.
     *
     * <p>Every other unreachable call on this bean throws {@link IllegalStateException}, deliberately,
     * because reaching it means this platform's own code holds a false belief. This one is reached by
     * whoever posts to a public webhook, so it answers with the type the endpoint turns into a flat
     * 401. An {@code IllegalStateException} here would be a 500 and a stack trace per probe, which
     * tells a stranger their request got further into the application than it did.
     */
    @Test
    @DisplayName("a callback to an estate with no provider is refused, not exploded over")
    void readCallbackRefusesQuietly() {
        assertThatThrownBy(() -> provider.readCallback(new PaymentCallback("none", Map.of("x-signature", "whatever"), "{}")))
            .isInstanceOf(PaymentCallbackRefused.class)
            .hasMessageContaining("D43");
    }

    /**
     * Every state's answer to "may a booking exist against this?", driven from {@code values()}.
     *
     * <p>The point of this test is to <strong>break when a state is added</strong>, and the version it
     * replaces did not. It hand-listed seven constants and there were eight: D43 added {@link
     * PaymentState#PENDING}, whose answer here is the whole substance of that decision, and this test
     * went green throughout. A test that has to be remembered is the same mechanism as the {@code this
     * == A || this == B} chain {@link PaymentState} moved away from, one layer out — the omission is
     * just silent in a test file instead of in an enum.
     *
     * <p>So the expectations are a map keyed by the enum, and the first assertion is that the map
     * covers it exactly. A ninth state fails that line naming itself, before any answer is checked.
     *
     * <p><strong>It did.</strong> D44 added {@link PaymentState#NOTHING_TO_PAY} and this test went red
     * on the {@code containsExactlyInAnyOrder} line before a single answer was compared — which is the
     * mechanism working, one package after it was built. The answer was then written here deliberately
     * rather than acquired by omission.
     */
    @Test
    @DisplayName("only committed money permits a booking, and every state has to say so")
    void permitsBookingIsExhaustive() {
        Map<PaymentState, Boolean> expected = Map.of(
            PaymentState.OFF_PLATFORM,
            true,
            // Nothing to pay, so nothing stands in the way — and unlike its neighbours above, no
            // provider was consulted to arrive at this. See D44.
            PaymentState.NOTHING_TO_PAY,
            true,
            PaymentState.PENDING,
            true,
            PaymentState.AUTHORIZED,
            true,
            PaymentState.CAPTURED,
            true,
            PaymentState.REFUNDED,
            false,
            PaymentState.VOIDED,
            false,
            PaymentState.DECLINED,
            false,
            PaymentState.FAILED,
            false
        );

        assertThat(expected.keySet()).containsExactlyInAnyOrder(PaymentState.values());
        assertThat(Arrays.stream(PaymentState.values()).collect(Collectors.toMap(state -> state, PaymentState::permitsBooking))).isEqualTo(
            expected
        );
    }

    /**
     * D43's decision, pinned where it is taken rather than only where it is used.
     *
     * <p>Three answers, and each of them is the substance of a paragraph in the decision. A pending
     * payment <strong>permits</strong> a booking — that is "may a booking exist while its payment is
     * pending", answered yes. It <strong>holds no money</strong>, because nothing has been committed.
     * And it is nevertheless <strong>awaiting the customer</strong>, which is why an abandoned pending
     * payment still has to be cancelled at the provider: the customer's phone is on a prompt they can
     * approve a minute later.
     *
     * <p>This asserts a decision rather than fixing a defect, so it cannot be red against the code
     * before D43 — {@code PENDING} did not exist. It is red against the two obvious alternatives: a
     * {@code PENDING(false, false)} that refuses the booking, and a {@code holdsMoney} that folds
     * pending in with authorized.
     */
    @Test
    @DisplayName("a pending payment permits a booking, holds no money, and is still live")
    void pendingIsThreeAnswers() {
        assertThat(PaymentState.PENDING.permitsBooking()).isTrue();
        assertThat(PaymentState.PENDING.holdsMoney()).isFalse();
        assertThat(PaymentState.PENDING.awaitingCustomer()).isTrue();
        // And nothing else is: awaitingCustomer exists to name one state, not to become a synonym for
        // "not final". A DECLINED payment is over, and releasing it would call a provider for nothing.
        assertThat(PaymentState.AUTHORIZED.awaitingCustomer()).isFalse();
        assertThat(PaymentState.DECLINED.awaitingCustomer()).isFalse();
        assertThat(PaymentState.OFF_PLATFORM.awaitingCustomer()).isFalse();
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
        // The same guard as above, one line: this list is complete today, and a tenth state must not
        // be able to arrive without somebody deciding whether the platform would be holding its money.
        assertThat(PaymentState.values()).hasSize(9);
        assertThat(PaymentState.AUTHORIZED.holdsMoney()).isTrue();
        assertThat(PaymentState.CAPTURED.holdsMoney()).isTrue();
        assertThat(PaymentState.OFF_PLATFORM.holdsMoney()).isFalse();
        assertThat(PaymentState.VOIDED.holdsMoney()).isFalse();
        assertThat(PaymentState.REFUNDED.holdsMoney()).isFalse();
        assertThat(PaymentState.DECLINED.holdsMoney()).isFalse();
        assertThat(PaymentState.FAILED.holdsMoney()).isFalse();
        assertThat(PaymentState.PENDING.holdsMoney()).isFalse();
        // Nothing was taken because nothing was owed, so the release path must not reach a provider
        // for it — the same reason OFF_PLATFORM answers false. D44.
        assertThat(PaymentState.NOTHING_TO_PAY.holdsMoney()).isFalse();
        assertThat(PaymentState.NOTHING_TO_PAY.awaitingCustomer()).isFalse();
    }

    @Test
    @DisplayName("the absent provider names itself, so a log line can say so")
    void namesItself() {
        assertThat(provider.name()).isEqualTo("none");
    }

    // ---------------------------------------------------- the registry, and what replaced the condition --

    /**
     * Two providers in one context, which is the whole of what D45 changed here.
     *
     * <p>This replaces the pair of tests that pinned {@code @ConditionalOnMissingBean}'s behaviour.
     * One of them demonstrated that a sibling {@code @Bean PaymentProvider} collides or steps aside
     * depending purely on which configuration class was parsed first — identical code, two outcomes,
     * decided by the order {@code getResources} happened to return two files in. That is gone rather
     * than fixed: <strong>nothing injects a {@code PaymentProvider} by type any more</strong>, so two
     * of them are two entries rather than a {@code NoUniqueBeanDefinitionException}, and which one a
     * booking reaches is decided by {@link PaymentProviders#chosen(String)} from a name.
     *
     * <p>Asserted in <strong>both registration orders</strong>, because order-independence is the
     * property being claimed and the deleted test proved the opposite one.
     */
    @Test
    @DisplayName("a second provider is a second entry, not a collision, whichever order they arrive in")
    void twoProvidersCoexistInEitherOrder() {
        for (Class<?>[] order : List.of(
            new Class<?>[] { PaymentConfiguration.class, SiblingProviderConfiguration.class },
            new Class<?>[] { SiblingProviderConfiguration.class, PaymentConfiguration.class }
        )) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.register(order);
                context.register(PaymentProviders.class);
                context.refresh();

                assertThat(context.getBeansOfType(PaymentProvider.class)).hasSize(2);
                PaymentProviders providers = context.getBean(PaymentProviders.class);
                assertThat(providers.choices()).containsExactly("named");
                assertThat(providers.named("none")).isPresent();
                assertThat(providers.chosen("named")).isInstanceOf(NamedProvider.class);
            }
        }
    }

    /**
     * The fallback carries no condition now, and is still not a payment method.
     *
     * <p>Both halves matter. It is registered unconditionally, so an estate with a real provider has
     * two beans rather than one — which is what makes the previous test's claim possible. And
     * {@link PaymentProviders#choices()} excludes it by identity, so "pay through no provider" never
     * becomes something a client can ask for.
     */
    @Test
    @DisplayName("the fallback is always registered and never on offer")
    void theFallbackIsUnconditionalAndUnselectable() {
        try (var context = new AnnotationConfigApplicationContext(PaymentConfiguration.class, PaymentProviders.class)) {
            assertThat(context.getBeansOfType(PaymentProvider.class)).hasSize(1).containsKey(PaymentProviders.FALLBACK_BEAN);
            assertThat(context.getBean(PaymentProviders.class).choices()).isEmpty();
        }
    }

    /**
     * None of D37's three adapters exists unless somebody turns it on, and turning one on is the only
     * way to get a provider into this estate.
     *
     * <p>{@code @ConditionalOnProperty} rather than {@code @ConditionalOnMissingBean}, and the
     * difference is the whole of D44's hazard: this one reads the {@code Environment} and never the
     * bean registry, so no parse order can change its answer.
     */
    @Test
    @DisplayName("the three adapters are absent until their own property says otherwise")
    void adaptersAreOptInOnly() {
        new ApplicationContextRunner()
            .withUserConfiguration(WithProviderSettings.class, PaymentConfiguration.class, PaymentProviders.class)
            .run(context -> assertThat(context.getBean(PaymentProviders.class).choices()).isEmpty());

        new ApplicationContextRunner()
            .withUserConfiguration(WithProviderSettings.class, PaymentConfiguration.class, PaymentProviders.class)
            .withPropertyValues("healthconnect.payments.hubtel.enabled=true", "healthconnect.payments.momo.enabled=true")
            .run(context -> {
                PaymentProviders providers = context.getBean(PaymentProviders.class);
                assertThat(providers.choices()).containsExactlyInAnyOrder("hubtel", "momo");
                // And the one nobody enabled is not reachable by a callback either.
                assertThat(providers.named("paystack")).isEmpty();
                assertThat(providers.named("momo")).isPresent();
            });
    }

    /**
     * An enabled adapter with no secret is registered and refuses — {@code decisions.md} D35/D45.
     *
     * <p>The alternative shape, dropping it from the registry, was rejected: it presents as "the
     * provider we configured is not offered" with nothing anywhere saying why. This way an operator
     * sees the provider, sees the WARN at startup, and every callback addressed to it is refused with
     * the same flat 401 an unimplemented one gives — no secret means refused, never trusted.
     */
    @Test
    @DisplayName("a provider enabled without its secret is offered and refuses every callback")
    void anEnabledProviderWithNoSecretRefuses() {
        new ApplicationContextRunner()
            .withUserConfiguration(WithProviderSettings.class, PaymentConfiguration.class, PaymentProviders.class)
            .withPropertyValues("healthconnect.payments.paystack.enabled=true")
            .run(context -> {
                PaymentProvider paystack = context.getBean(PaymentProviders.class).named("paystack").orElseThrow();
                assertThatThrownBy(() -> paystack.readCallback(new PaymentCallback("paystack", Map.of(), "{}")))
                    .isInstanceOf(PaymentCallbackRefused.class)
                    .hasMessageContaining("no signing secret");
            });
    }

    /**
     * Binds {@code healthconnect.payments.*} the way the application does, without the application.
     *
     * <p>The two beans below are what a real adapter needs and the fallback never did — D49's
     * Paystack makes an HTTP call and parses a callback, so a slice that used to need nothing but
     * properties now needs a client and a mapper. They are the plain, unconfigured forms rather than
     * substitutes: nothing in this file makes a request, and a stub here would only be a second thing
     * to keep in step with {@code PaymentConfiguration}.
     */
    @Configuration
    @EnableConfigurationProperties(PaymentProviderProperties.class)
    static class WithProviderSettings {

        @Bean
        org.springframework.web.client.RestClient.Builder restClientBuilder() {
            return org.springframework.web.client.RestClient.builder();
        }

        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }

    /**
     * A real provider declared as a bare {@code @Bean} beside {@code PaymentConfiguration} — the one
     * shape D44 told WP-13 not to use, kept here because it is now harmless and that is the claim.
     */
    @Configuration
    static class SiblingProviderConfiguration {

        @Bean
        PaymentProvider realPaymentProvider() {
            return new NamedProvider();
        }
    }

    /** A provider that exists and does nothing. Only its type, its presence and its name matter. */
    static class NamedProvider implements PaymentProvider {

        @Override
        public String name() {
            return "named";
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
            throw new UnsupportedOperationException();
        }
    }
}
