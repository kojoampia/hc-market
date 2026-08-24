package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ServiceOfferingTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static ServiceOffering getServiceOfferingSample1() {
        return new ServiceOffering()
            .id(1L)
            .reference("reference1")
            .name("name1")
            .durationMinutes(1)
            .priceMinor(1L)
            .currency("currency1")
            .description("description1")
            .sortOrder(1);
    }

    public static ServiceOffering getServiceOfferingSample2() {
        return new ServiceOffering()
            .id(2L)
            .reference("reference2")
            .name("name2")
            .durationMinutes(2)
            .priceMinor(2L)
            .currency("currency2")
            .description("description2")
            .sortOrder(2);
    }

    public static ServiceOffering getServiceOfferingRandomSampleGenerator() {
        return new ServiceOffering()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .durationMinutes(intCount.incrementAndGet())
            .priceMinor(longCount.incrementAndGet())
            .currency(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .sortOrder(intCount.incrementAndGet());
    }
}
