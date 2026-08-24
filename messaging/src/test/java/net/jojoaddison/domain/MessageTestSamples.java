package net.jojoaddison.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class MessageTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Message getMessageSample1() {
        return new Message().id(1L);
    }

    public static Message getMessageSample2() {
        return new Message().id(2L);
    }

    public static Message getMessageRandomSampleGenerator() {
        return new Message().id(longCount.incrementAndGet());
    }
}
