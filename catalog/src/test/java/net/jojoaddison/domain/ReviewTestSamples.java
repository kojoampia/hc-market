package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ReviewTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Review getReviewSample1() {
        return new Review()
            .id(1L)
            .reference("reference1")
            .customerLogin("customerLogin1")
            .authorName("authorName1")
            .authorInitials("authorInitials1")
            .stars(1)
            .bookingReference("bookingReference1");
    }

    public static Review getReviewSample2() {
        return new Review()
            .id(2L)
            .reference("reference2")
            .customerLogin("customerLogin2")
            .authorName("authorName2")
            .authorInitials("authorInitials2")
            .stars(2)
            .bookingReference("bookingReference2");
    }

    public static Review getReviewRandomSampleGenerator() {
        return new Review()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .customerLogin(UUID.randomUUID().toString())
            .authorName(UUID.randomUUID().toString())
            .authorInitials(UUID.randomUUID().toString())
            .stars(intCount.incrementAndGet())
            .bookingReference(UUID.randomUUID().toString());
    }
}
