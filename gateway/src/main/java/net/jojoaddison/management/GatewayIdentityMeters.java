package net.jojoaddison.management;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registrations by activation state, and logins by outcome — {@code decisions.md} D84, backlog NEW-43.
 *
 * <p>The architect asked for a dashboard of "registrations aggregated by (activated | not-activated)"
 * and "logins aggregated by (success | failed)". Those are <strong>two different kinds of
 * measurement</strong> and this class is where the difference is made, because getting it wrong gives
 * two panels that are each wrong in their own way:
 *
 * <ul>
 *   <li><strong>Logins are an event stream</strong>, so they are {@link Counter}s — monotonic, nothing
 *       to look up afterwards, and a failure has no later state to correct.
 *   <li><strong>Registrations by activation state are the state of a collection</strong>, so they are
 *       <em>gauges</em>. Activation happens <em>after</em> registration ({@code UserService} writes
 *       {@code setActivated(false)} on register and flips it on {@code activateAccount}), so a counter
 *       incremented at registration could never move when the user later activates — it would be
 *       describing a bucket that had already closed. This is also the repository's central rule:
 *       <em>derived, never stored</em>.
 * </ul>
 *
 * <p><strong>"Failed" is not one bucket, and that is the request's own answer to itself.</strong>
 * {@code DomainUserDetailsService} throws {@link net.jojoaddison.security.UserNotActivatedException}
 * for an account that exists and has never been activated — which is <em>exactly</em> the outcome the
 * registration panel exists to explain. Folding it in with a wrong password erases the one number that
 * links the two halves of the dashboard, so {@link Outcome} has four values and the "failed" of the
 * request is their sum.
 *
 * <p><strong>NO LOGIN, EMAIL OR ALIAS MAY EVER BECOME A TAG HERE.</strong> Two independent reasons and
 * both are load-bearing: a per-user tag is unbounded cardinality, and a login in a metric label is a
 * disclosure surface that <strong>survives erasure</strong> — nothing re-keys a metric that has already
 * been scraped or pushed, and the erasure sweep (D31/D35/D38/D39) does not visit a metrics backend.
 * Aggregate counts only. The tag sets below are closed, and every value in them is a constant in this
 * file.
 *
 * <p><strong>WHICH REGISTRY THIS IS GIVEN DECIDES WHETHER THE METRICS LEAVE THE PROCESS</strong>
 * ({@code decisions.md} D85, backlog NEW-44), and it is not a {@code @Component} for that reason —
 * {@code IdentityMetricsConfiguration} hands it {@code Metrics.globalRegistry} explicitly.
 *
 * <p>The OpenTelemetry agent's Micrometer bridge adds an OTel-backed registry as a <em>child</em> of
 * {@code Metrics.globalRegistry}, and a composite registry only forwards meters registered
 * <em>through it</em> — it does not index what its children register on themselves. Measured:
 *
 * <pre>
 *   registered on a child registry   → that child sees it; the OTHER children do NOT
 *   registered on Metrics.globalRegistry → every child sees it, including the agent's
 * </pre>
 *
 * <p>So constructing this with the injected {@code MeterRegistry} bean — which is the
 * {@code PrometheusMeterRegistry} — publishes to the exposition endpoint and to <strong>nothing
 * else</strong>: the dashboard's series would never arrive however the transport were wired, and every
 * panel would read "No data" for a reason no configuration file would show. D84 did exactly that and
 * NEW-44's measurement is what found it.
 *
 * <p>The constructor still takes any {@link MeterRegistry}, so a unit test can pass a
 * {@code SimpleMeterRegistry} and a naming test a real {@code PrometheusMeterRegistry}. What must not
 * happen is the <em>application</em> handing it a child.
 *
 * <p><strong>The gauges are read from an {@link AtomicLong} rather than from the database.</strong> A
 * Micrometer gauge supplier is called synchronously by whatever is reading the registry, and the
 * gateway's Mongo access is reactive — a supplier that blocked on it would block a scrape or an export.
 * {@code IdentityMetricsRefresher} in {@code service} owns the refresh; this class owns the numbers it
 * publishes and knows nothing about where they come from.
 */
public class GatewayIdentityMeters {

    /** Logins, by what happened. One counter per {@link Outcome}; see the class javadoc for why four. */
    public static final String LOGINS_METER = "gateway.identity.logins";

    /** Accounts, by whether they have been activated. A gauge, not a counter — class javadoc. */
    public static final String ACCOUNTS_METER = "gateway.identity.accounts";

    /** The tag both meters use for their dimension. Values are the enums below and nothing else. */
    public static final String OUTCOME_TAG = "outcome";

    /** The tag {@link #ACCOUNTS_METER} uses. Values: {@code activated}, {@code not-activated}. */
    public static final String STATE_TAG = "state";

    /**
     * What happened to a login attempt.
     *
     * <p>{@code SUCCESS} plus the three refusals. The request asked for two buckets and this is four,
     * for the reason in the class javadoc: {@code NOT_ACTIVATED} is the outcome that ties a login
     * failure to a registration that was never completed, and it is invisible if "failed" is one number.
     * {@code ERROR} is everything else — a store that could not be reached, say — and it is deliberately
     * separate from {@code BAD_CREDENTIALS} so that an outage does not read as a password-guessing
     * spike.
     */
    public enum Outcome {
        SUCCESS("success"),
        BAD_CREDENTIALS("bad-credentials"),
        NOT_ACTIVATED("not-activated"),
        ERROR("error");

        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    private final Counter success;
    private final Counter badCredentials;
    private final Counter notActivated;
    private final Counter error;

    /**
     * The two account numbers the gauges publish. {@code -1} until the first refresh has answered, and
     * that is not a placeholder — it is the honest value. Zero would mean "this estate holds no
     * unactivated accounts", which is a claim, and a dashboard cannot tell a real zero from a refresh
     * that has not run yet. A negative reading is obviously not a count and a panel can say so.
     */
    private final AtomicLong activated = new AtomicLong(-1);
    private final AtomicLong notActivatedAccounts = new AtomicLong(-1);

    public GatewayIdentityMeters(MeterRegistry registry) {
        this.success = counter(registry, Outcome.SUCCESS);
        this.badCredentials = counter(registry, Outcome.BAD_CREDENTIALS);
        this.notActivated = counter(registry, Outcome.NOT_ACTIVATED);
        this.error = counter(registry, Outcome.ERROR);

        // EVERY COUNTER IS REGISTERED AT STARTUP, including the ones nothing has incremented. Micrometer
        // creates a counter on first use otherwise, so a series would be ABSENT rather than zero until
        // the first failure — and a dashboard cannot tell "nobody has failed to log in" from "this panel
        // is querying a name that does not exist". `absent()` is what D63 found alerting on for the same
        // reason, one signal along.
        registry.gauge(ACCOUNTS_METER, Tags.of(STATE_TAG, "activated"), activated, AtomicLong::doubleValue);
        registry.gauge(ACCOUNTS_METER, Tags.of(STATE_TAG, "not-activated"), notActivatedAccounts, AtomicLong::doubleValue);
    }

    private static Counter counter(MeterRegistry registry, Outcome outcome) {
        return Counter
            .builder(LOGINS_METER)
            .description("Login attempts at POST /api/authenticate, by outcome")
            .baseUnit("attempts")
            .tag(OUTCOME_TAG, outcome.tagValue())
            .register(registry);
    }

    /** Record one login attempt. Called from the authentication manager decorator and nowhere else. */
    public void recordLogin(Outcome outcome) {
        switch (outcome) {
            case SUCCESS -> success.increment();
            case BAD_CREDENTIALS -> badCredentials.increment();
            case NOT_ACTIVATED -> notActivated.increment();
            case ERROR -> error.increment();
        }
    }

    /**
     * Publish the account split. Called by the refresher; both numbers are set from one observation so a
     * dashboard cannot add them and get a total that existed at no single moment.
     */
    public void setAccounts(long activatedCount, long notActivatedCount) {
        activated.set(activatedCount);
        notActivatedAccounts.set(notActivatedCount);
    }

    /** For tests: what the gauges currently publish. */
    public long activatedAccounts() {
        return activated.get();
    }

    /** For tests: what the gauges currently publish. */
    public long notActivatedAccounts() {
        return notActivatedAccounts.get();
    }
}
