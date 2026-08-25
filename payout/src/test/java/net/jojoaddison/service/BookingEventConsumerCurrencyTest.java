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
 * The currency agreement decisions.md D22 asks for, at the one join where it can actually break.
 *
 * <p>{@code Ledger.currency} used to be {@code p.path("currency").asText("GHS")}. Two failure modes,
 * neither of which said anything at the time:
 *
 * <ul>
 *   <li>an event carrying no currency minted a GHS row whatever the booking was denominated in;
 *   <li>nothing compared the row against the {@link BrokerageConfig} whose {@code commissionRate}
 *       had just been applied to it.
 * </ul>
 *
 * <p>Both produce a ledger that looks completely ordinary — right shape, plausible numbers, wrong
 * denomination — and the earnings screen would have added it up without complaint. Only GHS is ever
 * used today, which is exactly the argument for asserting it: the check costs nothing while it can
 * never fire, and the day it can, the alternative is silent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingEventConsumerCurrencyTest {

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

    private static String completedEvent(String currencyJson) {
        return """
        {
          "eventId": "e-1",
          "type": "healthconnect.booking.completed",
          "payload": {
            "bookingRef": "b-1",
            "professionalRef": "p1",
            "professionalLogin": "akosua.mensah",
            "priceMinor": 28000,
            %s
            "deliveryMode": "ONLINE",
            "serviceRef": "s1a",
            "serviceName": "Nutrition assessment"
          }
        }
        """.formatted(currencyJson);
    }

    @Test
    @DisplayName("a booking in the config's currency is written, and the row carries it")
    void matchingCurrencyIsWritten() {
        consumer.onBookingEvent(completedEvent("\"currency\": \"GHS\","));

        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger).save(saved.capture());
        assertThat(saved.getValue().getCurrency()).isEqualTo("GHS");
        // 12% of 28000, to confirm the guard did not disturb the arithmetic it sits next to.
        assertThat(saved.getValue().getGrossMinor()).isEqualTo(28000L);
        assertThat(saved.getValue().getCommissionMinor()).isEqualTo(3360L);
        assertThat(saved.getValue().getNetMinor()).isEqualTo(24640L);
    }

    @Test
    @DisplayName("an event with no currency is refused rather than defaulted to GHS")
    void missingCurrencyIsRefused() {
        assertThatThrownBy(() -> consumer.onBookingEvent(completedEvent("")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("carries no currency");

        verify(ledger, never()).save(any());
        // Never marked processed, so the container retries rather than acknowledging an event that
        // produced nothing. A late correct row beats a prompt wrong one.
        verify(processed, never()).save(any());
    }

    @Test
    @DisplayName("a booking in a currency the brokerage config does not price is refused")
    void mismatchedCurrencyIsRefused() {
        assertThatThrownBy(() -> consumer.onBookingEvent(completedEvent("\"currency\": \"USD\",")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not apply");

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
                "currency": "USD",
                "deliveryMode": "ONLINE"
              }
            }
            """;
        assertThatThrownBy(() -> consumer.onBookingEvent(cancelled)).isInstanceOf(IllegalStateException.class);
        verify(ledger, never()).save(any());
    }
}
