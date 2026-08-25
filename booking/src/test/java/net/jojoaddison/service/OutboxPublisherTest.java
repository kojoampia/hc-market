package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.jojoaddison.domain.OutboxEvent;
import net.jojoaddison.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * The outbox publisher.
 *
 * <p>The happy path is barely worth a test. What matters is the failure path, because that is the
 * entire reason the outbox exists: <strong>a send that fails must leave the row unsent</strong>, so
 * the next tick retries it. Get that backwards and the table becomes a log of events that were
 * silently dropped, which is worse than having no outbox at all — it looks like it worked.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outbox;

    @Mock
    private KafkaTemplate<String, String> kafka;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outbox, kafka);
    }

    private static OutboxEvent event() {
        return new OutboxEvent()
            .eventId("11111111-2222-3333-4444-555555555555")
            .type("healthconnect.booking.accepted")
            .topic("healthconnect.booking.accepted")
            .aggregateRef("b-1")
            .actor("akosua.mensah")
            .occurredAt(Instant.parse("2026-08-25T09:00:00Z"))
            .payload("{\"bookingRef\":\"b-1\"}");
    }

    @Test
    @DisplayName("a successful send marks the row sent and clears any earlier error")
    void successMarksSent() {
        OutboxEvent e = event();
        e.setLastError("a previous failure");
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mockResult()));

        publisher.drain();

        assertThat(e.getSentAt()).isNotNull();
        assertThat(e.getLastError()).isNull();
        verify(outbox).saveAll(List.of(e));
    }

    /**
     * The property the whole design rests on. This is the test that would have caught the
     * ByteArraySerializer misconfiguration as a design question rather than as 44 retries in a log.
     */
    @Test
    @DisplayName("a failed send leaves the row UNSENT so the next tick retries it")
    void failureLeavesRowUnsent() {
        OutboxEvent e = event();
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Can't convert key of class java.lang.String")));

        publisher.drain();

        assertThat(e.getSentAt()).as("an unsent row is a to-do list; a sent one is a lost event").isNull();
        assertThat(e.getAttempts()).isEqualTo(1);
        assertThat(e.getLastError()).contains("Can't convert key");
        verify(outbox).saveAll(List.of(e));
    }

    @Test
    @DisplayName("attempts accumulate across ticks rather than resetting")
    void attemptsAccumulate() {
        OutboxEvent e = event();
        e.setAttempts(43);
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("still down")));

        publisher.drain();

        assertThat(e.getAttempts()).isEqualTo(44);
    }

    @Test
    @DisplayName("one failure does not stop the rest of the batch")
    void oneFailureDoesNotBlockTheBatch() {
        OutboxEvent bad = event().eventId("bad").aggregateRef("b-bad");
        OutboxEvent good = event().eventId("good").aggregateRef("b-good");
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of(bad, good));
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")))
            .thenReturn(CompletableFuture.completedFuture(mockResult()));

        publisher.drain();

        assertThat(bad.getSentAt()).isNull();
        assertThat(good.getSentAt()).as("a poisoned row must not strand the ones behind it").isNotNull();
    }

    @Test
    @DisplayName("nothing unsent means no broker traffic and no write")
    void emptyOutboxDoesNothing() {
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of());

        publisher.drain();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
        verify(outbox, never()).saveAll(any());
    }

    /** The booking reference is the partition key, so per-booking ordering survives. */
    @Test
    @DisplayName("the aggregate reference is used as the Kafka key")
    void aggregateRefIsThePartitionKey() {
        OutboxEvent e = event();
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mockResult()));

        publisher.drain();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(1)).send(topic.capture(), key.capture(), body.capture());

        assertThat(topic.getValue()).isEqualTo("healthconnect.booking.accepted");
        assertThat(key.getValue()).isEqualTo("b-1");
        assertThat(body.getValue())
            .as("spec §7's envelope, with the payload spliced in rather than re-encoded")
            .contains("\"eventId\":\"11111111-2222-3333-4444-555555555555\"")
            .contains("\"type\":\"healthconnect.booking.accepted\"")
            .contains("\"aggregateRef\":\"b-1\"")
            .contains("\"actor\":\"akosua.mensah\"")
            .contains("\"payload\":{\"bookingRef\":\"b-1\"}");
    }

    @Test
    @DisplayName("a null actor is emitted as JSON null, not the string \"null\"")
    void nullActorIsJsonNull() {
        OutboxEvent e = event().actor(null);
        when(outbox.findUnsent(any(Pageable.class))).thenReturn(List.of(e));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mockResult()));

        publisher.drain();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(anyString(), anyString(), body.capture());
        assertThat(body.getValue()).contains("\"actor\":null").doesNotContain("\"actor\":\"null\"");
    }

    @SuppressWarnings("unchecked")
    private static SendResult<String, String> mockResult() {
        return (SendResult<String, String>) org.mockito.Mockito.mock(SendResult.class);
    }
}
