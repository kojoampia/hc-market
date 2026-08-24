package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PayoutTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Payout getPayoutSample1() {
        return new Payout()
            .id(1L)
            .reference("reference1")
            .professionalRef("professionalRef1")
            .grossMinor(1L)
            .commissionMinor(1L)
            .netMinor(1L)
            .currency("currency1")
            .bankReference("bankReference1");
    }

    public static Payout getPayoutSample2() {
        return new Payout()
            .id(2L)
            .reference("reference2")
            .professionalRef("professionalRef2")
            .grossMinor(2L)
            .commissionMinor(2L)
            .netMinor(2L)
            .currency("currency2")
            .bankReference("bankReference2");
    }

    public static Payout getPayoutRandomSampleGenerator() {
        return new Payout()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .professionalRef(UUID.randomUUID().toString())
            .grossMinor(longCount.incrementAndGet())
            .commissionMinor(longCount.incrementAndGet())
            .netMinor(longCount.incrementAndGet())
            .currency(UUID.randomUUID().toString())
            .bankReference(UUID.randomUUID().toString());
    }
}
