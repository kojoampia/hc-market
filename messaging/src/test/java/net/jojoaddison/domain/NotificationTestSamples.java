package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class NotificationTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Notification getNotificationSample1() {
        return new Notification().id(1L).recipientLogin("recipientLogin1").kind("kind1").body("body1").deepLink("deepLink1");
    }

    public static Notification getNotificationSample2() {
        return new Notification().id(2L).recipientLogin("recipientLogin2").kind("kind2").body("body2").deepLink("deepLink2");
    }

    public static Notification getNotificationRandomSampleGenerator() {
        return new Notification()
            .id(longCount.incrementAndGet())
            .recipientLogin(UUID.randomUUID().toString())
            .kind(UUID.randomUUID().toString())
            .body(UUID.randomUUID().toString())
            .deepLink(UUID.randomUUID().toString());
    }
}
