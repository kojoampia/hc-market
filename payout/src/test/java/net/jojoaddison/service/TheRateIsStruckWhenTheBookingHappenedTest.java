package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.repository.ProcessedEventRepository;
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
 * A booking prices against the rate in force when the <em>booking</em> happened, never when the
 * event about it happened to be consumed — {@code decisions.md} D53, backlog NEW-13.
 *
 * <h2>What these tests are standing on</h2>
 *
 * <p>{@code configInForce()} took {@code Instant.now()}. That is the same rate while delivery is
 * prompt and a different one after any outage, replayed partition or paused consumer that straddles a
 * rate change — and nothing in the estate could detect the difference afterwards, because a ledger
 * row records the amounts computed from a rate and never the rate itself. Verified against both
 * databases that hold rows rather than assumed (D53).
 *
 * <p><strong>Every instant here is in 2020 and 2021.</strong> Not decoration: a test whose asserted
 * moment could equal a real clock's is a test that passes against the defect on some days, which is
 * the trap D51 recorded when a hard-coded date matched today's real date in some zone. No wall clock
 * will be in 2020 again, so `Instant.now()` can only ever select the LATEST config here — which is
 * exactly what makes each of these go red against the defect rather than intermittently.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TheRateIsStruckWhenTheBookingHappenedTest {

    /** Long before anything. The rate a 2020 booking was sold under. */
    private static final Instant OLD_RATE_FROM = Instant.parse("2019-01-01T00:00:00Z");

    /** The terms changed here, between the booking and this consumer ever seeing the event. */
    private static final Instant NEW_RATE_FROM = Instant.parse("2021-01-01T00:00:00Z");

    /** When the session was completed — under the old terms, and a year before the change. */
    private static final Instant COMPLETED_AT = Instant.parse("2020-06-01T09:30:00Z");

    /** When booking recorded the event. Same transaction as the completion, microseconds later. */
    private static final Instant OCCURRED_AT = Instant.parse("2020-06-01T09:30:00.004Z");

    @Mock
    private LedgerRepository ledger;

    @Mock
    private EarningsRepository ledgerQueries;

    @Mock
    private BrokerageConfigRepository brokerage;

    @Mock
    private ProcessedEventRepository processed;

    private BookingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingEventConsumer(ledger, ledgerQueries, brokerage, processed, new ObjectMapper(), new MarketCalendar());
        // Both versions are present, exactly as they would be after the brokerage changed its terms:
        // the old one is still there, because effective-dating is how a rate change is recorded.
        when(brokerage.findAll()).thenReturn(List.of(config("0.12", OLD_RATE_FROM), config("0.30", NEW_RATE_FROM)));
        when(ledgerQueries.existsByBookingReference(anyString())).thenReturn(false);
        when(processed.existsById(anyString())).thenReturn(false);
    }

    private static BrokerageConfig config(String rate, Instant effectiveFrom) {
        return new BrokerageConfig()
            .commissionRate(new BigDecimal(rate))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(effectiveFrom);
    }

    private static String completed(String eventId, String completionInstantJson, String occurredAtJson) {
        return """
        {
          "eventId": "%s",
          "type": "healthconnect.booking.completed",
          %s
          "payload": {
            "bookingRef": "b-1",
            %s
            "professionalRef": "p1",
            "professionalLogin": "akosua.mensah",
            "priceMinor": 28000,
            "currency": "GHS",
            "deliveryMode": "ONLINE",
            "serviceRef": "s1a",
            "serviceName": "Nutrition assessment"
          }
        }
        """.formatted(eventId, occurredAtJson, completionInstantJson);
    }

    private static String occurred() {
        return "\"occurredAt\": \"%s\",".formatted(OCCURRED_AT);
    }

    private static String completedAt() {
        return "\"bookingCompletedAt\": \"%s\",".formatted(COMPLETED_AT);
    }

    private Ledger savedRow() {
        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("an event consumed after a rate change still prices at the rate in force when the booking completed")
    void completionInstantDecidesTheRate() {
        consumer.onBookingEvent(completed("e-1", completedAt(), occurred()));

        // 12% of 28000, not 30%. Against the defect this is 8400 — the rate that took effect in 2021,
        // six months after the session, chosen because that is what Instant.now() sees today.
        assertThat(savedRow().getCommissionMinor()).isEqualTo(3360L);
        assertThat(savedRow().getNetMinor()).isEqualTo(24640L);
    }

    @Test
    @DisplayName("a late-cancellation fee prices at the rate in force when the booking was cancelled")
    void cancellationInstantDecidesTheFeesRate() {
        String cancelled =
            """
            {
              "eventId": "e-2",
              "type": "healthconnect.booking.cancelled",
              "occurredAt": "2020-06-01T09:30:00.004Z",
              "payload": {
                "bookingRef": "b-2",
                "bookingCancelledAt": "2020-06-01T09:30:00Z",
                "professionalRef": "p1",
                "professionalLogin": "akosua.mensah",
                "priceMinor": 28000,
                "currency": "GHS",
                "deliveryMode": "ONLINE",
                "lateCancellation": true
              }
            }
            """;
        consumer.onBookingEvent(cancelled);

        // Half of 28000 is the fee; 12% of that is the commission. Against the defect, 4200.
        assertThat(savedRow().getGrossMinor()).isEqualTo(14000L);
        assertThat(savedRow().getCommissionMinor()).isEqualTo(1680L);
    }

    @Test
    @DisplayName("an event carrying no completion instant prices at the envelope's occurredAt, not at now")
    void anOlderEventFallsBackToWhenItWasRecorded() {
        // The compatibility case, and the reason this is a package rather than an edit: every event
        // already in booking's outbox on the day of the change carries no bookingCompletedAt. The
        // envelope's occurredAt is stamped in the SAME transaction as the completion, so it answers
        // the question to within that transaction's duration and is immune to delivery lag.
        consumer.onBookingEvent(completed("e-3", "", occurred()));

        assertThat(savedRow().getCommissionMinor()).isEqualTo(3360L);
    }

    @Test
    @DisplayName("a null completion instant is the same case as an absent one — not the string \"null\"")
    void anExplicitJsonNullFallsBackToo() {
        // booking puts these fields on EVERY booking payload, so booking.requested carries
        // "bookingCompletedAt": null. Jackson's NullNode.asText(default) answers "null" rather than
        // the default, so a reader written the obvious way parses the four characters and throws.
        consumer.onBookingEvent(completed("e-4", "\"bookingCompletedAt\": null,", occurred()));

        assertThat(savedRow().getCommissionMinor()).isEqualTo(3360L);
    }

    @Test
    @DisplayName("an event with neither a completion instant nor an occurredAt is refused, never priced at now")
    void withNoDefensibleMomentTheEventIsRefused() {
        // Not reachable from any version of booking that has ever run — occurredAt is a not-null
        // column written unconditionally by OutboxRecorder — which is exactly when a refusal is free.
        // Falling through to Instant.now() here would put the whole defect back for the one class of
        // event most likely to be delayed.
        assertThatThrownBy(() -> consumer.onBookingEvent(completed("e-5", "", "")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no moment to price");

        verify(ledger, never()).save(any());
        // Never marked processed, so the container retries rather than acknowledging an event that
        // produced nothing — the trade currencyOf and deliveryModeOf already make.
        verify(processed, never()).save(any());
    }

    @Test
    @DisplayName("a completion instant that is not an instant is refused rather than quietly replaced")
    void aMalformedCompletionInstantIsRefused() {
        assertThatThrownBy(() -> consumer.onBookingEvent(completed("e-6", "\"bookingCompletedAt\": \"yesterday\",", occurred())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bookingCompletedAt");

        verify(ledger, never()).save(any());
        verify(processed, never()).save(any());
    }

    @Test
    @DisplayName("a replay after a restore prices identically, because the price is a function of the event alone")
    void replayPricesTheSame() {
        // payout's two idempotency guards — processed_event and ledger.booking_reference — mean an
        // ordinary redelivery writes nothing at all, so they hide this. The case they do not cover is
        // a restore: payout's database recovered to a point before the event, and the partition
        // replayed from an earlier offset. Both guards are then legitimately clear, and the second
        // consumption is a first consumption as far as this class can tell.
        //
        // The terms move BETWEEN the two consumptions, which is what makes this test say something
        // the one at the top of this file does not: against the defect the two rows disagree with
        // each other, not merely with the truth, and neither the ledger nor the receipt records
        // which of them was written.
        String event = completed("e-7", completedAt(), occurred());
        consumer.onBookingEvent(event);
        when(brokerage.findAll()).thenReturn(
            List.of(config("0.12", OLD_RATE_FROM), config("0.30", NEW_RATE_FROM), config("0.50", Instant.parse("2022-01-01T00:00:00Z")))
        );
        consumer.onBookingEvent(event);

        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getCommissionMinor()).isEqualTo(saved.getAllValues().get(1).getCommissionMinor());
        assertThat(saved.getAllValues().get(1).getCommissionMinor()).isEqualTo(3360L);
    }
}
