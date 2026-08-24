package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HighlightTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Highlight getHighlightSample1() {
        return new Highlight().id(1L).label("label1").sortOrder(1);
    }

    public static Highlight getHighlightSample2() {
        return new Highlight().id(2L).label("label2").sortOrder(2);
    }

    public static Highlight getHighlightRandomSampleGenerator() {
        return new Highlight().id(longCount.incrementAndGet()).label(UUID.randomUUID().toString()).sortOrder(intCount.incrementAndGet());
    }
}
