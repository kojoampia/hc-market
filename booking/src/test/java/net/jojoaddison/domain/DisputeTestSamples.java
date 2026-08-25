package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class DisputeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Dispute getDisputeSample1() {
        return new Dispute()
            .id(1L)
            .reference("reference1")
            .bookingReference("bookingReference1")
            .raisedByLogin("raisedByLogin1")
            .professionalRef("professionalRef1")
            .reason("reason1")
            .resolution("resolution1")
            .resolvedBy("resolvedBy1")
            .refundMinor(1L)
            .currency("currency1");
    }

    public static Dispute getDisputeSample2() {
        return new Dispute()
            .id(2L)
            .reference("reference2")
            .bookingReference("bookingReference2")
            .raisedByLogin("raisedByLogin2")
            .professionalRef("professionalRef2")
            .reason("reason2")
            .resolution("resolution2")
            .resolvedBy("resolvedBy2")
            .refundMinor(2L)
            .currency("currency2");
    }

    public static Dispute getDisputeRandomSampleGenerator() {
        return new Dispute()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .bookingReference(UUID.randomUUID().toString())
            .raisedByLogin(UUID.randomUUID().toString())
            .professionalRef(UUID.randomUUID().toString())
            .reason(UUID.randomUUID().toString())
            .resolution(UUID.randomUUID().toString())
            .resolvedBy(UUID.randomUUID().toString())
            .refundMinor(longCount.incrementAndGet())
            .currency(UUID.randomUUID().toString());
    }
}
