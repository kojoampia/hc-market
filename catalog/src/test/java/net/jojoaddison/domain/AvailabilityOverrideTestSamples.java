package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class AvailabilityOverrideTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static AvailabilityOverride getAvailabilityOverrideSample1() {
        return new AvailabilityOverride().id(1L).note("note1");
    }

    public static AvailabilityOverride getAvailabilityOverrideSample2() {
        return new AvailabilityOverride().id(2L).note("note2");
    }

    public static AvailabilityOverride getAvailabilityOverrideRandomSampleGenerator() {
        return new AvailabilityOverride().id(longCount.incrementAndGet()).note(UUID.randomUUID().toString());
    }
}
