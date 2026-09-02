package net.jojoaddison.service;

import net.jojoaddison.repository.ErasedSubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Refuses to start an unpeppered messaging service that has already erased somebody —
 * {@code decisions.md} D35.
 *
 * <h2>Why this exists in messaging and nowhere else</h2>
 *
 * <p>{@link SubjectPseudonym} argues at length that a missing pepper should not stop a service
 * starting: it degrades one desk endpoint, and taking three services down over it is a worse outcome
 * than a 503 on the one operation that must not proceed. That argument holds while nobody has been
 * erased. It stops holding the moment somebody has.
 *
 * <p>Messaging is the only service holding a register of erased subjects ({@code ErasedSubject}, D32),
 * and that register is what {@code BookingEventConsumer} consults before it writes a login. Run it
 * without the pepper and {@code isErased} answers {@code false} for everybody — including people it
 * erased — so a booking event arriving for an erased customer writes their real login into a fresh
 * conversation and a fresh notification. That is byte for byte the failure D32 exists to close,
 * arriving through a configuration mistake instead of a race, and it is silent: the service is
 * healthy, the consumer is making progress, and the rows look ordinary.
 *
 * <p>So the rule is narrow. <strong>Empty register and no pepper: start.</strong> Nothing can have
 * been erased, the desk answers 503, and the estate is merely missing a variable.
 * <strong>Non-empty register and no pepper: refuse.</strong> There is no safe way to run, and the
 * operator is told which variable and why, at the one moment they are watching a deploy.
 *
 * <p>The same refusal catches the other half of D35's migration note — a pepper that has
 * <em>changed</em> is indistinguishable from one that is absent, as far as the existing rows are
 * concerned, and this only detects the absent case. A changed pepper still needs the register cleared
 * deliberately, which is why D35 records it rather than leaving it to be found.
 *
 * <h2>A {@code SmartLifecycle} at the lowest phase there is, and not an {@code ApplicationRunner}</h2>
 *
 * <p>This was an {@code ApplicationRunner} and that was wrong, in a way that gave the guard away
 * entirely in the one scenario it exists for. Spring starts {@code SmartLifecycle} beans inside
 * {@code finishRefresh()}, which is part of the context refresh;
 * {@code KafkaListenerEndpointRegistry} is one of them, and starting it starts
 * {@code BookingEventConsumer}'s listener container. {@code ApplicationRunner}s are invoked by
 * {@code SpringApplication.callRunners()}, which runs <em>after</em> the refresh has completed. So on
 * an unpeppered service with rows in the register the real order was: container starts, consumer
 * drains a slice of its backlog writing real logins for erased customers — each in its own committed
 * transaction — and only then does the guard throw and kill the process. With
 * {@code restart: unless-stopped} in both the quality and production compose files that becomes a
 * loop, and each restart grants the consumer another slice. The operator sees a service crash-looping
 * on a missing variable, which is exactly the loud failure that was intended, and has no reason to
 * suspect anything was written.
 *
 * <p>The check therefore has to complete <em>before any lifecycle bean starts</em>, not merely before
 * the application is declared ready. {@link #getPhase()} returns {@link Integer#MIN_VALUE}: phases
 * start in ascending order, so nothing else in the context can precede this one — including the
 * listener registry, whose phase is {@code ContainerProperties.DEFAULT_PHASE}
 * ({@code Integer.MAX_VALUE - 100}). Throwing from {@link #start()} propagates out of
 * {@code finishRefresh()} and aborts the refresh, so the context is destroyed rather than served,
 * which is the same outcome the runner had and now at the right moment.
 *
 * <p>Phasing it below the registry rather than merely below its default value is deliberate: a
 * container factory can be given a custom phase, and a guard that is only conditionally first is not
 * a guard. {@code ErasureRegisterGuardOrderingTest} pins the relationship against the real
 * {@code KafkaListenerEndpointRegistry} rather than against a remembered constant.
 *
 * <p>The other candidate hook was {@code afterPropertiesSet} on an early bean with
 * {@code @DependsOn("liquibase")}, which would also beat the registry. It was not taken because it
 * buys the same ordering at the price of a hard-coded bean name, and because the phase gives the
 * Liquibase guarantee for free: every singleton, Liquibase's included, is instantiated during
 * {@code finishBeanFactoryInitialization()}, before any of this runs. Liquibase is synchronous in this
 * service ({@code application.liquibase.async-start: false}) so {@code erased_subject} exists by then
 * — the same ordering {@code SeedDataLoader} depends on, and the same race if it were ever turned
 * back on.
 */
@Component
public class ErasureRegisterGuard implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureRegisterGuard.class);

    private final ErasedSubjectRepository register;
    private final SubjectPseudonym pseudonyms;

    private volatile boolean running;

    public ErasureRegisterGuard(ErasedSubjectRepository register, SubjectPseudonym pseudonyms) {
        this.register = register;
        this.pseudonyms = pseudonyms;
    }

    /**
     * Before everything. See the class javadoc: the listener container must not have consumed an event
     * by the time this decides whether the service may run at all.
     */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void start() {
        check();
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    /**
     * The decision itself, kept separate from the lifecycle plumbing so a unit test can make it without
     * a container. Throws when the register holds rows and no pepper is configured; returns otherwise.
     */
    void check() {
        if (pseudonyms.isConfigured()) {
            return;
        }
        long erased = register.count();
        if (erased > 0) {
            throw new IllegalStateException(
                ("this service has erased %d subject(s) and healthconnect.privacy.pepper is not set. " +
                    "Their aliases cannot be recomputed, so every booking event for an erased customer would " +
                    "store their real login again. Set HEALTHCONNECT_PRIVACY_PEPPER to the estate's value " +
                    "(decisions.md D35)").formatted(erased)
            );
        }
        LOG.warn("privacy: no pepper is set, and nobody has been erased yet — the erasure desk will answer 503 (decisions.md D35)");
    }
}
