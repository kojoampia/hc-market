package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.jojoaddison.repository.ErasedSubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/**
 * That the erasure guard decides <em>before</em> the booking-event consumer can consume anything —
 * {@code decisions.md} D35.
 *
 * <h2>The regression this catches, which the existing unit test could not</h2>
 *
 * <p>{@code ErasureRegisterGuardUnitTest} pins <em>what</em> the guard decides in each of the three
 * states, and it passed throughout the period the guard was useless. The guard was an
 * {@code ApplicationRunner}, and {@code SpringApplication} invokes those in {@code callRunners()},
 * after {@code refreshContext()} has returned. The Kafka listener container starts <em>inside</em>
 * that refresh, from {@code KafkaListenerEndpointRegistry}'s {@code SmartLifecycle} callback. So in
 * the one scenario the guard exists for — rows in {@code erased_subject}, no pepper — the container
 * had already started and {@code BookingEventConsumer} had already written erased customers' real
 * logins into fresh conversations and notifications, each committed in its own transaction, before
 * the guard threw. {@code restart: unless-stopped} turned that into a loop that worked through the
 * backlog a slice per restart, presenting as nothing worse than a crash-loop on a missing variable.
 *
 * <p>So this test asserts the <em>ordering</em>, not the decision: a bean at the listener registry's
 * own phase must never be started when the register holds rows and no pepper is set. Move the check
 * back to an {@code ApplicationRunner}, an {@code ApplicationListener}, an {@code @EventListener} on
 * {@code ApplicationReadyEvent}, or any phase above the registry's, and
 * {@link #theContainerPhaseIsNeverReachedWhenTheGuardRefuses()} goes red — under an
 * {@code ApplicationRunner} the refresh below completes without throwing at all.
 *
 * <p>A plain {@link GenericApplicationContext} rather than {@code @SpringBootTest}: the thing under
 * test is {@code DefaultLifecycleProcessor}'s phase ordering, which is Spring's and needs no
 * database, no broker and no application context of ours to exercise honestly. The stand-in takes its
 * phase from a real {@link KafkaListenerEndpointRegistry} instance rather than from a copy of
 * {@code ContainerProperties.DEFAULT_PHASE}, so an upstream change to that constant cannot leave this
 * test passing against a guard that no longer runs first.
 */
class ErasureRegisterGuardOrderingTest {

    /** No pepper is the whole point: this is the configuration the guard has to have an opinion about. */
    private static final SubjectPseudonym UNPEPPERED = new SubjectPseudonym("");

    private static final SubjectPseudonym PEPPERED = new SubjectPseudonym("hc-market-test-pepper-not-a-real-one");

    private final ErasedSubjectRepository register = mock(ErasedSubjectRepository.class);

    /**
     * Stands in for {@code KafkaListenerEndpointRegistry}, at its phase, recording only whether it was
     * ever started. Starting the real registry would need a container factory and a broker; what
     * matters here is that nothing at that phase gets its turn.
     */
    private static final class ListenerRegistryStandIn implements SmartLifecycle {

        private final int phase;
        private boolean started;

        private ListenerRegistryStandIn(int phase) {
            this.phase = phase;
        }

        @Override
        public int getPhase() {
            return phase;
        }

        @Override
        public void start() {
            this.started = true;
        }

        @Override
        public void stop() {
            this.started = false;
        }

        @Override
        public boolean isRunning() {
            return started;
        }
    }

    private GenericApplicationContext contextWith(SubjectPseudonym pseudonyms, ListenerRegistryStandIn containers) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("erasureRegisterGuard", ErasureRegisterGuard.class, () -> new ErasureRegisterGuard(register, pseudonyms));
        context.registerBean("kafkaListenerEndpointRegistry", ListenerRegistryStandIn.class, () -> containers);
        return context;
    }

    private static ListenerRegistryStandIn atTheListenerRegistrysPhase() {
        return new ListenerRegistryStandIn(new KafkaListenerEndpointRegistry().getPhase());
    }

    @Test
    @DisplayName("a refusal aborts the refresh before anything at the listener registry's phase starts")
    void theContainerPhaseIsNeverReachedWhenTheGuardRefuses() {
        when(register.count()).thenReturn(2L);
        ListenerRegistryStandIn containers = atTheListenerRegistrysPhase();

        try (GenericApplicationContext context = contextWith(UNPEPPERED, containers)) {
            Throwable refusal = catchThrowable(context::refresh);

            // Asserted before the refusal itself, deliberately. Under the ApplicationRunner this
            // replaced, the refresh completed and this was already true — the consumer had its slice
            // of the backlog before the guard was ever asked — so reporting it first makes a
            // regression say what actually went wrong rather than only that an exception is missing.
            assertThat(containers.started).as("nothing at the listener registry's phase may have started").isFalse();

            assertThat(refusal).rootCause().isInstanceOf(IllegalStateException.class).hasMessageContaining("HEALTHCONNECT_PRIVACY_PEPPER");
        }
    }

    @Test
    @DisplayName("with a pepper the same context starts, and the listener phase is reached")
    void aHealthyEstateStartsItsConsumer() {
        ListenerRegistryStandIn containers = atTheListenerRegistrysPhase();

        try (GenericApplicationContext context = contextWith(PEPPERED, containers)) {
            context.refresh();
            // Proves the stand-in is genuinely startable, so the assertion in the test above is about
            // the guard's ordering rather than about a bean that never starts under any conditions.
            assertThat(containers.started).isTrue();
        }
    }

    @Test
    @DisplayName("an empty register and no pepper still starts — the narrow rule D35 settled on")
    void anEmptyRegisterIsAllowedToStartUnpeppered() {
        when(register.count()).thenReturn(0L);
        ListenerRegistryStandIn containers = atTheListenerRegistrysPhase();

        try (GenericApplicationContext context = contextWith(UNPEPPERED, containers)) {
            context.refresh();
            assertThat(containers.started).isTrue();
        }
    }

    @Test
    @DisplayName("the guard's phase is strictly below the listener registry's, whatever that constant becomes")
    void theGuardIsPhasedBelowTheListenerRegistry() {
        int guard = new ErasureRegisterGuard(register, UNPEPPERED).getPhase();
        assertThat(guard).isLessThan(new KafkaListenerEndpointRegistry().getPhase());
        // And below everything else too: a container factory may be given a custom phase, so being
        // below the registry's default is not on its own enough.
        assertThat(guard).isEqualTo(Integer.MIN_VALUE);
    }
}
