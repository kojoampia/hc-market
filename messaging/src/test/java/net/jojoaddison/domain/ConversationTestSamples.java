package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ConversationTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Conversation getConversationSample1() {
        return new Conversation()
            .id(1L)
            .reference("reference1")
            .customerLogin("customerLogin1")
            .professionalRef("professionalRef1")
            .bookingReference("bookingReference1");
    }

    public static Conversation getConversationSample2() {
        return new Conversation()
            .id(2L)
            .reference("reference2")
            .customerLogin("customerLogin2")
            .professionalRef("professionalRef2")
            .bookingReference("bookingReference2");
    }

    public static Conversation getConversationRandomSampleGenerator() {
        return new Conversation()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .customerLogin(UUID.randomUUID().toString())
            .professionalRef(UUID.randomUUID().toString())
            .bookingReference(UUID.randomUUID().toString());
    }
}
