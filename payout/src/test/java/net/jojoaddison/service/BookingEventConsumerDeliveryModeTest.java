package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.BrokerageConfig;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.enumeration.DeliveryMode;
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
 * The second silent default D22 recorded and left, closed by D29 — the sibling of
 * {@link BookingEventConsumerCurrencyTest} and deliberately shaped like it.
 *
 * <p>{@code Ledger.deliveryMode} used to be
 * {@code DeliveryMode.valueOf(p.path("deliveryMode").asText("ONLINE"))}. An event missing the field
 * wrote an {@code ONLINE} row regardless, so a home visit or an in-person session was booked into
 * the wrong bucket of the earnings-by-format breakdown on the professional's Overview.
 *
 * <p>What makes it hard to see is what it does <em>not</em> disturb: gross, commission and net are
 * all correct, so every total reconciles and only the split moves — against a figure nobody has an
 * independent copy of. The currency bug at least had the decency to be checkable against a
 * brokerage config.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingEventConsumerDeliveryModeTest {

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
        consumer = new BookingEventConsumer(ledger, ledgerQueries, brokerage, processed, new ObjectMapper());
        when(brokerage.findAll()).thenReturn(List.of(ghsConfig()));
        when(ledgerQueries.existsByBookingReference(anyString())).thenReturn(false);
        when(processed.existsById(anyString())).thenReturn(false);
    }

    private static BrokerageConfig ghsConfig() {
        return new BrokerageConfig()
            .commissionRate(new BigDecimal("0.12"))
            .payoutLagDays(3)
            .freeCancellationHours(24)
            .lateCancellationPct(new BigDecimal("0.50"))
            .currency("GHS")
            .effectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static String completedEvent(String deliveryModeJson) {
        return """
        {
          "eventId": "e-1",
          "type": "healthconnect.booking.completed",
          "payload": {
            "bookingRef": "b-1",
            "professionalRef": "p1",
            "professionalLogin": "akosua.mensah",
            "priceMinor": 28000,
            "currency": "GHS",
            %s
            "serviceRef": "s1a",
            "serviceName": "Nutrition assessment"
          }
        }
        """.formatted(deliveryModeJson);
    }

    /**
     * HOME_VISIT rather than ONLINE on purpose: with the old default in place this assertion is the
     * one that fails, and it fails by reporting the mode the bug invented.
     */
    @Test
    @DisplayName("the row carries the mode the event actually named")
    void theEventsModeIsWritten() {
        consumer.onBookingEvent(completedEvent("\"deliveryMode\": \"HOME_VISIT\","));

        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger).save(saved.capture());
        assertThat(saved.getValue().getDeliveryMode()).isEqualTo(DeliveryMode.HOME_VISIT);
        // The money is untouched by the guard sitting beside it.
        assertThat(saved.getValue().getGrossMinor()).isEqualTo(28000L);
        assertThat(saved.getValue().getCommissionMinor()).isEqualTo(3360L);
    }

    @Test
    @DisplayName("an event with no deliveryMode is refused rather than defaulted to ONLINE")
    void missingModeIsRefused() {
        assertThatThrownBy(() -> consumer.onBookingEvent(completedEvent("")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("carries no deliveryMode");

        verify(ledger, never()).save(any());
        // Never marked processed, so the container retries rather than acknowledging an event that
        // produced nothing.
        verify(processed, never()).save(any());
    }

    /**
     * A mode this service has never heard of is the realistic version of this failure: booking ships
     * a new delivery mode and payout is a release behind. {@code valueOf}'s own message — "No enum
     * constant ...DeliveryMode.HYBRID" — reads as a code fault, so it is replaced by one naming the
     * booking and the value.
     */
    @Test
    @DisplayName("an unknown deliveryMode is refused, and the message names it")
    void unknownModeIsRefused() {
        assertThatThrownBy(() -> consumer.onBookingEvent(completedEvent("\"deliveryMode\": \"HYBRID\",")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown deliveryMode 'HYBRID'");

        verify(ledger, never()).save(any());
        verify(processed, never()).save(any());
    }

    @Test
    @DisplayName("the late-cancellation fee path is guarded too, not just the completed path")
    void lateFeePathIsGuarded() {
        String cancelled =
            """
            {
              "eventId": "e-2",
              "type": "healthconnect.booking.cancelled",
              "payload": {
                "bookingRef": "b-2",
                "professionalRef": "p1",
                "professionalLogin": "akosua.mensah",
                "priceMinor": 28000,
                "lateCancellation": true,
                "currency": "GHS"
              }
            }
            """;
        assertThatThrownBy(() -> consumer.onBookingEvent(cancelled)).isInstanceOf(IllegalStateException.class);
        verify(ledger, never()).save(any());
    }
}
