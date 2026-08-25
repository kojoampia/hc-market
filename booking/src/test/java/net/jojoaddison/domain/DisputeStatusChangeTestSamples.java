package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class DisputeStatusChangeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static DisputeStatusChange getDisputeStatusChangeSample1() {
        return new DisputeStatusChange().id(1L).actor("actor1").note("note1");
    }

    public static DisputeStatusChange getDisputeStatusChangeSample2() {
        return new DisputeStatusChange().id(2L).actor("actor2").note("note2");
    }

    public static DisputeStatusChange getDisputeStatusChangeRandomSampleGenerator() {
        return new DisputeStatusChange()
            .id(longCount.incrementAndGet())
            .actor(UUID.randomUUID().toString())
            .note(UUID.randomUUID().toString());
    }
}
