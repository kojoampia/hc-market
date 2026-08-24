package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class AvailabilitySlotTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static AvailabilitySlot getAvailabilitySlotSample1() {
        return new AvailabilitySlot().id(1L).slotTime("slotTime1");
    }

    public static AvailabilitySlot getAvailabilitySlotSample2() {
        return new AvailabilitySlot().id(2L).slotTime("slotTime2");
    }

    public static AvailabilitySlot getAvailabilitySlotRandomSampleGenerator() {
        return new AvailabilitySlot().id(longCount.incrementAndGet()).slotTime(UUID.randomUUID().toString());
    }
}
