package net.jojoaddison.service;

import java.math.BigDecimal;
import java.time.Instant;
import net.jojoaddison.domain.BrokerageConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The brokerage terms a brand-new estate starts life with, under {@code healthconnect.brokerage.founding}
 * — {@code decisions.md} D57, backlog NEW-18.
 *
 * <h2>What this is for</h2>
 *
 * <p>{@code brokerage_config} was empty on any estate that does not seed, and production does not
 * seed. {@code BookingEventConsumer.configInForce} then threw and the consumer retried for ever — no
 * ledger row, {@code /api/pro/earnings} stuck at zero, booking perfectly happy because the failure was
 * entirely inside payout — and {@code BrokerageResource.inForce} answered 503, so every receipt was a
 * 503. Both land after the customer's money has already moved. {@link BrokerageBootstrap} writes one
 * row from these values into an empty table; this class is where the values come from.
 *
 * <h2>Every value here has a default, and the default is the founding rate</h2>
 *
 * <p><strong>An estate that sets none of these is priced correctly.</strong> That is the point of the
 * defaults and it is the answer to the obvious objection to making commercial terms a deployment
 * input: a deployment input that must be set is one more thing to get wrong by omission, and getting
 * it wrong by omission is exactly the failure this package exists to remove. Setting one is a
 * deliberate act; setting none is the ratified answer.
 *
 * <p>The defaults are the prototype's, which is the only place this platform's commercial terms have
 * ever been written down: {@code Abofonsa_BridgeCare_Marketplace.html} declares
 * {@code const COMMISSION = 0.12} and {@code const PAYOUT_LAG = 3} and prints "12% brokerage fee
 * included" on every listing, and {@code extract-seed.mjs} carries them into
 * {@code deploy/demo/seed-data.json}'s {@code brokerage} block along with the 24-hour free
 * cancellation window and the 50% late fee that {@code jdl/payout.jdl} annotates beside the same
 * fields. Nothing here is invented in a code comment.
 *
 * <h2>Every value is a String, and blank means "the founding default"</h2>
 *
 * <p>Not typed fields with Spring placeholder defaults, and the reason is the compose files. A
 * variable this repository documents and no compose file carries is a variable that silently does
 * nothing (D46, D50), so all five are passed through in all three compose files; and compose's
 * {@code ${X:-}} sets an <em>empty</em> variable rather than leaving it unset, which Spring reads as a
 * value present. A {@code BigDecimal} field would then fail to bind on every estate that had not set
 * one. Blank counting as absent is the same convention the payment secrets already use (D45).
 *
 * <h2>Malformed refuses startup, even on an estate this would not have written to</h2>
 *
 * <p>{@link #founding()} is called from {@link BrokerageBootstrap}'s constructor, so an unparseable or
 * out-of-range value fails the context rather than being discovered later. That is deliberate even
 * though the values are unused once {@code brokerage_config} has a row: a variable that is silently
 * ignored today and prices every booking the day the table is next empty is this repository's most
 * frequently rediscovered defect shape. The refusal is loud, immediate, and cannot be reached by
 * omission.
 *
 * <p>What it does <strong>not</strong> catch is a plausible wrong number — {@code 0.15} where
 * {@code 0.12} was meant. Nothing can: it is a well-formed commercial term. What bounds that is that
 * it is written exactly once, into an empty table, on the day an estate is created and while somebody
 * is watching a deploy, and that {@link BrokerageBootstrap} logs the row it wrote at INFO. It cannot
 * silently re-price an estate that already has terms, because a non-empty table is never written to.
 */
@Component
@ConfigurationProperties(prefix = "healthconnect.brokerage.founding")
public class FoundingTerms {

    /** The prototype's {@code const COMMISSION = 0.12}, and the rate every seeded ledger row carries. */
    public static final String DEFAULT_COMMISSION_RATE = "0.12";

    /** The prototype's {@code const PAYOUT_LAG = 3}, in days. */
    public static final String DEFAULT_PAYOUT_LAG_DAYS = "3";

    /** "Cancel free up to 24 hours before", from the prototype's own how-it-works panel. */
    public static final String DEFAULT_FREE_CANCELLATION_HOURS = "24";

    /** Half the price, as {@code jdl/payout.jdl} annotates the field and the seed file carries it. */
    public static final String DEFAULT_LATE_CANCELLATION_PCT = "0.50";

    /** Ghana cedis. Money is minor units plus an explicit ISO currency, never a bare number. */
    public static final String DEFAULT_CURRENCY = "GHS";

    /**
     * When the founding terms took effect: <strong>the beginning of time, and not a property.</strong>
     *
     * <p>D53 prices a ledger row against the config in force at the booking's own instant and D56
     * prices the receipt at the same moment, so a founding row dated <em>later</em> than a completion
     * this estate is asked to price silently reintroduces "no config in force" — the whole of NEW-18,
     * through the fix for it. The requirement is therefore "predates every booking this estate will
     * ever price", and {@link Instant#EPOCH} is the only value that satisfies it by construction
     * rather than by a claim about history.
     *
     * <p>Every dated alternative encodes a belief. {@code 2020-01-01T00:00:00Z} — which is what
     * {@code PayoutSeeder} used, and it was right there, for seeded sessions whose dates it also
     * controlled — asserts that nothing older will ever be priced here, which is a statement about
     * estates that do not exist yet and about data that might be migrated in. The bootstrap's own
     * instant is worse still: it reads a clock on the pricing path, which is the shape D53 removed
     * from the consumer and D56 from the receipt, and it makes a completion that predates the deploy
     * unpriceable.
     *
     * <p>It reads as a sentinel because it <em>is</em> one — "from the beginning" — and that is
     * preferable to a date somebody will later try to interpret as the day the terms changed. It is
     * not a deployment input for the same reason: it is the one field where a well-formed wrong value
     * brings the defect back, and no estate has a use for a different one.
     */
    public static final Instant EFFECTIVE_FROM = Instant.EPOCH;

    /** Inclusive bounds on both rates, matching {@code min(0) max(1)} in {@code jdl/payout.jdl}. */
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private String commissionRate = "";
    private String payoutLagDays = "";
    private String freeCancellationHours = "";
    private String lateCancellationPct = "";
    private String currency = "";

    /**
     * The founding row, parsed and validated. A fresh object every call — {@link BrokerageConfig} is an
     * entity, and handing the same instance to two savers would be handing out a managed row.
     *
     * @throws IllegalStateException if any value is set and unusable, naming the property and what it
     *                               was given.
     */
    public BrokerageConfig founding() {
        return new BrokerageConfig()
            .commissionRate(rate("commission-rate", commissionRate, DEFAULT_COMMISSION_RATE))
            .payoutLagDays(count("payout-lag-days", payoutLagDays, DEFAULT_PAYOUT_LAG_DAYS))
            .freeCancellationHours(count("free-cancellation-hours", freeCancellationHours, DEFAULT_FREE_CANCELLATION_HOURS))
            .lateCancellationPct(rate("late-cancellation-pct", lateCancellationPct, DEFAULT_LATE_CANCELLATION_PCT))
            .currency(currency("currency", currency, DEFAULT_CURRENCY))
            .effectiveFrom(EFFECTIVE_FROM);
    }

    /** A proportion in [0, 1]. Refuses 12 for "12%", which is the mistake this bound is here for. */
    private static BigDecimal rate(String property, String given, String fallback) {
        String value = orDefault(given, fallback);
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException notANumber) {
            throw refusal(property, value, "it is not a decimal number");
        }
        if (parsed.compareTo(ZERO) < 0 || parsed.compareTo(ONE) > 0) {
            throw refusal(property, value, "a rate is a proportion between 0 and 1 — 12% is 0.12, not 12");
        }
        return parsed;
    }

    /** A non-negative whole number of days or hours. */
    private static int count(String property, String given, String fallback) {
        String value = orDefault(given, fallback);
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            throw refusal(property, value, "it is not a whole number");
        }
        if (parsed < 0) {
            throw refusal(property, value, "it cannot be negative");
        }
        return parsed;
    }

    /** Three upper-case letters. {@code currency} is {@code maxlength(3)} in the JDL. */
    private static String currency(String property, String given, String fallback) {
        String value = orDefault(given, fallback).trim();
        if (!value.matches("[A-Z]{3}")) {
            throw refusal(property, value, "it is not a three-letter upper-case ISO currency code");
        }
        return value;
    }

    private static String orDefault(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given.trim();
    }

    private static IllegalStateException refusal(String property, String value, String why) {
        return new IllegalStateException(
            ("healthconnect.brokerage.founding.%s is '%s' and %s. These are the terms this estate would " +
                "be founded on and they price every booking, so payout refuses to start on one it cannot " +
                "read — leave it unset for the founding default (decisions.md D57)").formatted(property, value, why)
        );
    }

    public String getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(String commissionRate) {
        this.commissionRate = commissionRate;
    }

    public String getPayoutLagDays() {
        return payoutLagDays;
    }

    public void setPayoutLagDays(String payoutLagDays) {
        this.payoutLagDays = payoutLagDays;
    }

    public String getFreeCancellationHours() {
        return freeCancellationHours;
    }

    public void setFreeCancellationHours(String freeCancellationHours) {
        this.freeCancellationHours = freeCancellationHours;
    }

    public String getLateCancellationPct() {
        return lateCancellationPct;
    }

    public void setLateCancellationPct(String lateCancellationPct) {
        this.lateCancellationPct = lateCancellationPct;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
