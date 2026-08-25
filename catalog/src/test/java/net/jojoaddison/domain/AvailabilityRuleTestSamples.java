package net.jojoaddison.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AvailabilityRuleTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static AvailabilityRule getAvailabilityRuleSample1() {
        return new AvailabilityRule().id(1L).slotMinutes(1);
    }

    public static AvailabilityRule getAvailabilityRuleSample2() {
        return new AvailabilityRule().id(2L).slotMinutes(2);
    }

    public static AvailabilityRule getAvailabilityRuleRandomSampleGenerator() {
        return new AvailabilityRule().id(longCount.incrementAndGet()).slotMinutes(intCount.incrementAndGet());
    }
}
