package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.jojoaddison.domain.Booking;
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
}
