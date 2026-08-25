package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import net.jojoaddison.domain.Ledger;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.LedgerRepository;
import net.jojoaddison.repository.ProcessedEventRepository;
import net.jojoaddison.repository.ReversalRepository;
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
 * Reversing an earning when a dispute is upheld — decisions.md D23.
 *
 * <p>The invariant every test here defends: <strong>the original row is never touched.</strong> A
 * reversal is a new row with negative amounts, which is what keeps the ledger append-only and every
 * earnings figure a plain aggregate over rows that are all still present.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeEventConsumerTest {

    @Mock
    private LedgerRepository ledger;

    @Mock
    private ReversalRepository ledgerQueries;

    @Mock
    private ProcessedEventRepository processed;

    private DisputeEventConsumer consumer;

    /** 28000 gross, 12% commission = 3360, net 24640 — the seed's s1a. */
    private static Ledger original() {
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

    private static String event(String refundJson) {
        return """
        {
          "eventId": "e-1",
          "type": "healthconnect.dispute.resolved",
          "payload": {
            "disputeRef": "d-abc",
            "bookingRef": "b-1",
            "professionalRef": "p1",
            "status": "RESOLVED",
            %s
            "resolution": "session not delivered"
          }
        }
        """.formatted(refundJson);
    }

    @BeforeEach
    void setUp() {
        consumer = new DisputeEventConsumer(ledger, ledgerQueries, processed, new ObjectMapper());
        when(processed.existsById(anyString())).thenReturn(false);
        when(ledgerQueries.findByBookingReference("d-abc")).thenReturn(Optional.empty());
        when(ledgerQueries.findByBookingReference("b-1")).thenReturn(Optional.of(original()));
    }

    private Ledger captureSaved() {
        ArgumentCaptor<Ledger> saved = ArgumentCaptor.forClass(Ledger.class);
        verify(ledger).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("a full reversal mirrors the original with every amount negated")
    void fullReversalNegatesEverything() {
        consumer.onDisputeResolved(event("\"refundMinor\": null,"));

        Ledger reversal = captureSaved();
        assertThat(reversal.getGrossMinor()).isEqualTo(-28000L);
        assertThat(reversal.getCommissionMinor()).isEqualTo(-3360L);
        assertThat(reversal.getNetMinor()).isEqualTo(-24640L);
        // Mirrored from the row being reversed, so the two cannot disagree.
        assertThat(reversal.getProfessionalLogin()).isEqualTo("akosua.mensah");
        assertThat(reversal.getCurrency()).isEqualTo("GHS");
        assertThat(reversal.getDeliveryMode()).isEqualTo(DeliveryMode.ONLINE);
    }

    @Test
    @DisplayName("the reversal is keyed by the DISPUTE reference, naming the booking in reversalOf")
    void reversalCannotCollideWithTheOriginal() {
        consumer.onDisputeResolved(event("\"refundMinor\": null,"));

        Ledger reversal = captureSaved();
        // Ledger.bookingReference is unique, and that uniqueness is the guard against a replayed
        // booking.completed double-crediting. Reusing "b-1" here would collide with the very row
        // being reversed.
        assertThat(reversal.getBookingReference()).isEqualTo("d-abc");
        assertThat(reversal.getReversalOf()).isEqualTo("b-1");
    }

    @Test
    @DisplayName("a partial refund takes back commission in the same proportion")
    void partialRefundIsProportional() {
        consumer.onDisputeResolved(event("\"refundMinor\": 14000,"));

        Ledger reversal = captureSaved();
        assertThat(reversal.getGrossMinor()).isEqualTo(-14000L);
        // Half the session refunded, so half the commission comes back: 3360 / 2.
        assertThat(reversal.getCommissionMinor()).isEqualTo(-1680L);
        assertThat(reversal.getNetMinor()).isEqualTo(-12320L);
    }

    @Test
    @DisplayName("a refund larger than the earning is capped, not applied")
    void refundIsCappedAtTheEarning() {
        consumer.onDisputeResolved(event("\"refundMinor\": 999999,"));

        // Reversing more than was ever credited would leave the professional owing money on a
        // session they were legitimately paid for.
        assertThat(captureSaved().getGrossMinor()).isEqualTo(-28000L);
    }

    @Test
    @DisplayName("a redelivered event does not reverse twice")
    void isIdempotentOnReplay() {
        when(ledgerQueries.findByBookingReference("d-abc")).thenReturn(Optional.of(new Ledger().bookingReference("d-abc")));

        consumer.onDisputeResolved(event("\"refundMinor\": null,"));

        verify(ledger, never()).save(any());
    }

    @Test
    @DisplayName("a dispute on a booking that never earned anything is a no-op, not a failure")
    void nothingToReverseIsNotAnError() {
        when(ledgerQueries.findByBookingReference("b-1")).thenReturn(Optional.empty());

        consumer.onDisputeResolved(event("\"refundMinor\": null,"));

        // A no-show can be upheld and never produced an earning. Throwing would retry forever
        // against a row that will never appear.
        verify(ledger, never()).save(any());
        verify(processed).save(any());
    }

    @Test
    @DisplayName("a negative refundMinor is refused — the sign belongs to this service, not the caller")
    void negativeRefundIsRefused() {
        assertThatThrownBy(() -> consumer.onDisputeResolved(event("\"refundMinor\": -500,")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not a signed adjustment");
        verify(ledger, never()).save(any());
    }

    @Test
    @DisplayName("an event missing its references is refused rather than half-applied")
    void missingReferencesAreRefused() {
        String noBooking =
            """
            { "eventId": "e-9", "type": "healthconnect.dispute.resolved",
              "payload": { "disputeRef": "d-zzz" } }
            """;
        assertThatThrownBy(() -> consumer.onDisputeResolved(noBooking)).isInstanceOf(IllegalStateException.class);
        verify(ledger, never()).save(any());
        verify(processed, never()).save(any());
    }
}
