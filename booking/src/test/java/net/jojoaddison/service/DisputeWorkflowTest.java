package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DisputeStatus;
import net.jojoaddison.repository.DisputeQueryRepository;
import net.jojoaddison.repository.DisputeStatusChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The dispute lifecycle — decisions.md D23.
 *
 * <p>Two properties here are load-bearing and are asserted rather than assumed: only
 * <em>upholding</em> publishes an event (so only upholding can move money), and the booking's own
 * status is never touched by any of it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeWorkflowTest {

    @Mock
    private DisputeQueryRepository disputes;

    @Mock
    private DisputeStatusChangeRepository history;

    @Mock
    private OutboxRecorder outbox;

    private DisputeWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new DisputeWorkflow(disputes, history, outbox, 5);
        when(disputes.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));
        when(disputes.findByBookingReference(anyString())).thenReturn(Optional.empty());
    }

    private static Booking completed() {
        return new Booking()
            .reference("b-1")
            .customerLogin("ama.owusu")
            .professionalRef("p1")
            .professionalLogin("akosua.mensah")
            .currency("GHS")
            .priceMinor(28000L)
            .scheduledDate(LocalDate.of(2026, 8, 10))
            .scheduledTime(LocalTime.of(10, 0))
            .status(BookingStatus.COMPLETED);
    }

    private static Dispute open() {
        return new Dispute().reference("d-abc").bookingReference("b-1").status(DisputeStatus.OPEN).professionalRef("p1").currency("GHS");
    }

    @Test
    @DisplayName("raising records the deadline and opens the dispute")
    void raiseOpens() {
        Dispute d = workflow.raise(completed(), "ama.owusu", CancelledBy.CUSTOMER, "the session never happened");

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(d.getBookingReference()).isEqualTo("b-1");
        assertThat(d.getDueBy()).isAfter(d.getRaisedAt());
        assertThat(d.getCurrency()).isEqualTo("GHS");
        verify(history).save(any());
        // Raising is not a decision, so nothing is published.
        verify(outbox, never()).record(anyString(), any(Dispute.class), anyString());
    }

    @Test
    @DisplayName("a booking that has not happened cannot be disputed")
    void onlyCompletedSessionsCanBeDisputed() {
        Booking confirmed = completed().status(BookingStatus.CONFIRMED);

        // Otherwise this would be a way to escape a late-cancellation fee by calling it a
        // grievance: cancelling is a different act with different consequences.
        assertThatThrownBy(() -> workflow.raise(confirmed, "ama.owusu", CancelledBy.CUSTOMER, "changed my mind"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("only a completed or no-show session");
    }

    @Test
    @DisplayName("a second dispute on the same booking is refused")
    void oneDisputePerBooking() {
        when(disputes.findByBookingReference("b-1")).thenReturn(Optional.of(open()));

        assertThatThrownBy(() -> workflow.raise(completed(), "ama.owusu", CancelledBy.CUSTOMER, "again"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has dispute");
    }

    @Test
    @DisplayName("upholding publishes exactly one event and records who decided")
    void upholdingPublishes() {
        Dispute resolved = workflow.apply(open(), new DisputeTransition.Uphold("not delivered", 14000L), "desk.kwame");

        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("desk.kwame");
        assertThat(resolved.getRefundMinor()).isEqualTo(14000L);
        verify(outbox).record(eq("dispute.resolved"), any(Dispute.class), eq("desk.kwame"));
    }

    @Test
    @DisplayName("rejecting publishes NOTHING — only upholding moves money")
    void rejectingPublishesNothing() {
        Dispute rejected = workflow.apply(open(), new DisputeTransition.Reject("session was delivered"), "desk.kwame");

        assertThat(rejected.getStatus()).isEqualTo(DisputeStatus.REJECTED);
        verify(outbox, never()).record(anyString(), any(Dispute.class), anyString());
    }

    @Test
    @DisplayName("picking a dispute up publishes nothing either")
    void reviewingPublishesNothing() {
        Dispute reviewing = workflow.apply(open(), new DisputeTransition.Review(), "desk.kwame");

        assertThat(reviewing.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        verify(outbox, never()).record(anyString(), any(Dispute.class), anyString());
    }

    @Test
    @DisplayName("a resolved dispute cannot be resolved again")
    void resolvedIsTerminal() {
        Dispute alreadyResolved = open().status(DisputeStatus.RESOLVED);

        // Reopening would mean either double-reversing a ledger entry or un-reversing one.
        assertThatThrownBy(() -> workflow.apply(alreadyResolved, new DisputeTransition.Uphold("again", null), "desk.kwame"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("legal only from");
        verify(outbox, never()).record(anyString(), any(Dispute.class), anyString());
    }

    @Test
    @DisplayName("a rejected dispute cannot be flipped to upheld")
    void rejectedIsTerminal() {
        assertThatThrownBy(() -> workflow.apply(open().status(DisputeStatus.REJECTED), new DisputeTransition.Uphold("second thoughts", null), "desk.kwame")
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an obviously correct complaint can be upheld without a ceremonial review step")
    void upholdIsLegalStraightFromOpen() {
        Dispute resolved = workflow.apply(open(), new DisputeTransition.Uphold("clear cut", null), "desk.kwame");

        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    }

    @Test
    @DisplayName("the booking's own status is never touched by a dispute")
    void bookingStatusIsUnaffected() {
        Booking booking = completed();
        workflow.raise(booking, "ama.owusu", CancelledBy.CUSTOMER, "the session never happened");
        workflow.apply(open(), new DisputeTransition.Uphold("not delivered", null), "desk.kwame");

        // A booking that was completed stays completed, because it was. The dispute is a separate
        // fact about the same booking -- which is exactly why DisputeStatus is its own enum
        // rather than more values on BookingStatus.
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }
}
