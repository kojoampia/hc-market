package net.jojoaddison.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * The one fact {@code deploy-prod.sh}'s smoke test asks payout for: whether this estate holds brokerage
 * terms in force — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>Why this exists beside {@link BrokerageTermsHealthIndicator}, which answers the same question</h2>
 *
 * <p>Because the health endpoint's answer is an <strong>aggregate</strong>, and the smoke test needs a
 * single fact. This was written as a health probe first and a real {@code prod} boot against an empty
 * throwaway database settled it: {@code /management/health} answered {@code DOWN} on an estate whose
 * founding row was present and correct, because {@code binders.kafka} was down with no broker on the
 * machine. A smoke test reading that would have failed the deploy of a healthy stack over a broker
 * blip — and a failing smoke test here does not warn, it rolls the deployment back (D49). That is the
 * WP-19 defect, rebuilt by the check meant to prevent a different one.
 *
 * <p>Note what {@code deploy-prod.sh}'s own {@code health_gate} already knew: it probes
 * {@code /management/health/readiness}, which is {@code readinessState,db} and deliberately not the
 * aggregate. The smoke test had no such narrowing available, so this supplies one.
 *
 * <p>A named health <em>group</em> would have been the obvious narrowing and is rejected in
 * {@link BrokerageTermsHealthIndicator}: a group is configuration, {@code application.yml} is
 * regenerated wholesale, and the generated test copy shadows it. A {@code @Component} in a new file
 * needs no configuration and survives a regeneration. The indicator stays where it is — it is what a
 * dashboard reads, and it is out of the readiness group so it can roll nothing back.
 *
 * <h2>What it publishes, and why that is safe</h2>
 *
 * <p>{@code termsInForce}, and when true the commission rate, the currency and the effective instant.
 * The rate is public — the prototype prints "12% brokerage fee included" on every listing — and
 * {@code /management} is 404 at the public edge ({@code prod-server/hc-market-app.conf}). Nothing here
 * is about a person, a booking or an amount somebody paid.
 *
 * <p>Printing the rate is not decoration either: a deploy that reports what this estate is charging is
 * the last moment a plausible-but-wrong founding value — {@code 0.15} where {@code 0.12} was meant — can
 * be caught by a human, and it is the one failure {@link FoundingTerms}' validation cannot see.
 *
 * <p>It asks {@link BrokerageTerms} rather than counting rows, for the same reason the indicator does:
 * a table holding only a future-dated row prices nothing, and both real callers fail identically
 * against it.
 */
@Component
public class BrokerageTermsInfoContributor implements InfoContributor {

    private final BrokerageTerms terms;

    public BrokerageTermsInfoContributor(BrokerageTerms terms) {
        this.terms = terms;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("brokerage", brokerage());
    }

    /**
     * Package-private and returning the map itself so a test can assert the fact without an HTTP round
     * trip and without restating Jackson's rendering of it.
     *
     * <p>A {@link LinkedHashMap} rather than {@code Map.of}: the order is what an operator reads off a
     * deploy, and an unordered map would shuffle it between runs.
     */
    Map<String, Object> brokerage() {
        Map<String, Object> detail = new LinkedHashMap<>();
        return terms
            .inForceAt(Instant.now())
            .map(config -> {
                detail.put("termsInForce", true);
                detail.put("commissionRate", config.getCommissionRate().toPlainString());
                detail.put("currency", config.getCurrency());
                detail.put("effectiveFrom", config.getEffectiveFrom().toString());
                return detail;
            })
            .orElseGet(() -> {
                detail.put("termsInForce", false);
                return detail;
            });
    }
}
