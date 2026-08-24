package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BrokerageConfigTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static BrokerageConfig getBrokerageConfigSample1() {
        return new BrokerageConfig().id(1L).payoutLagDays(1).freeCancellationHours(1).currency("currency1");
    }

    public static BrokerageConfig getBrokerageConfigSample2() {
        return new BrokerageConfig().id(2L).payoutLagDays(2).freeCancellationHours(2).currency("currency2");
    }

    public static BrokerageConfig getBrokerageConfigRandomSampleGenerator() {
        return new BrokerageConfig()
            .id(longCount.incrementAndGet())
            .payoutLagDays(intCount.incrementAndGet())
            .freeCancellationHours(intCount.incrementAndGet())
            .currency(UUID.randomUUID().toString());
    }
}
