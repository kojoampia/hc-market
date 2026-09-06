package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.BookingStatusChange;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.CancelledBy;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.BookingHistoryRepository;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The event says <em>when the act it reports happened</em> — {@code decisions.md} D53, backlog
 * NEW-13.
 *
 * <p>This is the producing half. Payout prices a ledger row against the {@code BrokerageConfig} in
 * force <em>when the booking completed</em>, and until this package the payload gave it nothing to
 * decide that with: {@code bookingRaisedAt} and no completion instant, so the consumer read its own
 * clock and a delayed event was priced at terms the customer was never shown. Neither number can be
 * identified as wrong afterwards — a ledger row records the amounts computed from a rate and never
 * the rate.
 *
 * <p>Two fields rather than one, because two events carry a money decision and they are taken at
 * different moments: {@code booking.completed} is priced at the completion and
 * {@code booking.cancelled}'s late fee at the cancellation. They are named the way
 * {@code bookingRaisedAt} is — a property of the <strong>booking</strong>, prefixed so it can never
 * be read as the envelope's {@code occurredAt}, which is a different and legitimate fact one level
 * up.
 *
 * <p><strong>The order inside {@code BookingWorkflow.apply} is what these tests really pin.</strong>
 * The transition writes {@code completedAt} and then records the event, so the field is populated;
 * swap those two statements and the payload carries {@code null}, every test that reads the recorder
 * directly stays green, and payout silently falls back to the envelope for every event in the estate.
 * So the assertion is made through {@code apply}, not through {@link OutboxRecorder} alone.
 */
class TheEventSaysWhenTheBookingHappenedTest {

    private final ObjectMapper reader = new ObjectMapper();

    /** A booking far enough from any appointment that cancelling it is free — the late fee is not what is under test. */
    private static Booking confirmed() {
        return new Booking()
            .reference("b-1")
            .customerLogin("ama.customer")
            .customerName("Ama")
            .professionalRef("p1")
            .professionalLogin("akosua.pro")
            .serviceRef("s1")
            .serviceName("Home visit")
            .priceMinor(28000L)
            .currency("GHS")
            .scheduledDate(LocalDate.of(2099, 9, 12))
            .scheduledTime(LocalTime.of(10, 0))
            .zoneId("Africa/Accra")
            .deliveryMode(DeliveryMode.HOME_VISIT)
            .status(BookingStatus.CONFIRMED)
            .careSummaryShared(false)
            .reviewed(false)
            .raisedAt(Instant.parse("2026-08-10T09:15:00Z"));
    }

    /** The workflow with its three repositories stubbed to pass their arguments straight back. */
    private record Rig(BookingWorkflow workflow, OutboxEventRepository outbox) {}

    private static Rig rig() {
        BookingQueryRepository bookings = mock(BookingQueryRepository.class);
        when(bookings.save(any(Booking.class))).thenAnswer(saved -> saved.getArgument(0));
        BookingHistoryRepository history = mock(BookingHistoryRepository.class);
        when(history.save(any(BookingStatusChange.class))).thenAnswer(saved -> saved.getArgument(0));
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(saved -> saved.getArgument(0));
        return new Rig(new BookingWorkflow(bookings, history, new OutboxRecorder(outbox, new ObjectMapper()), 24), outbox);
    }

    private JsonNode payloadOf(OutboxEventRepository outbox) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(outbox).save(captor.capture());
        return reader.readTree(captor.getValue().getPayload());
    }

    @Test
    @DisplayName("booking.completed carries the instant the booking was completed")
    void completedCarriesItsCompletionInstant() throws Exception {
        Rig rig = rig();
        Booking completed = rig.workflow().apply(confirmed(), new BookingTransition.Complete(), "akosua.pro");

        JsonNode payload = payloadOf(rig.outbox());
        assertThat(completed.getCompletedAt()).as("the transition must have written one to publish").isNotNull();
        assertThat(payload.path("bookingCompletedAt").asText())
            .as("payout prices the ledger row from this; absent, it falls back and says so")
            .isEqualTo(completed.getCompletedAt().toString());
    }

    @Test
    @DisplayName("booking.cancelled carries the instant the booking was cancelled")
    void cancelledCarriesItsCancellationInstant() throws Exception {
        Rig rig = rig();
        Booking cancelled = rig
            .workflow()
            .apply(confirmed(), new BookingTransition.Cancel(CancelledBy.CUSTOMER, "changed my mind"), "ama.customer");

        JsonNode payload = payloadOf(rig.outbox());
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(payload.path("bookingCancelledAt").asText())
            .as("a late-cancellation fee is priced from this")
            .isEqualTo(cancelled.getCancelledAt().toString());
    }

    /**
     * The payload is a snapshot of the booking, not of the event, so every event about a booking
     * carries every field — nulls included. That is the shape {@code bookingRaisedAt} already has and
     * it is why payout must treat an explicit JSON null exactly as it treats an absent field.
     */
    @Test
    @DisplayName("an event about a booking that has neither happened yet carries both fields as null")
    void anEarlyEventCarriesBothAsNull() throws Exception {
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(saved -> saved.getArgument(0));
        OutboxRecorder recorder = new OutboxRecorder(outbox, new ObjectMapper());

        JsonNode payload = reader.readTree(recorder.record("booking.requested", confirmed(), "ama.customer").getPayload());

        assertThat(payload.hasNonNull("bookingCompletedAt")).isFalse();
        assertThat(payload.hasNonNull("bookingCancelledAt")).isFalse();
        // Present-and-null rather than missing, which is what makes Jackson's NullNode.asText("x")
        // answering "null" a trap the consumer has to be written around rather than a hypothetical.
        assertThat(payload.has("bookingCompletedAt")).isTrue();
        assertThat(payload.has("bookingCancelledAt")).isTrue();
    }
}
