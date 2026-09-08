package net.jojoaddison.service;

import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Writes this estate's founding brokerage terms into an empty {@code brokerage_config}, once, on every
 * environment including production — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>Nothing could create a {@link BrokerageConfig} on an estate that does not seed, and production does
 * not seed. The only writers were {@code PayoutSeeder} — double-locked behind the {@code test & dev}
 * profile pair and {@code healthconnect.seed.enabled}, which {@code application-prod.yml} sets false —
 * and the generated {@code BrokerageConfigResource}, which D54 deleted because it let any authenticated
 * user set this platform's commission rate. The generated {@code loadData} in the entity's changelog is
 * {@code context="faker"}, which no environment here enables. So the table was empty, and:
 *
 * <ul>
 *   <li>{@code BookingEventConsumer.configInForce} threw {@code IllegalStateException} and the consumer
 *       <strong>retried for ever</strong> — no ledger row, {@code /api/pro/earnings} stuck at zero, and
 *       booking entirely happy, because the failure was inside payout's consumer and nowhere else;
 *   <li>{@code BrokerageResource.inForce} threw {@code SERVICE_UNAVAILABLE}, so <strong>every receipt
 *       was a 503</strong>.
 * </ul>
 *
 * <p>Both land on the one path where the customer's money has already moved, and a production deploy of
 * the estate without this would have come up healthy and passed its catalogue smoke test.
 *
 * <h2>This is NOT the seed, and the separation is structural rather than documentary</h2>
 *
 * <p>CLAUDE.md records that seeding is double-locked on the {@code test & dev} profile pair <em>and</em>
 * {@code healthconnect.seed.enabled}, and that {@code prod} was verified to refuse to seed with the
 * property forced true. This writes under {@code prod}. If it travelled through the seed's switch that
 * sentence would stop being true, and the next person reading it would be wrong — so it shares nothing
 * with the seed: a different class in a different package ({@code service}, not {@code service.seed}), a
 * different property namespace ({@code healthconnect.brokerage.founding}, not
 * {@code healthconnect.seed}), a different lifecycle hook, no profile condition, and no seed file. The
 * only thing the two ever shared was the table, and since D57 they do not share that either:
 * {@code PayoutSeeder} no longer writes a {@code BrokerageConfig} and no longer deletes one.
 *
 * <p>The distinction is not merely mechanical. The seed is <em>demo data</em>: eighteen invented
 * professionals and the sessions they never worked, which must never reach a real estate. The founding
 * terms are this brokerage's <em>commercial configuration</em>, and a real estate cannot function
 * without them. Nothing about "prod must not seed" was ever an argument for "prod must not know its own
 * commission rate"; the two were only ever conflated because one class happened to write both.
 *
 * <h2>There is no off switch, deliberately</h2>
 *
 * <p>The obvious shape is a {@code healthconnect.brokerage.bootstrap.enabled} flag "safe to leave on in
 * production". It is not here, because its only correct setting is on: the guard that matters is
 * {@link #alreadyConfigured()}, which is a fact about the database rather than a property somebody can
 * set wrongly, and it already provides every behaviour the flag would. An estate migrating its own terms
 * in inserts them and this stands down; an estate with none gets the founding row. A switch would add
 * exactly one capability — running a payout service that cannot price anything — which is the defect
 * this class exists to remove. It is also what keeps this off the seed's switch by construction: there
 * is no switch to confuse with {@code healthconnect.seed.enabled}.
 *
 * <h2>A {@code SmartLifecycle} at the lowest phase there is, and not an {@code ApplicationRunner}</h2>
 *
 * <p>Borrowed wholesale from messaging's {@code ErasureRegisterGuard}, which learned it the hard way.
 * {@code SpringApplication.callRunners()} runs <em>after</em> the context refresh has completed, while
 * {@code KafkaListenerEndpointRegistry} is a {@code SmartLifecycle} started <em>inside</em>
 * {@code finishRefresh()}. A runner would therefore let {@code BookingEventConsumer} take a
 * {@code booking.completed} against a still-empty table — the exact forever-retry this closes, arriving
 * in the seconds before the fix ran. {@link #getPhase()} returns {@link Integer#MIN_VALUE}: phases start
 * in ascending order, so nothing else in the context can precede this, including the listener registry
 * at {@code ContainerProperties.DEFAULT_PHASE} ({@code Integer.MAX_VALUE - 100}).
 * {@code BrokerageBootstrapOrderingTest} pins that against a real registry instance rather than against
 * a remembered constant.
 *
 * <p>Liquibase comes for free at that phase: every singleton is instantiated during
 * {@code finishBeanFactoryInitialization()}, before any lifecycle bean starts, and Liquibase is
 * synchronous here ({@code application.liquibase.async-start: false}). That is the same ordering
 * {@code SeedDataLoader} depends on and the same race if it were ever turned back on.
 *
 * <h2>Replicas</h2>
 *
 * <p>Two payout instances starting simultaneously against one empty database can both see an empty table
 * and both write. There is no unique constraint on {@code brokerage_config} to make one of them lose —
 * D56 chose a stated tie-break over a schema guarantee, because two rows sharing an
 * {@code effectiveFrom} is a state the schema allows and the selector has to have an answer for. The
 * outcome is harmless rather than merely unlikely: the two rows are identical in every column that
 * prices anything, and {@code BrokerageTerms} takes the newest {@code id} deterministically, so a
 * receipt and a ledger row cannot disagree. Deployments here are single-instance in any case.
 */
@Component
public class BrokerageBootstrap implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerageBootstrap.class);

    private final BrokerageConfigRepository configs;

    /**
     * The row this would write, parsed and validated <strong>at construction</strong>.
     *
     * <p>So a malformed founding value fails the context on every estate, including one whose table is
     * already populated and which this would never have written to. See {@link FoundingTerms} for why
     * that is the right direction: a variable silently ignored today and pricing every booking the day
     * the table is next empty is this repository's most frequently rediscovered defect shape.
     *
     * <p>Not the {@link BrokerageConfig} itself — that is an entity, and holding one across the life of a
     * singleton is holding a row. {@link FoundingTerms#founding()} builds a fresh one each call.
     */
    private final FoundingTerms terms;

    private volatile boolean running;

    public BrokerageBootstrap(BrokerageConfigRepository configs, FoundingTerms terms) {
        this.configs = configs;
        this.terms = terms;
        terms.founding();
    }

    /**
     * Before everything. See the class javadoc: {@code BookingEventConsumer}'s listener container must
     * not have consumed a {@code booking.completed} by the time this has decided.
     */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void start() {
        bootstrap();
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
     * Writes the founding row if, and only if, {@code brokerage_config} is empty.
     *
     * <p>Package-private so the decision can be exercised without a container. It is the whole of the
     * class's behaviour; {@link #start()} is plumbing.
     */
    void bootstrap() {
        if (alreadyConfigured()) {
            LOG.debug("brokerage: terms already present, leaving them alone");
            return;
        }
        BrokerageConfig founding = configs.save(terms.founding());
        // At INFO and naming every value, because this is the one moment a person can catch a
        // plausible-but-wrong deployment input. It happens once in an estate's life, on the day it is
        // created, while somebody is watching a deploy.
        LOG.info(
            "brokerage: founded this estate's terms — commission {} of {}, payout lag {} day(s), " +
            "free cancellation {}h then {} of the price, effective from {} (decisions.md D57)",
            founding.getCommissionRate().toPlainString(),
            founding.getCurrency(),
            founding.getPayoutLagDays(),
            founding.getFreeCancellationHours(),
            founding.getLateCancellationPct().toPlainString(),
            founding.getEffectiveFrom()
        );
    }

    /**
     * Whether this estate already has terms of its own.
     *
     * <p>{@code count()}, not "is there one in force" — the question is whether anything has ever been
     * decided here, and a row is a decision whatever it is dated. Asking the narrower question would
     * make this write a second founding row beside somebody's deliberately future-dated one.
     */
    private boolean alreadyConfigured() {
        return configs.count() > 0;
    }
}
