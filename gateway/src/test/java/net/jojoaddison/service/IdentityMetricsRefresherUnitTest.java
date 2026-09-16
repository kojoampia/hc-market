package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import net.jojoaddison.domain.User;
import net.jojoaddison.management.GatewayIdentityMeters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

/**
 * What {@link IdentityMetricsRefresher#refresh()} guarantees, and what it does NOT — backlog NEW-57.
 *
 * <p>This class exists because of one word. The fix for NEW-57 gave {@code refresh()} a bolded,
 * unconditional guarantee:
 *
 * <blockquote>"When this Mono completes, the standing reading is this observation or a NEWER one — never
 * an older one."</blockquote>
 *
 * <p><strong>That was false on the error path, in the commit whose entire subject was two javadoc claims
 * outrunning their code.</strong> Found at review. {@code onErrorResume(e -> Mono.empty())} turns a failed
 * count into an <em>empty</em> Mono, so {@code refresh()} completes <em>normally</em> having published
 * nothing — and the standing reading is then whatever stood before, which may be older than this
 * observation or the initial {@code -1}. A caller that blocks on it and concludes "the account I just
 * saved is in the counts" is wrong precisely then, with only a WARN in a log nobody is reading.
 *
 * <p>The sentence now says "having counted successfully". <strong>These tests are why that qualification
 * is worth more than the last one:</strong> it is pinned rather than promised, so the next person to
 * simplify the error handling finds out here instead of in a dashboard.
 *
 * <p>Unit tests with a mocked template on purpose — the question is what the pipeline does when a count
 * fails, which needs no Mongo, no context and no timer. The publication guard itself is pinned by
 * {@code GatewayIdentityMetersPublicationUnitTest}.
 *
 * <p><strong>THE REACH LIMIT, stated rather than discovered later.</strong> Every test here stubs
 * {@code count(any(Query.class), any(Class.class))}, so the two queries are <em>indistinguishable</em> to
 * these tests: a change making both counts {@code is(true)} passes all of them, {@code times(2)} and
 * {@code eq(User.class)} included. What pins the predicates is {@code GatewayIdentityMetricsIT} against a
 * real Mongo — the two gauges summing to {@code userRepository.count()}, and adding an unactivated
 * account moving the dormant side by exactly one. Do not read a green run here as covering
 * {@code is(true)} versus {@code ne(true)}; that distinction is the integration test's to keep.
 */
class IdentityMetricsRefresherUnitTest {

    private static final Duration ANY_INTERVAL = Duration.ofSeconds(60);

    private GatewayIdentityMeters meters() {
        return new GatewayIdentityMeters(new SimpleMeterRegistry());
    }

    /** Neither {@code start()} nor the timer is involved: nothing here calls the {@code @PostConstruct}. */
    private IdentityMetricsRefresher refresher(ReactiveMongoTemplate mongo, GatewayIdentityMeters meters) {
        return new IdentityMetricsRefresher(mongo, meters, ANY_INTERVAL);
    }

    @Test
    @DisplayName("a successful observation is published, and the Mono completes")
    void aSuccessfulObservationIsPublished() {
        var mongo = mock(ReactiveMongoTemplate.class);
        // The two counts are distinguished by their Query, but stubbing both to the same value is enough
        // here: what is under test is the pipeline, not the predicates. 7 and 7 keeps that explicit.
        when(mongo.count(any(Query.class), any(Class.class))).thenReturn(Mono.just(7L));
        var meters = meters();

        refresher(mongo, meters).refresh().block();

        assertThat(meters.activatedAccounts()).isEqualTo(7);
        assertThat(meters.notActivatedAccounts()).isEqualTo(7);
        assertThat(meters.accounts().sequence())
            .as("an observation was published, so the sequence must have moved off its initial value")
            .isGreaterThan(Long.MIN_VALUE);
    }

    /**
     * A failed count leaves the previous reading standing, and the Mono still completes.
     *
     * <p><strong>ONE refresher across both observations, and that is not incidental.</strong> The first
     * version of this test built a fresh refresher per call, so each restarted its own sequence at zero
     * and the second observation carried sequence 1 exactly like the first. A mutation that published
     * {@code (0, 0)} on the error path was then refused by the <em>equal-sequence</em> guard rather than
     * never attempted — so the test passed, and it passed for a reason that had nothing to do with what it
     * claims to check. Found by mutating the error path and watching only its sibling go red.
     *
     * <p>A shared refresher makes the second observation genuinely newer, so the only thing that can keep
     * the reading at 4 is the error path publishing nothing at all.
     */
    @Test
    @DisplayName("a failed count leaves the previous reading standing, and the Mono still COMPLETES")
    void aFailedCountLeavesThePreviousReadingStanding() {
        var mongo = mock(ReactiveMongoTemplate.class);
        var meters = meters();
        var refresher = refresher(mongo, meters);

        // First, a good observation, so there is something to be left standing. Without this the test
        // could not tell "the previous reading stood" from "nothing was ever published".
        when(mongo.count(any(Query.class), any(Class.class))).thenReturn(Mono.just(4L));
        refresher.refresh().block();
        long standingSequence = meters.accounts().sequence();
        assertThat(meters.activatedAccounts()).isEqualTo(4);

        // Now the store cannot be reached.
        when(mongo.count(any(Query.class), any(Class.class)))
            .thenReturn(Mono.error(new IllegalStateException("mongo is unreachable")));

        // IT COMPLETES NORMALLY. Not an error, not empty-with-a-signal — this is the whole finding: a
        // caller cannot tell from the Mono that nothing was published.
        Boolean completed = refresher.refresh().hasElement().block();
        assertThat(completed).as("refresh() must never propagate the error to its caller").isFalse();

        assertThat(meters.activatedAccounts())
            .as("the previous reading stands — a store that could not be reached is not an estate with no accounts")
            .isEqualTo(4);
        assertThat(meters.notActivatedAccounts()).isEqualTo(4);
        assertThat(meters.accounts().sequence())
            .as("NOTHING was published, so the standing sequence is still the earlier observation's")
            .isEqualTo(standingSequence);
    }

    @Test
    @DisplayName("the gauges stay at -1 when the very first observation fails, rather than reading zero")
    void aFirstObservationThatFailsPublishesNothingAtAll() {
        var mongo = mock(ReactiveMongoTemplate.class);
        when(mongo.count(any(Query.class), any(Class.class)))
            .thenReturn(Mono.error(new IllegalStateException("mongo is unreachable")));
        var meters = meters();

        refresher(mongo, meters).refresh().block();

        // -1 AND NOT 0. Zero would be a claim — "this estate holds no unactivated accounts" — and a
        // dashboard cannot tell a real zero from a refresh that has never succeeded.
        assertThat(meters.activatedAccounts()).isEqualTo(-1);
        assertThat(meters.notActivatedAccounts()).isEqualTo(-1);
        assertThat(meters.accounts().sequence()).isEqualTo(Long.MIN_VALUE);
    }

    /**
     * A count that completes EMPTY is treated as a failed count, not as a silent no-op.
     *
     * <p>Found at review and measured there on reactor-core 3.8.7: {@code Mono.zip} completes
     * <strong>normally</strong> when a source completes empty, skipping {@code doOnNext} <em>and</em>
     * {@code doOnError} — so before the {@code .single()} guard this published nothing and logged nothing,
     * which is quieter than the error path the {@code refresh()} guarantee is careful to qualify.
     *
     * <p>Unreachable through {@code ReactiveMongoTemplate.count}, which emits exactly one element or
     * errors. It is pinned because the fix named in the class javadoc — one aggregation grouping on
     * {@code activated} — is exactly a pipeline that can complete empty on an empty collection, so this
     * test is what stops that change reintroducing the silence.
     */
    @Test
    @DisplayName("a count that completes empty is REPORTED, not silently ignored")
    void anEmptyCountIsTreatedAsAFailure() {
        var mongo = mock(ReactiveMongoTemplate.class);
        var meters = meters();
        var refresher = refresher(mongo, meters);

        when(mongo.count(any(Query.class), any(Class.class))).thenReturn(Mono.just(9L));
        refresher.refresh().block();
        long standingSequence = meters.accounts().sequence();
        assertThat(meters.activatedAccounts()).isEqualTo(9);

        // Mono.empty() rather than an error: this is the path that used to be invisible.
        when(mongo.count(any(Query.class), any(Class.class))).thenReturn(Mono.empty());
        List<ILoggingEvent> heard = whileListening(() -> refresher.refresh().block());

        // THE ASSERTION THAT HAS TEETH, and the first version of this test did not have it. Asserting
        // only "the previous reading stands" passes WITH and WITHOUT the `.single()` guard — measured,
        // by removing the guard and watching all six tests stay green — because an empty zip publishes
        // nothing either way. The whole difference `.single()` makes is that the estate is TOLD.
        assertThat(heard)
            .as("an empty count must produce a WARN; without .single() it is silent, which is the defect")
            .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("could not count accounts"));

        assertThat(meters.activatedAccounts())
            .as("and the previous reading stands, exactly as it does for a failed count")
            .isEqualTo(9);
        assertThat(meters.accounts().sequence())
            .as("nothing was published, so the sequence has not moved")
            .isEqualTo(standingSequence);
    }

    /**
     * Collects what {@link IdentityMetricsRefresher} logs while the given work runs — the shape
     * {@code PaymentConfigurationUnitTest} established in booking and
     * {@code TheZoneACatalogueOffersIsParsedAtCaptureTest} copied. The appender is attached to that one
     * logger and detached in a {@code finally}, so a failure inside the block cannot leave it attached
     * for the rest of the suite.
     */
    private static List<ILoggingEvent> whileListening(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(IdentityMetricsRefresher.class);
        ListAppender<ILoggingEvent> heard = new ListAppender<>();
        heard.start();
        logger.addAppender(heard);
        try {
            work.run();
        } finally {
            logger.detachAppender(heard);
            heard.stop();
        }
        return List.copyOf(heard.list);
    }

    /**
     * Two subscriptions are two observations — the {@code Mono.defer} half of the sequence claim.
     *
     * <p><strong>What this does NOT pin</strong>, said plainly because the javadoc it supports claims
     * more than this test can see: the sequence being taken <em>before the queries are issued</em>. Moving
     * {@code incrementAndGet} into {@code doOnNext} — after the counts have returned — passes every test
     * in this class while silently changing issue-order to completion-order, which is the ordering the
     * {@code AccountSplit} javadoc reasons about. It is a javadoc property with no behavioural
     * consequence today; it is written down rather than asserted, which is the honest state of it.
     */
    @Test
    @DisplayName("each subscription is its own observation, so a held Mono does not reuse a sequence")
    void everySubscriptionTakesItsOwnSequence() {
        var mongo = mock(ReactiveMongoTemplate.class);
        when(mongo.count(any(Query.class), any(Class.class))).thenReturn(Mono.just(1L));
        var meters = meters();
        var refresher = refresher(mongo, meters);

        // THE Mono.defer CLAIM, driven rather than read: the sequence is taken at SUBSCRIBE time, so the
        // same Mono subscribed twice is two observations. Assembled once, deliberately.
        Mono<Void> held = refresher.refresh();
        held.block();
        long first = meters.accounts().sequence();
        held.block();
        long second = meters.accounts().sequence();

        assertThat(second)
            .as("a second subscription must be a second observation, or refresh() could not be retried")
            .isGreaterThan(first);
    }

    @Test
    @DisplayName("User is the document counted, so the query cannot silently move to another collection")
    void itCountsTheUserCollection() {
        var mongo = mock(ReactiveMongoTemplate.class);
        when(mongo.count(any(Query.class), any(Class.class))).thenReturn(Mono.just(2L));
        var meters = meters();

        refresher(mongo, meters).refresh().block();

        org.mockito.Mockito.verify(mongo, org.mockito.Mockito.times(2)).count(any(Query.class), org.mockito.ArgumentMatchers.eq(User.class));
    }
}
