package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class LedgerTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Ledger getLedgerSample1() {
        return new Ledger()
            .id(1L)
            .bookingReference("bookingReference1")
            .professionalRef("professionalRef1")
            .grossMinor(1L)
            .commissionMinor(1L)
            .netMinor(1L)
            .currency("currency1");
    }

    public static Ledger getLedgerSample2() {
        return new Ledger()
            .id(2L)
            .bookingReference("bookingReference2")
            .professionalRef("professionalRef2")
            .grossMinor(2L)
            .commissionMinor(2L)
            .netMinor(2L)
            .currency("currency2");
    }

    public static Ledger getLedgerRandomSampleGenerator() {
        return new Ledger()
            .id(longCount.incrementAndGet())
            .bookingReference(UUID.randomUUID().toString())
            .professionalRef(UUID.randomUUID().toString())
            .grossMinor(longCount.incrementAndGet())
            .commissionMinor(longCount.incrementAndGet())
            .netMinor(longCount.incrementAndGet())
            .currency(UUID.randomUUID().toString());
    }
}
