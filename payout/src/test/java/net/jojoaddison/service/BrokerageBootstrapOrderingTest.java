package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/**
 * The founding terms are written <em>before</em> {@code BookingEventConsumer}'s listener container can
 * start — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>The regression this catches, which the unit test beside it cannot</h2>
 *
 * <p>{@code BrokerageBootstrapUnitTest} pins <em>what</em> the bootstrap decides in each state, and it
 * would pass throughout the window in which the bootstrap was useless. The obvious hook for "do this at
 * startup" is an {@code ApplicationRunner}, and {@code SpringApplication} invokes those in
 * {@code callRunners()} — <em>after</em> {@code refreshContext()} has returned. The Kafka listener
 * container starts <em>inside</em> that refresh, from {@code KafkaListenerEndpointRegistry}'s
 * {@code SmartLifecycle} callback. So the real order would be: container starts, consumer takes a
 * {@code booking.completed} against a table that is still empty, {@code configInForce} throws, the
 * container retries, and only then is the row written. The retry would eventually succeed, which is what
 * makes this worth a test rather than a comment: the window is self-healing and therefore invisible, and
 * the same reasoning applied to messaging's erasure guard produced a window that was not.
 *
 * <p>So this asserts the <em>ordering</em>, not the decision. Move the bootstrap to an
 * {@code ApplicationRunner}, an {@code @EventListener} on {@code ApplicationReadyEvent}, or any phase
 * above the registry's, and {@link #theRowIsWrittenBeforeTheListenerPhaseIsReached()} goes red — under a
 * runner nothing is written during the refresh below at all.
 *
 * <p>A plain {@link GenericApplicationContext} rather than {@code @SpringBootTest}: what is under test is
 * {@code DefaultLifecycleProcessor}'s phase ordering, which is Spring's and needs no database and no
 * broker to exercise honestly. The stand-in takes its phase from a real
 * {@link KafkaListenerEndpointRegistry} instance rather than from a copy of
 * {@code ContainerProperties.DEFAULT_PHASE}, so an upstream change to that constant cannot leave this
 * passing against a bootstrap that no longer runs first.
 */
class BrokerageBootstrapOrderingTest {

    private final BrokerageConfigRepository configs = mock(BrokerageConfigRepository.class);

    /** Flipped by the repository stub the moment the founding row is written. */
    private final AtomicBoolean founded = new AtomicBoolean();

    /**
     * Stands in for {@code KafkaListenerEndpointRegistry}, at its phase, recording only what had already
     * happened by the time it was given its turn. Starting the real registry would need a container
     * factory and a broker.
     */
    private static final class ListenerRegistryStandIn implements SmartLifecycle {

        private final int phase;
        private final AtomicBoolean founded;
        private boolean started;
        private Boolean foundedWhenStarted;

        private ListenerRegistryStandIn(int phase, AtomicBoolean founded) {
            this.phase = phase;
            this.founded = founded;
        }

        @Override
        public int getPhase() {
            return phase;
        }

        @Override
        public void start() {
            this.foundedWhenStarted = founded.get();
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

    private GenericApplicationContext contextWith(ListenerRegistryStandIn containers) {
        when(configs.save(any(BrokerageConfig.class))).thenAnswer(saved -> {
            founded.set(true);
            return saved.getArgument(0);
        });
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("brokerageBootstrap", BrokerageBootstrap.class, () -> new BrokerageBootstrap(configs, new FoundingTerms()));
        context.registerBean("kafkaListenerEndpointRegistry", ListenerRegistryStandIn.class, () -> containers);
        return context;
    }

    private ListenerRegistryStandIn atTheListenerRegistrysPhase() {
        return new ListenerRegistryStandIn(new KafkaListenerEndpointRegistry().getPhase(), founded);
    }

    @Test
    @DisplayName("the founding row is written before anything at the listener registry's phase starts")
    void theRowIsWrittenBeforeTheListenerPhaseIsReached() {
        when(configs.count()).thenReturn(0L);
        ListenerRegistryStandIn containers = atTheListenerRegistrysPhase();

        try (GenericApplicationContext context = contextWith(containers)) {
            context.refresh();

            // Both halves matter and they fail differently. The stand-in must genuinely have started —
            // otherwise this asserts nothing about ordering — and the row must have been there when it
            // did. Under an ApplicationRunner the first is true and the second is false.
            assertThat(containers.started).as("the listener registry's phase must be reached").isTrue();
            assertThat(containers.foundedWhenStarted)
                .as("the founding terms must already be written when the consumer's container starts")
                .isTrue();
        }
    }

    @Test
    @DisplayName("an estate that already has terms starts its consumer too, having written nothing")
    void aPopulatedEstateStartsNormally() {
        when(configs.count()).thenReturn(1L);
        ListenerRegistryStandIn containers = atTheListenerRegistrysPhase();

        try (GenericApplicationContext context = contextWith(containers)) {
            context.refresh();

            assertThat(containers.started).isTrue();
            assertThat(founded).isFalse();
        }
    }

    @Test
    @DisplayName("the phase is strictly below the listener registry's, whatever that constant becomes")
    void theBootstrapIsPhasedBelowTheListenerRegistry() {
        when(configs.count()).thenReturn(1L);
        int bootstrap = new BrokerageBootstrap(configs, new FoundingTerms()).getPhase();

        assertThat(bootstrap).isLessThan(new KafkaListenerEndpointRegistry().getPhase());
        // And below everything else too: a container factory may be given a custom phase, so being
        // below the registry's default is not on its own enough.
        assertThat(bootstrap).isEqualTo(Integer.MIN_VALUE);
    }
}
