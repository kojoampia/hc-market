package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BrokerageConfigRepository;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.repository.ProcessedEventRepository;
import net.jojoaddison.repository.ReversalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@code ledger.earned_on} is written on the marketplace's day — NEW-10, {@code decisions.md} D51.
 *
 * <h2>What went wrong, and why nothing said so</h2>
 *
 * <p>Three lines wrote this column and all three read {@code LocalDate.now()}, which takes the JVM's
 * default zone. The seeders write the same column in {@code Africa/Accra} (D48), so after that
 * package one table was dated in two calendars — and the disagreement is invisible from inside:
 * lifetime gross does not move, no count changes, and the row looks entirely ordinary. It surfaces
 * only at a month boundary, on a tile, as a number that is slightly wrong.
 *
 * <h2>Why the clocks are not Accra's, and why they come in pairs</h2>
 *
 * <p>A fixed clock already holding Accra cannot tell an implementation that <em>names</em> the zone
 * from one that merely inherits it — on a machine with no {@code TZ} they agree, which is exactly how
 * this survived. So every test here forces a JVM default that is not Accra and stands at an hour
 * where the two calendars differ.
 *
 * <p>And each write is bracketed from <strong>both</strong> ends, in two tests rather than two
 * assertions in one. An eastward clock alone leaves the westward half unwatched (D48's review,
 * finding 8): at 02:30 UTC a zone ahead of Accra still says the same day and agrees by accident. The
 * split into separate tests is so the first failure cannot hide the second.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EarnedOnIsTheMarketplacesDayTest {

    /**
     * The three constants below are dated 2021 deliberately — see {@code MarketCalendarUnitTest}'s
     * class javadoc. A date the real clock could also be would let an implementation that ignores the
     * injected clock pass on whichever of the two directions happened to agree with the machine that
     * day; five years past, both are red on every day of the year.
     */
    /** 23:30 in Accra. Already tomorrow anywhere east of UTC — the window that was live here. */
    private static final Instant LATE_EVENING = Instant.parse("2021-09-05T23:30:00Z");

    /** 02:30 in Accra. Still yesterday far enough west. */
    private static final Instant EARLY_MORNING = Instant.parse("2021-09-05T02:30:00Z");

    /** The day both instants above fall on, in {@code Africa/Accra}, and the only right answer. */
    private static final LocalDate ACCRAS_DAY = LocalDate.of(2021, 9, 5);

    @Mock
    private LedgerRepository ledger;

    @Mock
    private EarningsRepository ledgerQueries;

    @Mock
    private ReversalRepository reversalQueries;

    @Mock
    private BrokerageConfigRepository brokerage;

    @Mock
    private ProcessedEventRepository processed;

    // ------------------------------------------------------- a completed booking --

    @Test
    @DisplayName("a booking completed late in Accra's evening is earned on that day, not the next")
    void completedBookingLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> {
            bookingConsumer(LATE_EVENING, "Europe/Berlin").onBookingEvent(completedEvent());
            assertThat(savedLedgerRow().getEarnedOn()).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("a booking completed early in Accra's morning is earned on that day, not the previous one")
    void completedBookingEarlyMorning() {
        underDefaultZone("America/New_York", () -> {
            bookingConsumer(EARLY_MORNING, "America/New_York").onBookingEvent(completedEvent());
            assertThat(savedLedgerRow().getEarnedOn()).isEqualTo(ACCRAS_DAY);
        });
    }

    // ------------------------------------------------- a late-cancellation fee --

    @Test
    @DisplayName("a late-cancellation fee raised late in Accra's evening is earned on that day")
    void lateFeeLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> {
            bookingConsumer(LATE_EVENING, "Europe/Berlin").onBookingEvent(lateCancellationEvent());
            assertThat(savedLedgerRow().getEarnedOn()).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("a late-cancellation fee raised early in Accra's morning is earned on that day")
    void lateFeeEarlyMorning() {
        underDefaultZone("America/New_York", () -> {
            bookingConsumer(EARLY_MORNING, "America/New_York").onBookingEvent(lateCancellationEvent());
            assertThat(savedLedgerRow().getEarnedOn()).isEqualTo(ACCRAS_DAY);
        });
    }

    // ------------------------------------------------------- a dispute reversal --

    /**
     * The sharpest of the three. {@code DisputeEventConsumer}'s comment reasons carefully that a
     * reversal is dated today rather than backdated, because backdating "would silently rewrite a
     * month that has already been reported" — and never said whose today. Read in a zone ahead of
     * Accra on the last day of a month, the reversal lands in the next month, which rewrites a
     * reported month by the other door.
     */
    @Test
    @DisplayName("a reversal recorded late in Accra's evening is dated that day, not the next")
    void reversalLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> {
            disputeConsumer(LATE_EVENING, "Europe/Berlin").onDisputeResolved(disputeEvent());
            assertThat(savedLedgerRow().getEarnedOn()).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("a reversal recorded early in Accra's morning is dated that day, not the previous one")
    void reversalEarlyMorning() {
        underDefaultZone("America/New_York", () -> {
            disputeConsumer(EARLY_MORNING, "America/New_York").onDisputeResolved(disputeEvent());
            assertThat(savedLedgerRow().getEarnedOn()).isEqualTo(ACCRAS_DAY);
        });
    }

    // -------------------------------------------------------------- the fixtures --

    private BookingEventConsumer bookingConsumer(Instant instant, String clockZone) {
        when(brokerage.findAll()).thenReturn(List.of(ghsConfig()));
        when(ledgerQueries.existsByBookingReference(anyString())).thenReturn(false);
        when(processed.existsById(anyString())).thenReturn(false);
        return new BookingEventConsumer(
            ledger,
            ledgerQueries,
            new BrokerageTerms(brokerage),
            processed,
            new ObjectMapper(),
            calendarAt(instant, clockZone)
        );
    }

    private DisputeEventConsumer disputeConsumer(Instant instant, String clockZone) {
        when(processed.existsById(anyString())).thenReturn(false);
        when(reversalQueries.findByBookingReference("d-abc")).thenReturn(Optional.empty());
        when(reversalQueries.findByBookingReference("b-1")).thenReturn(Optional.of(originalEarning()));
        return new DisputeEventConsumer(ledger, reversalQueries, processed, new ObjectMapper(), calendarAt(instant, clockZone));
    }

    /**
     * The clock's own zone is deliberately not Accra, and which side of Accra it sits on matches the
     * test using it: an implementation reading the <em>clock's</em> zone rather than the calendar's
     * is only caught by a clock on the failing side.
     */
    private static MarketCalendar calendarAt(Instant instant, String clockZone) {
        return MarketCalendar.at(Clock.fixed(instant, ZoneId.of(clockZone)));
    }

    private Ledger savedLedgerRow() {
        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger).save(saved.capture());
        return saved.getValue();
    }

    private static BrokerageConfig ghsConfig() {
        return new BrokerageConfig()
            .commissionRate(new BigDecimal("0.12"))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            // Before every instant this class uses, which it was not until D53. The fixture's clocks
            // are in 2021 and this said 2026, so the config in force was only ever found because
            // configInForce read the real clock — the defect NEW-13 names, propping up a fixture about
            // something else. The events carry their own act instants now, so the config has to
            // predate them or nothing here can be priced at all.
            .effectiveFrom(Instant.parse("2019-01-01T00:00:00Z"));
    }

    private static Ledger originalEarning() {
        return new Ledger()
            .bookingReference("b-1")
            .professionalRef("p1")
            .professionalLogin("akosua.mensah")
            .grossMinor(28000L)
            .commissionMinor(3360L)
            .netMinor(24640L)
            .currency("GHS")
            .deliveryMode(DeliveryMode.ONLINE)
            .serviceRef("s1a")
            .serviceName("Nutrition assessment")
            .earnedOn(LocalDate.of(2026, 8, 10));
    }

    /**
     * The act instants below decide the <em>rate</em> (D53) and nothing about {@code earned_on}, which
     * is the day this consumer runs on the marketplace's calendar — the two are different questions
     * and D51 answered the second one deliberately. They are on the same Accra day as this class's
     * clocks only so that neither reading changes what these tests assert.
     */
    private static String completedEvent() {
        return """
        {
          "eventId": "e-1",
          "type": "healthconnect.booking.completed",
          "occurredAt": "2021-09-05T12:00:00.004Z",
          "payload": {
            "bookingRef": "b-1",
            "bookingCompletedAt": "2021-09-05T12:00:00Z",
            "professionalRef": "p1",
            "professionalLogin": "akosua.mensah",
            "priceMinor": 28000,
            "currency": "GHS",
            "deliveryMode": "ONLINE",
            "serviceRef": "s1a",
            "serviceName": "Nutrition assessment"
          }
        }
        """;
    }

    private static String lateCancellationEvent() {
        return """
        {
          "eventId": "e-2",
          "type": "healthconnect.booking.cancelled",
          "occurredAt": "2021-09-05T12:00:00.004Z",
          "payload": {
            "bookingRef": "b-2",
            "bookingCancelledAt": "2021-09-05T12:00:00Z",
            "professionalRef": "p1",
            "professionalLogin": "akosua.mensah",
            "priceMinor": 28000,
            "lateCancellation": true,
            "currency": "GHS",
            "deliveryMode": "ONLINE",
            "serviceRef": "s1a",
            "serviceName": "Nutrition assessment"
          }
        }
        """;
    }

    private static String disputeEvent() {
        return """
        {
          "eventId": "e-3",
          "type": "healthconnect.dispute.resolved",
          "payload": {
            "disputeRef": "d-abc",
            "bookingRef": "b-1",
            "professionalRef": "p1",
            "status": "RESOLVED",
            "refundMinor": null,
            "resolution": "session not delivered"
          }
        }
        """;
    }

    private static void underDefaultZone(String zone, Runnable assertion) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            assertion.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
