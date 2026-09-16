package net.jojoaddison.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the account split is PUBLISHED — backlog NEW-57.
 *
 * <p>This class exists because of a failure nobody had a test for.
 * {@code GatewayIdentityMetricsIT.gaugesPartitionTheCollection} went red in CI on a branch whose entire
 * diff was four markdown files:
 *
 * <pre>
 *   expected: 6L
 *    but was: 2L
 * </pre>
 *
 * <p>The gauges were holding a reading taken when the collection had two documents in it.
 * {@code IdentityMetricsRefresher} runs {@code Flux.interval(...).concatMap(tick -> refresh())}, and
 * {@code concatMap} serialises the timer against <em>itself and nothing else</em> — so an explicit
 * {@code refresh()}, which that class's contract invites, ran concurrently with a slow first tick and the
 * older numbers landed last.
 *
 * <p><strong>Two javadoc claims were stronger than the code, and these tests are what makes them
 * true.</strong> That is the point rather than the coverage: both statements read as guarantees and both
 * were prose.
 *
 * <ol>
 *   <li>{@code refresh()} — <em>"drive exactly one observation and know when it has landed"</em>. True of
 *       landing, false of <strong>standing</strong>.
 *   <li>{@code setAccounts} — <em>"a dashboard cannot add them and get a total that existed at no single
 *       moment"</em>. The pair was zipped from one query pair, then written with two sequential
 *       {@code AtomicLong.set} calls.
 * </ol>
 *
 * <p><strong>Every test here carries a control</strong>, because both properties are of a shape that a
 * broken implementation satisfies by accident. A class that refused every write would pass "a staler
 * observation is discarded"; a concurrency test whose threads never actually interleave passes
 * "the pair is never torn" having proved nothing. The controls are named where they appear.
 *
 * <p>These are unit tests on purpose. The defect is in how two numbers are published and needs no Mongo,
 * no Spring context and no timer — and the integration test that found it could only ever find it by
 * luck, which is how it survived a green pull request.
 */
class GatewayIdentityMetersPublicationUnitTest {

    private GatewayIdentityMeters meters() {
        return new GatewayIdentityMeters(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("before any observation the gauges read -1, which is not a count and not a zero")
    void theInitialReadingIsNegative() {
        var meters = meters();

        assertThat(meters.activatedAccounts()).isEqualTo(-1);
        assertThat(meters.notActivatedAccounts()).isEqualTo(-1);
        assertThat(meters.accounts().sequence())
            .as("no observation has landed, so any real sequence must beat it")
            .isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("a staler observation is discarded rather than published over a fresher one")
    void aStalerObservationIsDiscarded() {
        var meters = meters();

        assertThat(meters.setAccounts(10, 2, 5)).as("the first observation is published").isTrue();

        // THE DEFECT, reproduced: observation 4 was issued before 5 and completed after it. Before
        // NEW-57 this overwrote the gauges and the dashboard regressed to a reading from the past.
        assertThat(meters.setAccounts(1, 1, 4)).as("an older observation is refused").isFalse();

        assertThat(meters.activatedAccounts()).isEqualTo(10);
        assertThat(meters.notActivatedAccounts()).isEqualTo(2);
        assertThat(meters.accounts().sequence()).isEqualTo(5);

        // THE CONTROL. Without this a class whose setAccounts always returned false and never wrote
        // anything would pass every assertion above.
        assertThat(meters.setAccounts(11, 3, 6)).as("a newer observation is still published").isTrue();
        assertThat(meters.activatedAccounts()).isEqualTo(11);
        assertThat(meters.notActivatedAccounts()).isEqualTo(3);
    }

    @Test
    @DisplayName("an equal sequence is refused, because a published reading may only move forward")
    void anEqualSequenceIsRefused() {
        var meters = meters();
        meters.setAccounts(10, 2, 5);

        assertThat(meters.setAccounts(99, 99, 5)).isFalse();
        assertThat(meters.activatedAccounts()).isEqualTo(10);
        assertThat(meters.notActivatedAccounts()).isEqualTo(2);
    }

    /**
     * The pair cannot be read torn, measured rather than asserted in prose.
     *
     * <p>Every observation published here sums to {@link #TOTAL}, so a reader that ever sees a different
     * sum has combined two observations — which is exactly what two sequential {@code set} calls allowed
     * and what one reference swap makes impossible. The exposition endpoint is scraped concurrently with
     * the refresh, so this reader is a real one.
     */
    private static final long TOTAL = 1_000;

    @Test
    @DisplayName("a concurrent reader never sees one observation's activated beside another's not-activated")
    void thePairIsNeverTorn() throws Exception {
        var meters = meters();
        var sequence = new AtomicLong();
        var stop = new AtomicBoolean(false);
        var start = new CountDownLatch(1);
        var torn = new ConcurrentSkipListSet<Long>();
        Set<Long> sequencesSeen = new ConcurrentSkipListSet<>();

        int writers = 4;
        var threads = new Thread[writers + 1];

        for (int w = 0; w < writers; w++) {
            threads[w] = new Thread(() -> {
                await(start);
                while (!stop.get()) {
                    long seq = sequence.incrementAndGet();
                    long activated = seq % (TOTAL + 1);
                    meters.setAccounts(activated, TOTAL - activated, seq);
                }
            });
        }

        threads[writers] = new Thread(() -> {
            await(start);
            while (!stop.get()) {
                GatewayIdentityMeters.AccountSplit split = meters.accounts();
                if (split.sequence() == Long.MIN_VALUE) {
                    continue; // nothing published yet
                }
                sequencesSeen.add(split.sequence());
                long sum = split.activated() + split.notActivated();
                if (sum != TOTAL) {
                    torn.add(sum);
                }
            }
        });

        for (Thread t : threads) {
            t.start();
        }
        start.countDown();
        TimeUnit.MILLISECONDS.sleep(300);
        stop.set(true);
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(10));
            // JOIN WITH A TIMEOUT DOES NOT ASSERT TERMINATION — found at review. A hung thread would
            // time out silently here and every assertion below could still pass with it running.
            assertThat(t.isAlive()).as("every writer and the reader must have stopped").isFalse();
        }

        // THE CONTROL, and it is the whole reason this test means anything: if the reader never actually
        // raced the writers it would have seen one reading, and "no torn pair" would be true of a class
        // that never published at all. Assert the race happened before believing its result.
        assertThat(sequencesSeen)
            .as("the reader must have observed many distinct observations, or it never raced anything")
            .hasSizeGreaterThan(10);

        assertThat(torn)
            .as("every reading must sum to %s; any other sum is two observations added together", TOTAL)
            .isEmpty();

        // And the standing reading belongs to the newest observation that was published, not to whichever
        // thread happened to write last.
        assertThat(meters.accounts().sequence()).isPositive();
        assertThat(meters.activatedAccounts() + meters.notActivatedAccounts()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("under concurrent writers the standing sequence is the highest one published")
    void theStandingReadingIsTheNewest() throws Exception {
        var meters = meters();
        int writers = 8;
        int each = 500;
        var start = new CountDownLatch(1);
        var threads = new Thread[writers];

        for (int w = 0; w < writers; w++) {
            int base = w * each;
            threads[w] = new Thread(() -> {
                await(start);
                for (int i = 1; i <= each; i++) {
                    long seq = base + i;
                    meters.setAccounts(seq, -seq, seq);
                }
            });
        }
        for (Thread t : threads) {
            t.start();
        }
        start.countDown();
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(10));
            assertThat(t.isAlive()).as("every writer must have stopped").isFalse();
        }

        long highest = (long) writers * each;
        assertThat(meters.accounts().sequence()).isEqualTo(highest);
        assertThat(meters.activatedAccounts())
            .as("the numbers must be the ones that arrived WITH the winning sequence")
            .isEqualTo(highest);
        assertThat(meters.notActivatedAccounts()).isEqualTo(-highest);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
