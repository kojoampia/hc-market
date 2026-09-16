package net.jojoaddison.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import net.jojoaddison.domain.User;
import net.jojoaddison.management.GatewayIdentityMeters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Keeps the account gauges current — {@code decisions.md} D84, backlog NEW-43.
 *
 * <p><strong>Why a refresher instead of a gauge that queries.</strong> Micrometer calls a gauge's
 * supplier synchronously, from whichever thread is reading the registry, and this gateway's Mongo access
 * is reactive. A supplier that blocked on a count would block a scrape or an OTLP export — on the
 * gateway's event loop, which is the one thing in this estate that must never block. So the counting
 * happens on a schedule, off that path, and {@link GatewayIdentityMeters} publishes whatever the last
 * observation said.
 *
 * <p><strong>Both numbers are PUBLISHED as one observation. They are not COUNTED as one, and the
 * difference is a residual rather than a fix</strong> — narrowed at review of NEW-57, because the
 * paragraph here used to claim both. Publication is now a single reference swap, so no reader can add
 * one observation's {@code activated} to another's {@code notActivated}
 * ({@code GatewayIdentityMeters.AccountSplit}). But the counting is still <strong>two independent
 * queries</strong> joined by {@code Mono.zip}: if an account activates between them, the {@code is(true)}
 * count ran too early to see it and the {@code ne(true)} count ran too late, so the pair sums to one less
 * than the collection holds — a total that existed at no single moment, now published atomically.
 *
 * <p>It is self-correcting at the next tick, it can never make the reading go backwards, and it needs a
 * registration inside a two-query window. It is not fixed here because the fix is a different change —
 * one aggregation grouping on {@code activated} rather than two counts — with its own null-key handling
 * for a document that has no such field. Worth knowing before anyone writes a test that asserts the two
 * gauges sum to the collection size and expects it to hold under concurrent writes; the one integration
 * test that does is safe only because those tests run sequentially, and it says so.
 *
 * <p><strong>It reads through {@link ReactiveMongoTemplate} and not through a repository method.</strong>
 * {@code UserRepository} is generated, so a {@code countByActivated} added to it is discarded by the next
 * {@code jhipster jdl --force} — the same reason {@code MarketplaceService} exists in catalog rather than
 * a method on the generated repository. The query is on {@code activated}, which is a field of the
 * generated {@link User} document and is not going anywhere: it is what
 * {@code registerAccount} sets false and {@code activateAccount} sets true.
 *
 * <p><strong>A failed refresh leaves the previous reading in place and says so at WARN.</strong> It does
 * not zero the gauges: a store that could not be reached is not an estate with no accounts, and writing
 * zero would turn a database blip into a dashboard claiming every account had vanished. The reading goes
 * stale instead, which is visible as a flat line beside a warning rather than invisible as a plausible
 * number.
 *
 * <p><strong>No login, email or identifier is read, logged or published here</strong> — only two counts.
 * That is what keeps this off the erasure sweep's list entirely (D31/D35/D38/D39): there is nothing about
 * a person in a count, so there is nothing for an erasure to come back and correct.
 */
@Component
public class IdentityMetricsRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityMetricsRefresher.class);

    /** The field {@code registerAccount} sets false and {@code activateAccount} sets true. */
    private static final String ACTIVATED = "activated";

    private final ReactiveMongoTemplate mongo;
    private final GatewayIdentityMeters meters;
    private final Duration interval;

    /**
     * Which observation this is. Monotonic, taken before each observation's queries go out, and the only
     * thing that lets {@link GatewayIdentityMeters#setAccounts} tell a fresh reading from a slow one that
     * completed late — NEW-57. It is never read for anything else, so wrapping at {@link Long#MAX_VALUE}
     * is not a concern: at one observation a second it would take some 292 billion years.
     */
    private final AtomicLong observations = new AtomicLong();

    public IdentityMetricsRefresher(
        ReactiveMongoTemplate mongo,
        GatewayIdentityMeters meters,
        @Value("${gateway.identity-metrics.refresh-interval:60s}") Duration interval
    ) {
        this.mongo = mongo;
        this.meters = meters;
        this.interval = interval;
    }

    /**
     * Refresh on a timer, starting immediately.
     *
     * <p>The first tick is at <strong>zero</strong>, not at the interval. A gauge that reads {@code -1}
     * for the first minute of a service's life is a dashboard that looks broken on every deploy, and this
     * is the same mistake the SSE heartbeat made in reverse (its first tick was an interval away, so no
     * response was committed for twenty seconds).
     *
     * <p><strong>{@code @Scheduled} is deliberately not used, and the reason given here was wrong until
     * NEW-57.</strong> It said {@code @Scheduled} "needs {@code @EnableScheduling} on a generated
     * application class, which a regeneration would discard silently". Measured for {@code decisions.md}
     * D91: {@code @EnableScheduling} is on the generated {@code config/AsyncConfiguration}, active in all
     * five services and on every profile that runs — so a regeneration <em>restores</em> it rather than
     * discarding it, and {@code @Scheduled} works here today. {@code UserService} uses one.
     *
     * <p>The real reason is composition. {@link #refresh()} returns a {@link Mono}, and
     * {@code Flux.interval(...).concatMap(...)} consumes it natively: one observation at a time, on a
     * scheduler that is not the event loop, with backpressure if Mongo is slow. (Precisely: the interval
     * ticks on {@code Schedulers.parallel()} and {@code setAccounts} runs on the Mongo driver's thread —
     * {@code subscribeOn} moves the subscription and not every signal. Nothing blocks on any of them,
     * which is the property that matters.) A {@code @Scheduled} void
     * method would have to {@code block()} — which is banned here and is what BlockHound would catch — or
     * subscribe and discard, which drops the serialisation {@code concatMap} provides.
     */
    @jakarta.annotation.PostConstruct
    void start() {
        Flux
            .interval(Duration.ZERO, interval)
            // THE BELT: refresh() handles an error from the COUNTS, but a throw from inside its own
            // Mono.defer supplier — building a Query, say — errors the outer Mono instead, and an error
            // reaching concatMap CANCELS THE INTERVAL. The timer would then be dead for the life of the
            // process, with one onErrorDropped line and gauges frozen at their last reading: silent, total
            // and permanent, which is the worst failure shape available here. Unreachable today, found at
            // review, and one line to make "it never throws" true of the timer as well as of refresh().
            .concatMap(tick -> refresh().onErrorResume(e -> Mono.empty()))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        LOG.info("publishing {} every {}", GatewayIdentityMeters.ACCOUNTS_METER, interval);
    }

    /**
     * One observation of the account split.
     *
     * <p>Public and returning its own {@link Mono} so a caller — the timer above, or a test — can drive
     * exactly one observation rather than waiting on the interval. It is not a test backdoor: "observe the
     * split now" is a legitimate operation, and the alternative is a test that sleeps, which is a test
     * that is either slow or flaky.
     *
     * <p><strong>When this {@link Mono} completes having counted successfully, the standing reading is
     * this observation or a NEWER one — never an older one.</strong> That is the guarantee, and it is
     * narrower than the one this javadoc used to give. It said a caller could "know when it has landed",
     * which was true of landing and false of <em>standing</em>: nothing serialises an explicit
     * {@code refresh()} against the timer above, so a slow timer observation could complete afterwards and
     * write its older numbers over this one. The gauge then regressed to a reading from the past, silently.
     *
     * <p><strong>"Having counted successfully" is load-bearing and the first version of this sentence
     * omitted it</strong> — found at review, in the commit whose whole subject is a claim outrunning its
     * code. The counts can fail: {@code onErrorResume} below turns that into an <em>empty</em>
     * {@code Mono}, so this one <em>completes normally having published nothing</em>, and the standing
     * reading is then whatever stood before — possibly older than this observation, possibly the initial
     * {@code -1}. A caller that blocks on this and concludes "the account I just saved is in the counts"
     * is wrong in exactly that case. {@code IdentityMetricsRefresherUnitTest} pins it, because a
     * qualification nothing tests is the next version of the sentence this one replaced.
     *
     * <p>That is NEW-57, and it is fixed in {@link GatewayIdentityMeters#setAccounts} rather than here:
     * each observation takes a <strong>sequence before it queries</strong>, and a write whose sequence is
     * not the newest is discarded. Serialising the two callers was the alternative and it is the wrong
     * trade — it needs a sink, a completion signal per enqueued observation, and it would make an
     * explicit refresh wait on a Mongo round trip it does not need.
     *
     * <p>The sequence is taken at <strong>subscribe</strong> time and not at assembly, hence the
     * {@link Mono#defer}: a {@code Mono} held and subscribed twice is two observations, and the number has
     * to say when the queries went out rather than when the pipeline was built.
     *
     * <p>It never throws. A failed count warns and leaves the previous reading in place, so a caller
     * cannot distinguish "refreshed" from "tried and failed" — deliberately, because the two gauges are
     * the answer and the {@link Mono} is only the timing.
     */
    public Mono<Void> refresh() {
        return Mono.defer(() -> {
            long sequence = observations.incrementAndGet();
            Mono<Long> activated = mongo.count(new Query(Criteria.where(ACTIVATED).is(true)), User.class);
            Mono<Long> notActivated = mongo.count(new Query(Criteria.where(ACTIVATED).ne(true)), User.class);
            // `ne(true)` RATHER THAN `is(false)`, so the two counts partition the collection. A document
            // whose `activated` field is absent or null — which nothing here writes today, but a
            // hand-inserted or migrated user could — would be counted by neither under `is(false)`, and the
            // two gauges would silently not add up to the number of accounts.
            return Mono
                .zip(activated, notActivated)
                .doOnNext(both -> {
                    if (!meters.setAccounts(both.getT1(), both.getT2(), sequence)) {
                        // DEBUG, not WARN. A discarded observation is the guard working: the numbers were
                        // right when taken and a later reading has already superseded them. Nothing is
                        // wrong and nobody needs to act, so warning here would train people to ignore the
                        // level that the genuinely stale case above uses.
                        LOG.debug(
                            "observation {} of {} completed after a newer one and was discarded",
                            sequence,
                            GatewayIdentityMeters.ACCOUNTS_METER
                        );
                    }
                })
                .doOnError(e ->
                    LOG.warn(
                        "could not count accounts for {}, so the previous reading stands and is now stale: {}",
                        GatewayIdentityMeters.ACCOUNTS_METER,
                        e.toString()
                    )
                )
                .onErrorResume(e -> Mono.empty())
                .then();
        });
    }
}
