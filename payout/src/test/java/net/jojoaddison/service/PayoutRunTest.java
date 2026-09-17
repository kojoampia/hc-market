package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.Payout;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.domain.enumeration.PayoutStatus;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.repository.PayoutQueryRepository;
import net.jojoaddison.repository.SettlementLedgerRepository;
import net.jojoaddison.service.dto.PayoutDeskDtos.BatchView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The arithmetic and the refusals of a payout run — {@code decisions.md} D95, backlog NEW-51.
 *
 * <p>The invariant every test here defends: <strong>a professional is paid the sum of the period's
 * unsettled rows, reversals included, with their own sign.</strong> Getting the sign wrong overpays
 * somebody for a booking that was refunded, which is the failure the compensating-entry design (D23)
 * makes possible and which nothing before this class could have caught.
 *
 * <p>The double-payment guard itself — {@code payout is null} — is a property of the query and of the
 * attachment, so it is asserted against a real database in {@code PayoutDeskResourceIT}. What is
 * asserted here is that the run <em>attaches every row it summed</em>, which is the half a mocked
 * repository can see and the half that would otherwise leave those rows payable again.
 *
 * <p>{@link BrokerageTerms} is the real class over a mocked repository rather than a mock of its own.
 * The payout lag is the estate's own commercial term and the selector that finds it is D56's; mocking
 * the selector would assert that this class multiplies by whatever it is handed, which is not the
 * question.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayoutRunTest {

    /** ₵280.00 at 12% — the seed's s1a, so the arithmetic reads familiarly. */
    private static final long GROSS_A = 28_000L;
    private static final long COMMISSION_A = 3_360L;
    private static final long NET_A = 24_640L;

    /** ₵150.00 at 12%. */
    private static final long GROSS_B = 15_000L;
    private static final long COMMISSION_B = 1_800L;
    private static final long NET_B = 13_200L;

    private static final String PRO = "p1";

    /**
     * A clock fixed to a zone that is neither Accra nor UTC.
     *
     * <p>Deliberately: {@link MarketCalendar} reads the instant and names its own calendar, and a
     * clock already carrying the right zone cannot tell an implementation that names one from an
     * implementation that merely inherits one.
     */
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneId.of("America/New_York"));

    /** 2026-08-20 in Accra. */
    private static final LocalDate TODAY = LocalDate.parse("2026-08-20");

    /** The founding lag is 3 days (D57), so earnings are payable through the 17th. */
    private static final LocalDate PAYABLE_THROUGH = LocalDate.parse("2026-08-17");

    private static final LocalDate PERIOD_START = LocalDate.parse("2026-08-01");

    @Mock
    private SettlementLedgerRepository ledger;

    @Mock
    private PayoutQueryRepository payouts;

    @Mock
    private BrokerageConfigRepository configs;

    private PayoutRun run;

    @BeforeEach
    void wire() {
        when(configs.findAll()).thenReturn(List.of(founding(3)));
        when(payouts.countByProfessionalRefAndReferenceStartingWith(anyString(), anyString())).thenReturn(0L);
        // A saved batch comes back with an id, so the entry count has something to count against.
        when(payouts.save(any(Payout.class))).thenAnswer(invocation -> ((Payout) invocation.getArgument(0)).id(77L));
        when(ledger.countByPayoutId(77L)).thenReturn(0L);
        run = new PayoutRun(ledger, payouts, new BrokerageTerms(configs), MarketCalendar.at(FIXED));
    }

    private static BrokerageConfig founding(int lagDays) {
        return new BrokerageConfig()
            .id(1L)
            .commissionRate(new BigDecimal("0.120000"))
            .payoutLagDays(lagDays)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.500000"))
            .currency("GHS")
            .effectiveFrom(Instant.EPOCH);
    }

    private static Ledger earning(String bookingRef, long gross, long commission, long net, LocalDate on) {
        return new Ledger()
            .bookingReference(bookingRef)
            .professionalRef(PRO)
            .professionalLogin("akosua.mensah")
            .grossMinor(gross)
            .commissionMinor(commission)
            .netMinor(net)
            .currency("GHS")
            .deliveryMode(DeliveryMode.ONLINE)
            .earnedOn(on);
    }

    /**
     * A compensating entry, spelled the way {@code DisputeEventConsumer} spells one: the DISPUTE
     * reference in {@code bookingReference} (the booking's is unique and already taken by the
     * original) and the booking named in {@code reversalOf}. That is exactly why a batch query may not
     * filter on the shape of {@code bookingReference}.
     */
    private static Ledger reversal(String disputeRef, String ofBooking, long gross, long commission, long net, LocalDate on) {
        return earning(disputeRef, -gross, -commission, -net, on).reversalOf(ofBooking);
    }

    private void unsettled(Ledger... rows) {
        when(ledger.unsettledBetween(PRO, PERIOD_START, PAYABLE_THROUGH)).thenReturn(List.of(rows));
        when(ledger.countByPayoutId(77L)).thenReturn((long) rows.length);
    }

    private BatchView open() {
        return run.open(PRO, PERIOD_START, PAYABLE_THROUGH);
    }

    // ----------------------------------------------------------------- the arithmetic --

    @Test
    @DisplayName("a batch is the sum of the period's unsettled rows")
    void aBatchIsTheSumOfThePeriod() {
        unsettled(
            earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")),
            earning("b-2", GROSS_B, COMMISSION_B, NET_B, LocalDate.parse("2026-08-11"))
        );

        BatchView batch = open();

        assertThat(batch.grossMinor()).isEqualTo(GROSS_A + GROSS_B);
        assertThat(batch.commissionMinor()).isEqualTo(COMMISSION_A + COMMISSION_B);
        assertThat(batch.netMinor()).isEqualTo(NET_A + NET_B);
        assertThat(batch.currency()).isEqualTo("GHS");
        assertThat(batch.status()).isEqualTo(PayoutStatus.OPEN.name());
        assertThat(batch.settledOn()).isNull();
        assertThat(batch.bankReference()).isNull();
        assertThat(batch.entries()).isEqualTo(2);
    }

    /**
     * The one that matters, and the one nothing could have caught before.
     *
     * <p>Asserted as an exact figure rather than "less than it would otherwise be": a reversal added
     * with the wrong sign gives {@code NET_A + NET_B + NET_A}, and an inequality assertion against a
     * two-row baseline would pass for a reversal that was simply dropped. The equality pins both.
     */
    @Test
    @DisplayName("a reversal reduces the batch, and by exactly what it reversed")
    void aReversalReducesTheBatch() {
        unsettled(
            earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")),
            earning("b-2", GROSS_B, COMMISSION_B, NET_B, LocalDate.parse("2026-08-11")),
            reversal("d-1", "b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-14"))
        );

        BatchView batch = open();

        assertThat(batch.grossMinor()).as("b-1's gross is reversed, leaving b-2's").isEqualTo(GROSS_B);
        assertThat(batch.commissionMinor()).isEqualTo(COMMISSION_B);
        assertThat(batch.netMinor()).as("the professional is paid for b-2 and not for b-1").isEqualTo(NET_B);
        assertThat(batch.entries()).as("all three rows are settled, including the reversal").isEqualTo(3);
    }

    @Test
    @DisplayName("every row the run summed is attached to the batch it wrote — otherwise they are payable again")
    void everySummedRowIsAttached() {
        Ledger one = earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03"));
        Ledger two = reversal("d-1", "b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-14"));
        unsettled(one, two);

        open();

        ArgumentCaptor<Payout> written = ArgumentCaptor.forClass(Payout.class);
        verify(payouts).save(written.capture());
        assertThat(one.getPayout()).as("the earning was attached").isSameAs(written.getValue());
        assertThat(two.getPayout()).as("the reversal was attached").isSameAs(written.getValue());
        verify(ledger).saveAll(List.of(one, two));
        verify(ledger).flush();
    }

    /**
     * Zero and below still get a batch, and D95 poses whether that is right.
     *
     * <p>The alternative is refusing, which leaves the reversal {@code payout is null} for ever so
     * that it silently discounts a later, unrelated period. A batch nobody can pay is visible.
     */
    @Test
    @DisplayName("a period whose reversals cancel its earnings is still a batch, not a refusal")
    void aPeriodThatCancelsOutIsStillABatch() {
        unsettled(
            earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")),
            reversal("d-1", "b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-14"))
        );

        BatchView batch = open();

        assertThat(batch.netMinor()).isZero();
        assertThat(batch.grossMinor()).isZero();
        assertThat(batch.entries()).isEqualTo(2);
    }

    /**
     * A reversal written with the sign applied to two of its three columns.
     *
     * <p>This is the shape of the defect that overpays, and it is internally plausible: the gross and
     * the commission are negated and the net is not, so every row looks like a row and only the batch
     * disagrees with itself. A settlement figure taken from these rows could not be reconciled against
     * anything, so the run refuses rather than recording it.
     */
    @Test
    @DisplayName("rows that do not add up are refused rather than settled")
    void rowsThatDoNotAddUpAreRefused() {
        unsettled(
            earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")),
            earning("d-1", -GROSS_A, -COMMISSION_A, NET_A, LocalDate.parse("2026-08-14"))
        );

        assertThatThrownBy(this::open)
            .isInstanceOf(PayoutRun.LedgerDoesNotAddUp.class)
            .hasMessageContaining("gross - commission must equal net");
        verify(payouts, never()).save(any(Payout.class));
    }

    @Test
    @DisplayName("two currencies in one period have no single answer, so nothing is written")
    void twoCurrenciesAreRefused() {
        unsettled(
            earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")),
            earning("b-2", GROSS_B, COMMISSION_B, NET_B, LocalDate.parse("2026-08-11")).currency("USD")
        );

        assertThatThrownBy(this::open).isInstanceOf(PayoutRun.MixedCurrencies.class).hasMessageContaining("one currency");
        verify(payouts, never()).save(any(Payout.class));
    }

    // -------------------------------------------------------------------- the refusals --

    /**
     * A STRUCTURAL assertion, and it is labelled as one because it would be easy to over-read.
     *
     * <p>{@code payout is null} stops a later run claiming a settled row and does nothing at all about
     * a simultaneous one — two runs read the same rows, both compute the same money, both write a
     * batch, and one of them ends up holding no rows while still reporting the amount. Settle both and
     * the professional is paid twice. `SettlementLedgerRepository.unsettledBetween` therefore takes a
     * write lock, argued at length there.
     *
     * <p><strong>This test does not observe a race.</strong> It asserts the annotation is present and
     * that its mode is {@code PESSIMISTIC_WRITE}, so removing either goes red — which is more than
     * nothing and much less than a measurement. The property itself rests on PostgreSQL's row locking
     * under {@code READ COMMITTED}, and nothing in this repository establishes it.
     */
    @Test
    @DisplayName("the batch query takes a write lock — structural, and it does not prove a race is impossible")
    void theBatchQueryTakesAWriteLock() throws Exception {
        var method = SettlementLedgerRepository.class.getMethod("unsettledBetween", String.class, LocalDate.class, LocalDate.class);
        var lock = method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);

        assertThat(lock).as("without a lock, two simultaneous runs both claim the same rows and both report the money").isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("nothing unsettled is no batch, never a batch for zero")
    void nothingUnsettledIsNoBatch() {
        when(ledger.unsettledBetween(PRO, PERIOD_START, PAYABLE_THROUGH)).thenReturn(List.of());

        assertThatThrownBy(this::open).isInstanceOf(PayoutRun.NothingToSettle.class).hasMessageContaining(PRO);
        verify(payouts, never()).save(any(Payout.class));
    }

    /**
     * The payout lag bounds the period, and the refusal names the day the desk may ask for.
     *
     * <p>Refused rather than narrowed: a batch whose period claims days whose rows were excluded is a
     * record that disagrees with itself, invisibly — the amounts are right for the rows that were
     * taken and the period says something else.
     */
    @Test
    @DisplayName("a period running past the payout lag is refused, naming the day it may run to")
    void aPeriodPastTheLagIsRefused() {
        assertThatThrownBy(() -> run.open(PRO, PERIOD_START, PAYABLE_THROUGH.plusDays(1)))
            .isInstanceOf(PayoutRun.NotPayableYet.class)
            .hasMessageContaining(PAYABLE_THROUGH.toString());
        verify(ledger, never()).unsettledBetween(anyString(), any(), any());
    }

    @Test
    @DisplayName("today itself is past the lag — the boundary, in the direction that matters")
    void todayIsPastTheLag() {
        assertThatThrownBy(() -> run.open(PRO, PERIOD_START, TODAY)).isInstanceOf(PayoutRun.NotPayableYet.class);
    }

    /**
     * The lag is read from the config in force, not from a constant here.
     *
     * <p>With a zero lag the same request that was refused above is accepted, which is what
     * establishes that the number came from {@code BrokerageConfig} — a hard-coded 3 would refuse
     * both.
     */
    @Test
    @DisplayName("the lag comes from the brokerage config, so a zero lag makes today payable")
    void theLagComesFromTheConfig() {
        when(configs.findAll()).thenReturn(List.of(founding(0)));
        when(ledger.unsettledBetween(PRO, PERIOD_START, TODAY)).thenReturn(List.of(earning("b-1", GROSS_A, COMMISSION_A, NET_A, TODAY)));
        when(ledger.countByPayoutId(77L)).thenReturn(1L);

        assertThat(run.open(PRO, PERIOD_START, TODAY).netMinor()).isEqualTo(NET_A);
    }

    @Test
    @DisplayName("no brokerage config in force means the lag is unknown, which is a 503 and not a guess")
    void noTermsInForceIsRefused() {
        when(configs.findAll()).thenReturn(List.of());

        assertThatThrownBy(this::open).isInstanceOf(PayoutRun.NoTermsInForce.class).hasMessageContaining("payout lag");
    }

    @Test
    @DisplayName("a period that runs backwards is a malformed request")
    void aBackwardsPeriodIsRefused() {
        assertThatThrownBy(() -> run.open(PRO, PAYABLE_THROUGH, PERIOD_START))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is after");
    }

    // ------------------------------------------------------------------- the reference --

    @Test
    @DisplayName("the reference names the period's own month and the professional, and carries a sequence")
    void theReferenceNamesTheMonthAndTheProfessional() {
        unsettled(earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")));

        assertThat(open().reference()).isEqualTo("PAY-202608-p1-01");
    }

    @Test
    @DisplayName("a second batch in the same month is the next in sequence, so a run need not be monthly")
    void asecondBatchInAMonthIsTheNextInSequence() {
        unsettled(earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-08-03")));
        when(payouts.countByProfessionalRefAndReferenceStartingWith(PRO, "PAY-202608-p1-")).thenReturn(1L);

        assertThat(open().reference()).isEqualTo("PAY-202608-p1-02");
    }

    /** {@code periodEnd}'s month, so a period straddling a boundary is filed under the month it ends in. */
    @Test
    @DisplayName("a period that straddles a month is filed under the month it ends in")
    void aStraddlingPeriodIsFiledUnderItsEnd() {
        LocalDate start = LocalDate.parse("2026-07-20");
        when(ledger.unsettledBetween(PRO, start, PAYABLE_THROUGH)).thenReturn(
            List.of(earning("b-1", GROSS_A, COMMISSION_A, NET_A, LocalDate.parse("2026-07-25")))
        );
        when(ledger.countByPayoutId(77L)).thenReturn(1L);

        assertThat(run.open(PRO, start, PAYABLE_THROUGH).reference()).startsWith("PAY-202608-");
    }

    // ------------------------------------------------------------------ the settlement --

    private Payout openBatch() {
        return new Payout()
            .id(77L)
            .reference("PAY-202608-p1-01")
            .professionalRef(PRO)
            .periodStart(PERIOD_START)
            .periodEnd(PAYABLE_THROUGH)
            .grossMinor(GROSS_A)
            .commissionMinor(COMMISSION_A)
            .netMinor(NET_A)
            .currency("GHS")
            .status(PayoutStatus.OPEN);
    }

    @Test
    @DisplayName("a settlement records the day and the bank's own reference, and moves the batch to PAID")
    void aSettlementRecordsTheDayAndTheBankReference() {
        when(payouts.findByReference("PAY-202608-p1-01")).thenReturn(Optional.of(openBatch()));

        BatchView settled = run.settle("PAY-202608-p1-01", LocalDate.parse("2026-08-19"), "  GTB-99887766  ");

        assertThat(settled.status()).isEqualTo(PayoutStatus.PAID.name());
        assertThat(settled.settledOn()).isEqualTo(LocalDate.parse("2026-08-19"));
        assertThat(settled.bankReference())
            .as("trimmed — a reference pasted with whitespace is the same reference")
            .isEqualTo("GTB-99887766");
    }

    @Test
    @DisplayName("PAID needs a bank reference — a settlement nobody can check is not a settlement")
    void paidNeedsABankReference() {
        when(payouts.findByReference("PAY-202608-p1-01")).thenReturn(Optional.of(openBatch()));

        assertThatThrownBy(() -> run.settle("PAY-202608-p1-01", LocalDate.parse("2026-08-19"), "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bank's own reference");
        verify(payouts, never()).save(any(Payout.class));
    }

    @Test
    @DisplayName("PAID needs the day the transfer was made")
    void paidNeedsTheDay() {
        when(payouts.findByReference("PAY-202608-p1-01")).thenReturn(Optional.of(openBatch()));

        assertThatThrownBy(() -> run.settle("PAY-202608-p1-01", null, "GTB-99887766"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("day the transfer was made");
        verify(payouts, never()).save(any(Payout.class));
    }

    @Test
    @DisplayName("a settlement cannot be dated in the future")
    void aSettlementCannotBeDatedInTheFuture() {
        when(payouts.findByReference("PAY-202608-p1-01")).thenReturn(Optional.of(openBatch()));

        assertThatThrownBy(() -> run.settle("PAY-202608-p1-01", TODAY.plusDays(1), "GTB-99887766"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("has not happened yet");
    }

    @Test
    @DisplayName("a batch already settled is refused rather than re-stamped")
    void aBatchAlreadySettledIsRefused() {
        Payout paid = openBatch().status(PayoutStatus.PAID).settledOn(LocalDate.parse("2026-08-18")).bankReference("GTB-11112222");
        when(payouts.findByReference("PAY-202608-p1-01")).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> run.settle("PAY-202608-p1-01", LocalDate.parse("2026-08-19"), "GTB-99887766"))
            .isInstanceOf(PayoutRun.NotSettleable.class)
            .hasMessageContaining("2026-08-18")
            .hasMessageContaining("GTB-11112222");
        verify(payouts, never()).save(any(Payout.class));
    }

    /**
     * {@code FAILED} is refused, and that is where this stops deliberately — see D95. Nothing puts a
     * batch there today, and what happens to its rows is undecided.
     */
    @Test
    @DisplayName("a FAILED batch is refused, because what happens to its rows is undecided")
    void aFailedBatchIsRefused() {
        when(payouts.findByReference("PAY-202608-p1-01")).thenReturn(Optional.of(openBatch().status(PayoutStatus.FAILED)));

        assertThatThrownBy(() -> run.settle("PAY-202608-p1-01", LocalDate.parse("2026-08-19"), "GTB-99887766"))
            .isInstanceOf(PayoutRun.NotSettleable.class)
            .hasMessageContaining("undecided");
    }

    @Test
    @DisplayName("a reference no batch carries is a 404's worth of refusal, not a malformed request")
    void anUnknownReferenceIsItsOwnRefusal() {
        when(payouts.findByReference("PAY-NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> run.settle("PAY-NOPE", LocalDate.parse("2026-08-19"), "GTB-99887766")).isInstanceOf(
            PayoutRun.NoSuchBatch.class
        );
    }
}
