package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import net.jojoaddison.domain.Booking;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.domain.enumeration.DeliveryMode;
import net.jojoaddison.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbox recorder's transaction contract.
 *
 * <p>{@code Propagation.MANDATORY} is the entire safety property of this class: it refuses to run
 * outside an existing transaction, which is what guarantees an event can only be written as part of
 * the same transaction that changed the booking.
 *
 * <p>Asserting an annotation is usually a poor test — it checks the spelling, not the behaviour. It
 * earns its place here because the failure mode is silent and severe: with {@code REQUIRED} (the
 * default) this class still compiles, still passes every integration test, and still writes events —
 * while quietly opening its own transaction and committing them independently of the booking. That
 * reintroduces the dual write the outbox exists to remove, and nothing observable changes until a
 * crash lands between the two commits.
 */
class OutboxRecorderTest {

    @Test
    @DisplayName("record() is MANDATORY, so it cannot run outside a transaction")
    void recordIsMandatory() throws NoSuchMethodException {
        Method record = OutboxRecorder.class.getMethod("record", String.class, Booking.class, String.class);
        Transactional tx = record.getAnnotation(Transactional.class);

        assertThat(tx).as("record() must be transactional").isNotNull();
        assertThat(tx.propagation())
            .as("REQUIRED would let this open its own transaction and commit the event independently of the booking")
            .isEqualTo(Propagation.MANDATORY);
    }

    @Test
    @DisplayName("the topic prefix is the one spec §7 specifies")
    void topicPrefix() {
        assertThat(OutboxRecorder.TOPIC_PREFIX).isEqualTo("healthconnect.");
    }

    /**
     * <strong>{@code bookingRaisedAt} is the booking's age, and every event about one booking reports
     * the same instant — {@code decisions.md} D37, backlog WP-08.</strong>
     *
     * <p>Messaging decides whether an erasure covers a booking by comparing this field to its
     * {@code erasedAt}, and the whole decision rests on the field being a property of the
     * <em>booking</em> rather than of the message carrying it. A booking that was still open when an
     * erasure ran goes on emitting events — accepted, completed, cancelled — and if this moved with
     * them, each one would report the booking as newer than the erasure and messaging would write the
     * customer's real login back one lifecycle step at a time.
     *
     * <p>So the assertion is that two events published at different times, about one booking, carry
     * one {@code bookingRaisedAt}, and that it is {@link Booking#getRaisedAt()} rather than the
     * envelope's {@code occurredAt} — which the recorder does still stamp with the moment of
     * publication, and which is a different and legitimate fact.
     */
    @Test
    @DisplayName("bookingRaisedAt is the booking's own raisedAt, and does not move between events")
    void payloadCarriesTheBookingsAgeRatherThanTheEvents() throws Exception {
        Instant raised = Instant.parse("2026-08-10T09:15:00Z");
        Booking booking = new Booking()
            .reference("b-1")
            .customerLogin("ama.customer")
            .customerName("Ama")
            .professionalRef("p1")
            .professionalLogin("akosua.pro")
            .serviceRef("s1")
            .serviceName("Home visit")
            .priceMinor(28000L)
            .currency("GHS")
            .scheduledDate(LocalDate.of(2026, 9, 12))
            .scheduledTime(LocalTime.of(10, 0))
            .zoneId("Africa/Accra")
            .deliveryMode(DeliveryMode.HOME_VISIT)
            .status(BookingStatus.REQUESTED)
            .careSummaryShared(false)
            .reviewed(false)
            .raisedAt(raised);

        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(saved -> saved.getArgument(0));
        OutboxRecorder recorder = new OutboxRecorder(outbox, new ObjectMapper());
        ObjectMapper reader = new ObjectMapper();

        JsonNode requested = reader.readTree(recorder.record("booking.requested", booking, "ama.customer").getPayload());
        // The same booking, later in its life. Only its status has moved; its age has not.
        booking.setStatus(BookingStatus.COMPLETED);
        JsonNode completed = reader.readTree(recorder.record("booking.completed", booking, "akosua.pro").getPayload());

        assertThat(requested.path("bookingRaisedAt").asText()).isEqualTo(raised.toString());
        assertThat(completed.path("bookingRaisedAt").asText())
            .as("a later event on the same booking must report the same age, or the erasure grows back")
            .isEqualTo(raised.toString());
        // And it is not the publication time, which is on the envelope and means something else.
        assertThat(completed.path("bookingRaisedAt").asText()).isNotEqualTo(Instant.now().toString());
    }
}
