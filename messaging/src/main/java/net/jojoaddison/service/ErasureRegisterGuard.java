package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.jojoaddison.domain.PepperWitness;
import net.jojoaddison.repository.ErasedSubjectRepository;
import net.jojoaddison.repository.PepperWitnessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <h2>Two things a missing pepper is not, and which this now also refuses</h2>
 *
 * <p>The rule above detects an <em>absent</em> pepper. Two other states are just as unrecoverable and
 * were detected by nothing, both recorded in D35 as risks rather than as checks:
 *
 * <p><strong>A register written by the pre-D35 derivation.</strong> Those aliases are {@code erased-}
 * plus 12 hex characters where a peppered one is 16, and nothing this service computes will ever
 * match one again. Counted by length — see
 * {@link ErasedSubjectRepository#countAliasesNotOfLength(int)} — because the register deliberately
 * holds nothing that says which rule produced a row.
 *
 * <p><strong>A pepper that has changed.</strong> D35: a rotation is indistinguishable, from the rows'
 * point of view, from a removal, and "a wrong pepper looks exactly like a right one until something
 * fails to match". So the first startup that has a pepper records what it produces for a fixed
 * sentinel input — see {@link PepperWitness} — and every later startup recomputes and compares. The
 * refusal then lands on the deploy that changed the variable, rather than on a data subject request
 * months later.
 *
 * <p>The witness lives in its own table, and that is not tidiness. A sentinel row in
 * {@code erased_subject} would make {@code count()} non-zero for ever, which would silently retract
 * the "empty register and no pepper: start" allowance that lets {@code isErased} answer {@code false}
 * instead of stalling the consumer.
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
 *
 * <h2>It decides once, per process — so every replica must share the pepper, or none may run</h2>
 *
 * <p>This is a startup check, and a startup check is a statement about the process that ran it. Two
 * messaging instances against one database, one of them started without the pepper, is a state this
 * cannot prevent: the unpeppered one passed its guard while the register was still empty, and it goes
 * on answering {@code isErased} = false for everything its peppered sibling erases afterwards,
 * writing real logins into fresh conversations with no restart to re-trigger anything.
 *
 * <p><strong>All replicas of messaging share one pepper, or none of them may run.</strong> The same
 * is true of booking and catalog, less sharply — they hold no register, so the worst case there is
 * two aliases for one person rather than an erasure that stops applying. It is stated in
 * {@code docker-compose.prod.yml} beside the variable as well as here, because the person who scales
 * a service reads the compose file and not this class.
 *
 * <p>Deployments here are single-instance, so this is structural rather than active, and it is
 * narrowed rather than closed: {@link #assertRegisterStillEmpty()} re-asks the register, at most once
 * every {@value #RECHECK_SECONDS} seconds and <em>only on the unpeppered path</em>, which is the only
 * configuration that can be wrong this way. A peppered service — every service in a healthy estate —
 * runs exactly the queries it ran before. The window between a sibling's erasure and this instance
 * noticing is bounded by that interval instead of by the next restart.
 */
@Component
public class ErasureRegisterGuard implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureRegisterGuard.class);

    /**
     * The single row's primary key. One witness per service, not one per pepper.
     *
     * <p>Public because it is also the name an operator deleting the row has to type, and because the
     * integration test that pins the witness out of {@code erased_subject} lives in another package.
     */
    public static final String WITNESS_ID = "subject-alias";

    /**
     * The input the witness alias is computed from.
     *
     * <p>It contains a NUL character, so no login can produce this alias however it is chosen, and
     * the witness can never be confused with a real subject. Its exact bytes are part of the stored
     * state: changing this string is indistinguishable, to the check below, from changing the pepper.
     */
    private static final String WITNESS_INPUT = "\0hc-market erasure pepper witness";

    /** How often the unpeppered path may re-ask the register. Never reached by a peppered service. */
    static final int RECHECK_SECONDS = 30;

    private final ErasedSubjectRepository register;
    private final PepperWitnessRepository witnesses;
    private final SubjectPseudonym pseudonyms;
    private final long recheckNanos;

    private volatile boolean running;

    /**
     * When the unpeppered path may next ask the register.
     *
     * <p>Seeded with the current reading rather than with {@link Long#MIN_VALUE}: {@code nanoTime} has
     * an arbitrary origin and is routinely negative, so {@code now - Long.MIN_VALUE} overflows and the
     * comparison below reads as "not due yet" — which suppressed the very first check, and therefore
     * every check, on some JVMs and not others.
     */
    private volatile long nextRecheckAt = System.nanoTime();

    /** {@code @Autowired} because there are two constructors and Spring may not pick for itself. */
    @Autowired
    public ErasureRegisterGuard(ErasedSubjectRepository register, PepperWitnessRepository witnesses, SubjectPseudonym pseudonyms) {
        this(register, witnesses, pseudonyms, Duration.ofSeconds(RECHECK_SECONDS));
    }

    /** The interval is injected only so a test can exercise the re-check without waiting for it. */
    ErasureRegisterGuard(
        ErasedSubjectRepository register,
        PepperWitnessRepository witnesses,
        SubjectPseudonym pseudonyms,
        Duration recheckInterval
    ) {
        this.register = register;
        this.witnesses = witnesses;
        this.pseudonyms = pseudonyms;
        this.recheckNanos = recheckInterval.toNanos();
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
     * a container. Throws when this process must not be allowed to touch a login; returns otherwise.
     */
    void check() {
        if (!pseudonyms.isConfigured()) {
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
            return;
        }

        /* The current alias for a fixed input, which answers two questions at once: how long an alias
           is now, and which pepper this process holds. Derived once — the pepper cannot change while
           the process runs, because SubjectPseudonym takes it in its constructor. */
        String witness = currentWitnessAlias();
        refuseLegacyAliases(witness.length());
        refuseAChangedPepper(witness);
    }

    /**
     * The alias this process's pepper produces for the sentinel.
     *
     * <p>Package-private rather than private so a test can build the row a correctly-peppered database
     * would already hold, without restating the derivation and testing this class against itself.
     */
    String currentWitnessAlias() {
        return pseudonyms.of(WITNESS_INPUT);
    }

    /**
     * Refuses a register still holding pre-D35 aliases.
     *
     * <p>Those are {@code erased-} plus 12 hex characters against today's 16, and nothing derived from
     * now on can match one. Every one of them is a person this service erased and can no longer
     * recognise, so the honest outcome is a service that will not start rather than one that answers
     * {@code isErased} = false and writes their login back into a fresh conversation.
     *
     * <p>There is no automatic migration and there cannot be one: re-keying needs the original logins,
     * and the whole design turns on nobody having kept them. See D35, "The migration consequence".
     */
    private void refuseLegacyAliases(int currentLength) {
        long stale = register.countAliasesNotOfLength(currentLength);
        if (stale > 0) {
            throw new IllegalStateException(
                ("erased_subject holds %d alias(es) that the current derivation cannot produce — they are " +
                    "not %d characters long, so they were written before the pepper existed (decisions.md D35). " +
                    "Nothing can re-key them: that would need the logins they came from, and none were kept. " +
                    "Read D35's migration section and clear the register deliberately, together with this " +
                    "service's volumes, before starting again").formatted(stale, currentLength)
            );
        }
    }

    /**
     * Records what this pepper produces the first time there is one, and compares it ever after.
     *
     * <p>The comparison is the only thing in the estate that can tell a rotated pepper from a correct
     * one. Everything else about a re-peppered service is indistinguishable from a healthy one: it
     * starts, it serves, the desk works, and each new alias is internally consistent — they simply no
     * longer match the rows already written, so an erasure quietly stops applying to the people it was
     * performed on.
     *
     * <p>Refuses even when the register is empty, deliberately. The pepper is one value across
     * booking, catalog and messaging (D35) and erasure is three separate desk calls (WP-07), so
     * "messaging happens to have erased nobody" says nothing about the other two, which hold aliases
     * and no register to notice with. The escape is to delete the row, which is a deliberate act with
     * a name.
     */
    private void refuseAChangedPepper(String witness) {
        Optional<PepperWitness> stored = witnesses.findById(WITNESS_ID);
        if (stored.isEmpty()) {
            try {
                witnesses.save(new PepperWitness(WITNESS_ID, witness, Instant.now()));
                LOG.info("privacy: recorded this estate's pepper witness — a change to it will now refuse startup (decisions.md D35)");
                return;
            } catch (DataIntegrityViolationException raced) {
                /* Two instances starting together. The loser reads what the winner wrote and compares
                   against it, which is the same check by a different route — and if they disagree,
                   that IS the defect this method exists for. */
                LOG.debug("privacy: another instance recorded the pepper witness first", raced);
                stored = witnesses.findById(WITNESS_ID);
            }
        }
        String recorded = stored.map(PepperWitness::getSubjectAlias).orElse(null);
        if (recorded != null && !recorded.equals(witness)) {
            throw new IllegalStateException(
                "HEALTHCONNECT_PRIVACY_PEPPER is not the value this database was built with. Every alias " +
                "already written — here, and in booking and catalog, which share the pepper — was derived " +
                "from the old one, and nothing re-keys an alias once it is in a row (decisions.md D35). " +
                "Restore the previous pepper. If this rotation is genuinely intended and you accept that " +
                "every erased subject becomes unrecognisable, delete the '" +
                WITNESS_ID +
                "' row from privacy_pepper_witness first"
            );
        }
    }

    /**
     * The unpeppered path's standing question — see the replica note in the class javadoc.
     *
     * <p>Called by {@code ErasureWorkflow.isErased} only when no pepper is configured, which is the
     * one configuration in which this instance's startup decision can silently stop being true: a
     * peppered sibling erasing somebody makes the register non-empty without anything happening in
     * this process. Throttled to one query per {@value #RECHECK_SECONDS} seconds, so the cost on a
     * misconfigured instance is negligible and on a correct one is nothing at all — a peppered service
     * never reaches this method.
     *
     * <p>It throws, and that is not the case the guard's "do not stall the consumer" rule protects.
     * That rule is about an empty register, where {@code false} is the true answer; here the register
     * has rows, so the alternatives are refusing the event or writing an erased person's real login,
     * and only one of those is recoverable. The event is not acknowledged, nothing is committed, and
     * the operator sees the same message the startup guard would have given them.
     */
    public void assertRegisterStillEmpty() {
        long now = System.nanoTime();
        if (now - nextRecheckAt < 0) {
            return;
        }
        nextRecheckAt = now + recheckNanos;
        long erased = register.count();
        if (erased > 0) {
            throw new IllegalStateException(
                ("%d subject(s) have been erased against this database while this instance is running " +
                    "without healthconnect.privacy.pepper — it cannot recognise them and would store their " +
                    "real logins. Every replica shares the estate's pepper or none may run (decisions.md D35)").formatted(
                        erased
                    )
            );
        }
    }
}
