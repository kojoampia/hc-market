package net.jojoaddison.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.Payout;
import net.jojoaddison.domain.enumeration.PayoutStatus;
import net.jojoaddison.repository.PayoutQueryRepository;
import net.jojoaddison.repository.SettlementLedgerRepository;
import net.jojoaddison.service.dto.PayoutDeskDtos.BatchView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A payout run: what this platform owes one professional for one period, and the record that it was
 * paid — {@code decisions.md} D95, backlog NEW-51.
 *
 * <h2>What was missing, and why it mattered</h2>
 *
 * <p>{@code Ledger} accumulated earnings from the day the estate was built and <strong>nothing in
 * the estate ever wrote a {@code Payout}</strong>. The generated {@code PayoutResource} was deleted
 * (D54) because CRUD over this table let any token disclose every professional's settlement history
 * and mark an unpaid batch {@code PAID}; that deletion was right and the replacement was never
 * built, so {@code GET /api/pro/payouts} answered {@code []} on every estate and always would.
 *
 * <h2>Not {@code PayoutService}</h2>
 *
 * <p>{@code jdl/payout.jdl} says {@code service Payout with serviceClass}, so JHipster generates
 * {@code PayoutService} and rewrites it wholesale on the next {@code jhipster jdl --force}. Anything
 * put there is discarded and the failure is a wall of "cannot find symbol" on methods that existed
 * minutes ago. This class is a new file with a name the generator does not use, which is the same
 * reason {@code BookingWorkflow} exists in booking and {@code BrokerageTerms} exists beside the
 * orphaned {@code BrokerageConfigService} here.
 *
 * <h2>Act 987, and why this is not blocked on it</h2>
 *
 * <p>Act 987 gates the platform <em>moving</em> money — pushing a transfer through a provider API —
 * which is why {@code PaymentProvider} has no settlement call and must not gain one. Recording a
 * transfer a human already made by bank needs no licence, and {@code Payout.settledOn} and
 * {@code Payout.bankReference} are the model's own answer to that. So this class computes and
 * records; it pays nobody.
 *
 * <h2>The three money rules</h2>
 *
 * <ul>
 *   <li><strong>{@code payout is null} is what makes a row payable, and nothing else is.</strong>
 *       See {@code SettlementLedgerRepository} — in particular why a query filtering on the shape of
 *       {@code bookingReference} silently drops every reversal.
 *   <li><strong>Reversals are inside the arithmetic, with their own sign.</strong> D23's compensating
 *       entries carry negative amounts, so summing the period's rows subtracts them without anything
 *       here knowing what a reversal is. Getting the sign wrong overpays a professional for a booking
 *       that was refunded, which is why {@link #sumOrRefuse} re-derives
 *       {@code gross - commission == net} over the batch and refuses rather than recording a total
 *       that does not add up.
 *   <li><strong>A batch is a record of a settlement, not a derived total.</strong> Storing
 *       {@code grossMinor}/{@code commissionMinor}/{@code netMinor} on {@code Payout} is a
 *       deliberate exception to "derived, never stored", of the same kind as {@code Ledger}'s own
 *       money columns: the figure has to keep saying what was paid even after a later reversal
 *       changes what the same period would sum to today. What is still forbidden is a total
 *       <em>across</em> batches — there is no {@code professional.total_paid} and there must never
 *       be one.
 * </ul>
 */
@Service
public class PayoutRun {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutRun.class);

    /** Nothing unsettled in the period. Not a batch for zero — see {@link #open}. */
    public static class NothingToSettle extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NothingToSettle(String message) {
            super(message);
        }
    }

    /** The period runs past the payout lag, so some of the rows it claims are not payable yet. */
    public static class NotPayableYet extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NotPayableYet(String message) {
            super(message);
        }
    }

    /** Two currencies in one period. {@code Payout.currency} is one column and cannot say both. */
    public static class MixedCurrencies extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        MixedCurrencies(String message) {
            super(message);
        }
    }

    /** The period's rows do not add up, so no figure computed from them can be reconciled. */
    public static class LedgerDoesNotAddUp extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        LedgerDoesNotAddUp(String message) {
            super(message);
        }
    }

    /**
     * No batch carries that reference.
     *
     * <p>An {@link IllegalArgumentException} because it is a fact about what was asked for, and its
     * own type because the desk needs 404 rather than 400 — a malformed settlement and a settlement
     * against a batch that does not exist are different mistakes and the second one is usually a
     * typed reference.
     */
    public static class NoSuchBatch extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        NoSuchBatch(String message) {
            super(message);
        }
    }

    /**
     * This batch has already been settled, or is in a state settlement has no answer for.
     *
     * <p>Three arms, and the enumeration is the contract: already {@code PAID}, {@code FAILED} (§7.2
     * of D95 — undecided, NEW-64), and a net of zero or below (D95 §6, also NEW-64).
     *
     * <p><strong>{@code IN_PROGRESS} is deliberately NOT one of them and falls through to
     * {@code PAID}</strong>, which is a decision rather than an omission — a review finding, because
     * an unstated fall-through in a money transition is indistinguishable from a forgotten case.
     * Nothing in the estate writes that status, so the path is unreachable today; if anything ever
     * does, it will mean "a transfer has been initiated and not yet confirmed", and the correct next
     * state for a confirmed transfer is exactly {@code PAID}. Refusing it would make a batch somebody
     * had begun paying impossible to finish.
     */
    public static class NotSettleable extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NotSettleable(String message) {
            super(message);
        }
    }

    /**
     * No {@code BrokerageConfig} is in force, so the payout lag is unknown.
     *
     * <p>Unreachable on any estate {@code BrokerageBootstrap} has started (D57), which is when a
     * closed door is free. It is a separate type because it is the one refusal here that is a
     * statement about this service rather than about what was asked for: the desk should retry, not
     * correct its request.
     */
    public static class NoTermsInForce extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NoTermsInForce(String message) {
            super(message);
        }
    }

    private final SettlementLedgerRepository ledger;
    private final PayoutQueryRepository payouts;
    private final BrokerageTerms terms;
    private final MarketCalendar calendar;

    public PayoutRun(SettlementLedgerRepository ledger, PayoutQueryRepository payouts, BrokerageTerms terms, MarketCalendar calendar) {
        this.ledger = ledger;
        this.payouts = payouts;
        this.terms = terms;
        this.calendar = calendar;
    }

    /**
     * Compute and record one batch: every unsettled ledger row for this professional earned in the
     * period, summed, attached, and written in {@link PayoutStatus#OPEN}.
     *
     * <p><strong>No unsettled rows is no batch</strong>, refused rather than written as a batch for
     * zero: a settlement row that settles nothing is a line on a professional's earnings screen
     * saying they were paid, and there is no endpoint to delete one.
     *
     * <p><strong>Rows that sum to zero — or below it — DO get a batch</strong>, and that is a
     * decision rather than an oversight. A period holding one reversal and no earnings sums to a
     * negative number, which is not something the desk can transfer; but refusing it leaves that
     * reversal {@code payout is null} for ever, so it would silently reduce some later, unrelated
     * period instead. A batch nobody can pay is visible and can be carried forward; a reversal that
     * quietly discounts next quarter is not. It is logged at WARN for exactly that reason, and named
     * in D95 as a question for ratification.
     *
     * @throws NothingToSettle    if the period holds no unsettled row
     * @throws NotPayableYet      if {@code periodEnd} is later than today minus the payout lag
     * @throws MixedCurrencies    if the period's rows are not all in one currency
     * @throws LedgerDoesNotAddUp if the summed rows fail {@code gross - commission == net}
     * @throws NoTermsInForce     if no {@code BrokerageConfig} is in force
     */
    @Transactional
    public BatchView open(String professionalRef, LocalDate periodStart, LocalDate periodEnd) {
        if (professionalRef == null || professionalRef.isBlank()) {
            throw new IllegalArgumentException("a payout run needs a professional reference");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("a payout run needs both a period start and a period end");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart " + periodStart + " is after periodEnd " + periodEnd);
        }

        LocalDate payableThrough = payableThrough();
        if (periodEnd.isAfter(payableThrough)) {
            // Refused rather than silently narrowed to `payableThrough`. A batch whose period claims
            // days whose rows were excluded is a record that disagrees with itself, and the
            // disagreement is invisible: the amounts are right for the rows that were taken and the
            // period says something else. The desk asks again for a period it may have.
            throw new NotPayableYet(
                "the payout lag makes earnings payable up to " +
                    payableThrough +
                    ", and this period runs to " +
                    periodEnd +
                    " — ask for a period ending on or before " +
                    payableThrough
            );
        }

        List<Ledger> rows = ledger.unsettledBetween(professionalRef, periodStart, periodEnd);
        if (rows.isEmpty()) {
            throw new NothingToSettle("nothing unsettled for " + professionalRef + " between " + periodStart + " and " + periodEnd);
        }

        String currency = oneCurrencyOrRefuse(rows, professionalRef, periodStart, periodEnd);
        Sums sums = sumOrRefuse(rows);

        Payout batch = payouts.save(
            new Payout()
                .reference(mintReference(professionalRef, periodEnd))
                .professionalRef(professionalRef)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .grossMinor(sums.gross())
                .commissionMinor(sums.commission())
                .netMinor(sums.net())
                .currency(currency)
                .status(PayoutStatus.OPEN)
        );

        // The attachment, which IS the double-payment guard: every one of these rows now has a
        // non-null `payout`, so no later run can select it. Deliberately not caught anywhere — a
        // unique-reference collision from two concurrent runs must roll this whole transaction back,
        // and catching a DataIntegrityViolationException inside @Transactional does not work here
        // anyway: the violation marks the transaction rollback-only and the response fails at commit
        // as an UnexpectedRollbackException, which is a 500 with no obvious cause.
        rows.forEach(row -> row.setPayout(batch));
        ledger.saveAll(rows);
        ledger.flush();

        long entries = ledger.countByPayoutId(batch.getId());
        if (sums.net() <= 0) {
            LOG.warn(
                "payout {} for {} sums to {} {} over {} entries — reversals exceed or cancel the period's earnings, " +
                    "so there is nothing to transfer; the batch exists so those rows are not carried into a later period",
                batch.getReference(),
                professionalRef,
                sums.net(),
                currency,
                entries
            );
        } else {
            LOG.info(
                "payout {} opened for {}: {} entries, gross {} commission {} net {} {}",
                batch.getReference(),
                professionalRef,
                entries,
                sums.gross(),
                sums.commission(),
                sums.net(),
                currency
            );
        }
        return view(batch, entries);
    }

    /**
     * Record that a human transferred this batch, against the reference the bank gave them.
     *
     * <p>{@link PayoutStatus#PAID} requires <strong>both</strong> a {@code settledOn} and a
     * {@code bankReference}, and a batch already {@code PAID} is refused rather than re-stamped: a
     * second settlement against the same batch is either a duplicate transfer or a lost record of the
     * first, and neither should be reachable by sending the request twice.
     *
     * <p><strong>The read takes a write lock, and without it this method is check-then-act.</strong>
     * Two settlements of one {@code OPEN} batch arriving together would both read {@code OPEN}, both
     * pass every refusal below and both answer 200 — and the last writer's {@code bankReference}
     * survives, so the first settlement's record is silently overwritten, which is exactly the loss
     * the paragraph above says must not be reachable. The mechanism is
     * {@code BookingQueryRepository.findByReferenceForUpdate}'s, one service along (D43), and the rule
     * is the same: what must not happen twice is the <em>transition</em>, not the request.
     *
     * <p>{@link PayoutStatus#FAILED} is refused too, and that is where this stops deliberately.
     * Nothing in the estate puts a batch in {@code FAILED} today, and what should happen to its rows
     * — return to unsettled, or stay attached — is a decision nobody has taken. A transition written
     * on a guess would decide it silently, in the direction that is either a double payment or a
     * professional who is never paid. D95 poses it; NEW-64 carries it.
     *
     * <p><strong>A batch whose net is zero or below is refused, and that is an interim door rather
     * than a settled answer</strong> (D95 §6, NEW-64). Such a batch exists so that its rows are not
     * carried into a later period, and D95's own text says there is nothing to transfer — so allowing
     * it to be marked {@code PAID} against a bank reference produces a record stating money was sent
     * for a debt, and the harm compounds: an operator clearing the {@code OPEN} list settles the
     * −15,000 batch with the next period's reference, the debt then reads as *paid to* the
     * professional, the following period settles in full, and 15,000 is overpaid with every record
     * internally consistent. Refusing is recoverable in a way settling is not — the door can be opened
     * when the carry-forward question is ratified, and nothing that ratification could want is lost by
     * closing it now.
     *
     * @throws NoSuchBatch   if no batch carries that reference
     * @throws NotSettleable if the batch is already {@code PAID}, is {@code FAILED}, or nets zero or
     *                       less
     */
    @Transactional
    public BatchView settle(String reference, LocalDate settledOn, String bankReference) {
        if (settledOn == null) {
            throw new IllegalArgumentException("a settlement needs the day the transfer was made");
        }
        if (bankReference == null || bankReference.isBlank()) {
            throw new IllegalArgumentException("a settlement needs the bank's own reference for the transfer");
        }
        LocalDate today = calendar.today();
        if (settledOn.isAfter(today)) {
            throw new IllegalArgumentException("settledOn " + settledOn + " has not happened yet — today is " + today);
        }

        // findByReferenceForUpdate, NOT findByReference: everything below is a check on a value this
        // method goes on to write, so the row has to be held for the whole transaction. The unlocked
        // finder exists for the desk's read and must not be used here.
        Payout batch = payouts.findByReferenceForUpdate(reference).orElseThrow(() -> new NoSuchBatch("no such payout batch: " + reference));

        if (batch.getStatus() == PayoutStatus.PAID) {
            throw new NotSettleable(
                "payout " +
                    batch.getReference() +
                    " was already settled on " +
                    batch.getSettledOn() +
                    " against " +
                    batch.getBankReference()
            );
        }
        if (batch.getStatus() == PayoutStatus.FAILED) {
            throw new NotSettleable(
                "payout " +
                    batch.getReference() +
                    " is FAILED, and what happens to a failed batch's ledger rows is undecided (decisions.md D95)"
            );
        }
        if (zeroIfNull(batch.getNetMinor()) <= 0) {
            throw new NotSettleable(
                "payout " +
                    batch.getReference() +
                    " nets " +
                    zeroIfNull(batch.getNetMinor()) +
                    " " +
                    batch.getCurrency() +
                    ", so there was no transfer to record — marking it PAID against a bank reference would " +
                    "state that money was sent for a period that owes nothing or owes it the other way. " +
                    "It holds its ledger rows, so nothing is carried into a later period; how such a batch " +
                    "is finally disposed of is backlog NEW-64 (decisions.md D95)"
            );
        }

        batch.setStatus(PayoutStatus.PAID);
        batch.setSettledOn(settledOn);
        batch.setBankReference(bankReference.trim());
        payouts.save(batch);

        long entries = ledger.countByPayoutId(batch.getId());
        LOG.info(
            "payout {} settled on {} against {} — {} entries, net {} {}",
            batch.getReference(),
            settledOn,
            batch.getBankReference(),
            entries,
            batch.getNetMinor(),
            batch.getCurrency()
        );
        return view(batch, entries);
    }

    /** One batch by reference, for the desk to read back. */
    @Transactional(readOnly = true)
    public Optional<BatchView> byReference(String reference) {
        return payouts.findByReference(reference).map(batch -> view(batch, ledger.countByPayoutId(batch.getId())));
    }

    /**
     * The last day whose earnings may be paid: today, in the marketplace's calendar, less the payout
     * lag in force.
     *
     * <p>{@link MarketCalendar}, never {@code LocalDate.now()} — {@code decisions.md} D51, and the
     * CI check that bans an implicit zone scans all five services. It has to be the marketplace's
     * calendar because it is a bound on {@code ledger.earned_on}, which is written in that calendar;
     * a cutoff read in the JVM's zone would include or exclude a day of earnings depending on where
     * the container was started.
     *
     * <p>The lag comes from the {@code BrokerageConfig} in force at the <em>start of today</em>, not
     * from {@code FoundingTerms} — those are the founding defaults and are only ever written into an
     * empty table (D57). The start of the marketplace day is the same convention D56 gives a caller
     * who supplies a day and no moment. Nothing on the <em>pricing</em> path reads a clock (D53) and
     * that is unchanged: this is not a price. "Is this earning old enough to pay" is a question about
     * today by construction, and the run is the only thing here that asks it.
     */
    private LocalDate payableThrough() {
        Instant startOfToday = calendar.today().atStartOfDay(MarketCalendar.MARKET_ZONE).toInstant();
        BrokerageConfig config = terms
            .inForceAt(startOfToday)
            .orElseThrow(() ->
                new NoTermsInForce(
                    "no brokerage configuration is in force, so the payout lag is unknown and no earning can be " +
                        "judged payable (decisions.md D57 — BrokerageBootstrap writes the founding row)"
                )
            );
        int lagDays = config.getPayoutLagDays() == null ? 0 : config.getPayoutLagDays();
        return calendar.today().minusDays(lagDays);
    }

    /** The batch's three money columns, computed once so nothing can sum the same column twice. */
    private record Sums(long gross, long commission, long net) {}

    /**
     * The period's totals, having first established that they add up.
     *
     * <p>Every ledger row satisfies {@code gross - commission == net}, a reversal included — its three
     * numbers are the original's negated. So the sums must satisfy it too, and if they do not then one
     * of the rows is wrong: a reversal written with a sign error is the case this exists for, and it
     * is the one that <em>overpays</em>. Refusing is the only honest answer, because these three
     * columns are what a reconciliation against a bank statement is done against, and a total that
     * does not add up cannot be reconciled with anything.
     *
     * <p>Summed as {@code long} in Java rather than in JPQL. Money here is minor units and a
     * {@code sum} over a {@code Long} column comes back boxed, with a one-row aggregate declared as
     * {@code Object[]} handing back the list wrapped in an array — the trap
     * {@code EarningsRepository.lifetime} documents. The rows are loaded anyway, to be attached.
     */
    private static Sums sumOrRefuse(List<Ledger> rows) {
        long gross = rows
            .stream()
            .mapToLong(r -> zeroIfNull(r.getGrossMinor()))
            .sum();
        long commission = rows
            .stream()
            .mapToLong(r -> zeroIfNull(r.getCommissionMinor()))
            .sum();
        long net = rows
            .stream()
            .mapToLong(r -> zeroIfNull(r.getNetMinor()))
            .sum();
        if (gross - commission != net) {
            throw new LedgerDoesNotAddUp(
                "these ledger rows sum to gross " +
                    gross +
                    " commission " +
                    commission +
                    " net " +
                    net +
                    ", and gross - commission must equal net on every row and therefore on the batch — " +
                    "one of the rows in this period is wrong and no settlement figure taken from them can be reconciled"
            );
        }
        return new Sums(gross, commission, net);
    }

    /** {@code Payout.currency} is one column, so a period spanning two currencies has no answer. */
    private static String oneCurrencyOrRefuse(List<Ledger> rows, String professionalRef, LocalDate from, LocalDate to) {
        Set<String> currencies = new LinkedHashSet<>();
        rows.forEach(r -> currencies.add(r.getCurrency()));
        if (currencies.size() != 1) {
            throw new MixedCurrencies(
                "the unsettled rows for " +
                    professionalRef +
                    " between " +
                    from +
                    " and " +
                    to +
                    " are in " +
                    currencies +
                    ", and a payout batch records one currency — settle them as separate periods"
            );
        }
        return currencies.iterator().next();
    }

    /**
     * {@code PAY-<yyyyMM>-<professionalRef>-<nn>}, from the period's own last month.
     *
     * <p>{@code jdl/payout.jdl} illustrates the reference as {@code PAY-202607-AM}, month plus
     * initials, and this deliberately differs on both halves of that:
     *
     * <ul>
     *   <li><strong>Not initials.</strong> This service holds no professional's name — {@code Ledger}
     *       carries a reference and a login and nothing else — so initials would need a round trip to
     *       catalog for a value that is <em>not unique</em>: two professionals sharing initials in one
     *       month would collide on a unique column, which makes the illustrated format unusable as a
     *       key rather than merely inconvenient. {@code professionalRef} is what the row is keyed by
     *       and what the desk already has in its hand.
     *   <li><strong>A sequence, so a run need not be monthly.</strong> Two batches in one month are a
     *       real thing — a corrected period, a professional paid off-cycle — and a format that cannot
     *       express them forces either a second run to fail or a reference to lie about its period.
     * </ul>
     *
     * <p>The month is {@code periodEnd}'s, so a period that straddles a month boundary is filed under
     * the month it ends in. Formatted from the year and month values rather than a
     * {@code DateTimeFormatter} pattern: {@code ofPattern} without a locale formats numbers in the
     * JVM's default locale, and a reference is an identifier rather than something rendered for a
     * reader.
     *
     * <p>The sequence comes from a count and a count is not a lock. Two concurrent runs for the same
     * professional and month both compute the same number and the loser collides on the unique index,
     * which is the direction to fail in — the guarantee is the schema's, and this only has to produce
     * a candidate that is usually right.
     */
    private String mintReference(String professionalRef, LocalDate periodEnd) {
        String prefix = "PAY-%04d%02d-%s-".formatted(periodEnd.getYear(), periodEnd.getMonthValue(), professionalRef);
        long already = payouts.countByProfessionalRefAndReferenceStartingWith(professionalRef, prefix);
        return prefix + "%02d".formatted(already + 1);
    }

    private BatchView view(Payout batch, long entries) {
        return new BatchView(
            batch.getReference(),
            batch.getProfessionalRef(),
            batch.getPeriodStart(),
            batch.getPeriodEnd(),
            zeroIfNull(batch.getGrossMinor()),
            zeroIfNull(batch.getCommissionMinor()),
            zeroIfNull(batch.getNetMinor()),
            batch.getCurrency(),
            batch.getStatus() == null ? null : batch.getStatus().name(),
            batch.getSettledOn(),
            batch.getBankReference(),
            entries
        );
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
