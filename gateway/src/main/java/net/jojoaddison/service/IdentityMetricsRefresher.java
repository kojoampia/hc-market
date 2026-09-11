package net.jojoaddison.service;

import java.time.Duration;
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
 * <p><strong>Both numbers come from one observation.</strong> Two independent counts taken moments apart
 * can disagree — a registration between them makes the pair describe no single moment, and a dashboard
 * adding them for a total would show an account that existed twice or not at all. They are counted
 * together and published together.
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
     * <p>{@code @Scheduled} is deliberately not used: it needs {@code @EnableScheduling} on a generated
     * application class, which a regeneration would discard silently — leaving the gauges frozen at
     * {@code -1} with nothing failing.
     */
    @jakarta.annotation.PostConstruct
    void start() {
        Flux
            .interval(Duration.ZERO, interval)
            .concatMap(tick -> refresh())
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        LOG.info("publishing {} every {}", GatewayIdentityMeters.ACCOUNTS_METER, interval);
    }

    /**
     * One observation of the account split.
     *
     * <p>Public and returning its own {@link Mono} so a caller — the timer above, or a test — can drive
     * exactly one observation and know when it has landed, rather than waiting on the interval. It is not
     * a test backdoor: "observe the split now" is a legitimate operation, and the alternative is a test
     * that sleeps, which is a test that is either slow or flaky.
     *
     * <p>It never throws. A failed count warns and leaves the previous reading in place, so a caller
     * cannot distinguish "refreshed" from "tried and failed" — deliberately, because the two gauges are
     * the answer and the {@link Mono} is only the timing.
     */
    public Mono<Void> refresh() {
        Mono<Long> activated = mongo.count(new Query(Criteria.where(ACTIVATED).is(true)), User.class);
        Mono<Long> notActivated = mongo.count(new Query(Criteria.where(ACTIVATED).ne(true)), User.class);
        // `ne(true)` RATHER THAN `is(false)`, so the two counts partition the collection. A document whose
        // `activated` field is absent or null — which nothing here writes today, but a hand-inserted or
        // migrated user could — would be counted by neither under `is(false)`, and the two gauges would
        // silently not add up to the number of accounts.
        return Mono
            .zip(activated, notActivated)
            .doOnNext(both -> meters.setAccounts(both.getT1(), both.getT2()))
            .doOnError(e ->
                LOG.warn(
                    "could not count accounts for {}, so the previous reading stands and is now stale: {}",
                    GatewayIdentityMeters.ACCOUNTS_METER,
                    e.toString()
                )
            )
            .onErrorResume(e -> Mono.empty())
            .then();
    }
}
