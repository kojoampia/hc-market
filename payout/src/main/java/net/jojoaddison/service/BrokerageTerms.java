package net.jojoaddison.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.repository.BrokerageConfigRepository;
import org.springframework.stereotype.Component;

/**
 * Which of the brokerage's terms were in force at a given moment — the estate's only answer to that
 * question, {@code decisions.md} D56.
 *
 * <h2>Why one selector and not two</h2>
 *
 * <p>There were two, character for character: {@code BookingEventConsumer.configInForce}, which
 * prices a ledger row, and {@code BrokerageResource.inForce}, which prices the receipt the customer
 * reads for the same booking. D53 declined to merge them on the grounds that "they already agree on
 * the rule", which is the weakest argument available — the value of merging is that they keep
 * agreeing — and there was a concrete defect behind it rather than only a tidiness argument.
 *
 * <p>Both took {@code Stream.max(comparing(effectiveFrom))} over an unordered {@code findAll()}.
 * {@code Stream.max} returns an <em>arbitrary</em> element among equals — documented behaviour, not
 * a JDK bug — so two configs sharing an {@code effectiveFrom} resolved non-deterministically, and
 * the two copies could pick different rows <strong>in the same JVM</strong>: a receipt and a ledger
 * row disagreeing with no rate change between them, and a ledger row that records the amounts a rate
 * produced and never the rate itself. Nothing in the schema stops that pair existing.
 *
 * <p>There was never an obstacle to merging, which is worth stating because two of this estate's
 * duplicated derivations ({@code SubjectPseudonym}, {@link MarketCalendar}) are copied files with
 * CI diffing the copies precisely because they cross Maven projects. These two do not: same module,
 * and {@code TechnicalStructureTest} permits {@code web} to reach {@code service}.
 *
 * <h2>The tie-break is decided, not left to the stream</h2>
 *
 * <p><strong>The newest row wins</strong> — the highest {@code id} among those sharing the latest
 * {@code effectiveFrom}. In this schema {@code id} comes from a sequence, so the highest is the most
 * recently inserted, which is the one whoever wrote the second row meant to take effect. Any stated
 * answer beats an arbitrary one; this one has the additional property that it does not depend on the
 * order a database happened to return rows in, so the receipt and the ledger cannot differ even if
 * one of them reads the table with a different plan.
 *
 * <p>A {@code null} id sorts first, so an unsaved entity never outranks a persisted one. That is
 * reachable only from a test holding a hand-built config; it is spelled out rather than left to
 * {@code Comparator.naturalOrder()}, which throws on null.
 *
 * <h2>Why this is not on the generated {@code BrokerageConfigService}</h2>
 *
 * <p>Because the JDL says {@code service BrokerageConfig with serviceClass}, so that class is
 * regenerated wholesale and anything put in it is discarded on the next
 * {@code jhipster jdl --force} — CLAUDE.md's "never name a hand-written class after one the JDL
 * generates", which is the rule {@code BookingWorkflow} exists for in booking. That class is
 * orphaned (NEW-15 deleted its only caller) and is deliberately left where it is: it has no HTTP
 * door, so unlike the nine resources D54 removed it discloses nothing and forges nothing, and
 * deleting a generated class a regeneration restores buys a delete-table row for a hazard the table
 * does not describe. What it does pose is this: it is the obvious-looking place to put a selector.
 * That is what this paragraph is for.
 *
 * @see net.jojoaddison.web.rest.BrokerageResource which asks this for the customer's receipt
 * @see BookingEventConsumer which asks it for the ledger row behind the same booking
 */
@Component
public class BrokerageTerms {

    private final BrokerageConfigRepository configs;

    public BrokerageTerms(BrokerageConfigRepository configs) {
        this.configs = configs;
    }

    /**
     * The latest terms that had already taken effect at {@code at}.
     *
     * <p>Effective-dated rather than simply newest: a rate scheduled for next month must not price a
     * booking completed today, and a rate that took effect last month must not price a booking
     * completed the month before it.
     *
     * <p><strong>An {@code Instant}, and it is the caller's job to have one that means something.</strong>
     * Nothing here reads a clock — that was NEW-13's whole defect, in the consumer, and NEW-16's in
     * the receipt one granularity down. Both callers refuse rather than substitute "now" for a moment
     * they were not given, and they refuse differently because the two contracts are different: the
     * consumer throws so the container retries, the resource answers a status code. That is why this
     * returns an {@link Optional} rather than throwing its own exception — a shared selector that
     * chose the failure would have to choose one of them wrongly.
     */
    public Optional<BrokerageConfig> inForceAt(Instant at) {
        List<BrokerageConfig> all = configs.findAll();
        return all.stream().filter(c -> c.getEffectiveFrom() != null && !c.getEffectiveFrom().isAfter(at)).max(BY_EFFECTIVE_FROM_THEN_NEWEST);
    }

    /**
     * Latest {@code effectiveFrom} first, and among those the highest {@code id}.
     *
     * <p>Private. It was package-private, with a javadoc claiming {@code BrokerageTermsUnitTest}
     * asserted the ordering directly — a claim nothing in the tree made true, since every case there
     * goes through {@link #inForceAt}. The distinction it was reaching for is real ("the tie-break
     * works" against "the rows happened to arrive in a helpful order") and it is answered a better
     * way: that test controls the list and asserts <strong>both</strong> orders, which is what
     * actually goes red when the tie-break is removed. Visibility bought nothing, so it is gone —
     * {@code decisions.md} D56's review.
     */
    private static final Comparator<BrokerageConfig> BY_EFFECTIVE_FROM_THEN_NEWEST = Comparator.comparing(
        BrokerageConfig::getEffectiveFrom
    ).thenComparing(BrokerageConfig::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
}
