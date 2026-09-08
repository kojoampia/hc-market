package net.jojoaddison.service;

import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Whether this payout service can price anything at all — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>Why a health indicator and not a test</h2>
 *
 * <p>{@link BrokerageBootstrap} is the remedy and its tests are what prove it works. This says, at run
 * time, whether an estate is in the state the remedy exists to prevent. NEW-18's whole character is
 * that the estate stays green while it is broken — booking is happy, the consumer retries in its own
 * log, the catalogue smoke test passes, every container reports ready — so an estate that cannot price
 * anything should say so somewhere a dashboard and a container healthcheck can see.
 *
 * <p>Reachable without a token: {@code SecurityConfiguration} permits {@code /management/health}, and
 * {@code /management} is 404 at the public edge. It discloses one word — {@code show-details} is
 * {@code when_authorized}, so the details below reach an admin and nobody else.
 *
 * <h2>The deploy does NOT read this, and that was learned the expensive way</h2>
 *
 * <p>{@code deploy-prod.sh}'s smoke test read the aggregate {@code /management/health} in the first
 * version of D57, and a real {@code prod} boot against an empty throwaway database found the defect:
 * the aggregate answered <strong>DOWN on an estate whose founding row was present and correct</strong>,
 * because {@code binders.kafka} was down with no broker on that machine. A smoke test reading it would
 * have failed the deploy of a healthy stack over a broker blip, and a failing smoke test here does not
 * warn — it rolls the deployment back (D49). That is WP-19's defect rebuilt by the check meant to
 * prevent a different one. {@code health_gate} already knew, and probes
 * {@code /management/health/readiness} rather than the aggregate.
 *
 * <p>So the deploy asks {@link BrokerageTermsInfoContributor} instead, which is about one thing. A
 * {@code /management/health/brokerage} group would have been the other way to narrow it and is
 * rejected: a group is configuration, {@code application.yml} is regenerated wholesale and the
 * generated {@code src/test/resources} copy shadows it, so the path would have to be spelled in two
 * files that drift — and losing either makes the request a 404, which fails a healthy deploy in the
 * other direction. A {@code @Component} in a new file needs no configuration and cannot go missing.
 *
 * <h2>"In force", not "exists"</h2>
 *
 * <p>An empty table is the defect, and it is not the only way to have one. A table holding only a
 * <em>future-dated</em> row prices nothing either, and the failure is identical from the consumer's and
 * the receipt's point of view — D53 and D56 both select the latest config whose {@code effectiveFrom}
 * has already passed. So the question asked here is the one the two callers ask, through the same
 * {@link BrokerageTerms} selector, rather than a count that would report a healthy estate holding terms
 * that take effect next year.
 *
 * <p><strong>It reads a clock, and that is the right thing here.</strong> The rule D53 and D56
 * established is that a <em>price</em> is a function of the event and never of when it was consumed.
 * This prices nothing. "Can you price something now" is a question about now, and substituting any
 * other moment would make the answer about something nobody asked.
 *
 * <h2>What it costs an estate that is fine</h2>
 *
 * <p>Nothing it was not already paying: one {@code findAll()} over a table holding a handful of rows,
 * on a management endpoint. And it cannot report DOWN on a correctly bootstrapped estate —
 * {@link FoundingTerms#EFFECTIVE_FROM} is the epoch, so the founding row is in force at every moment a
 * clock can produce. Reaching DOWN needs the table emptied by hand, or a row dated into the future by
 * hand, or the database gone (which the generated {@code db} indicator reports too, and which is the one
 * overlap).
 *
 * <p>It is <strong>not</strong> in the {@code readiness} group ({@code readinessState,db}), deliberately.
 * A deployment whose gates fail rolls back (D49), and rolling a stack back because it has no commission
 * rate would restore a previous stack that has none either — the rollback cannot fix this, so it must
 * not be triggered by it. The failure belongs in the smoke test, where it is reported and the operator
 * decides.
 *
 * <p>It <em>is</em> in the root aggregate, so an un-bootstrapped payout also fails its compose
 * {@code healthcheck}. That is the intended direction and not a side effect: a service that cannot price
 * anything is not healthy, and being visibly unhealthy is the whole of what NEW-18 was missing. Note
 * what the prod boot above also established about that aggregate, which is not this package's to fix:
 * <strong>a payout with no reachable broker already fails that healthcheck</strong>, because the binder
 * indicator is in the same aggregate. This adds one more reason to a set that was not empty.
 */
@Component
public class BrokerageTermsHealthIndicator implements HealthIndicator {

    private final BrokerageTerms terms;

    public BrokerageTermsHealthIndicator(BrokerageTerms terms) {
        this.terms = terms;
    }

    @Override
    public Health health() {
        return terms
            .inForceAt(Instant.now())
            .map(config ->
                Health.up()
                    .withDetail("commissionRate", config.getCommissionRate().toPlainString())
                    .withDetail("currency", config.getCurrency())
                    .withDetail("effectiveFrom", config.getEffectiveFrom().toString())
                    .build()
            )
            .orElseGet(() ->
                Health.down()
                    .withDetail(
                        "reason",
                        "no BrokerageConfig is in force — this service cannot price a completed booking or a " +
                        "receipt, and BookingEventConsumer will retry every booking.completed for ever. See " +
                        "decisions.md D57, backlog NEW-18."
                    )
                    .build()
            );
    }
}
