package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BookingTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Booking getBookingSample1() {
        return new Booking()
            .id(1L)
            .reference("reference1")
            .customerLogin("customerLogin1")
            .customerName("customerName1")
            .professionalRef("professionalRef1")
            .serviceRef("serviceRef1")
            .serviceName("serviceName1")
            .priceMinor(1L)
            .currency("currency1")
            .scheduledTime("scheduledTime1")
            .customerNote("customerNote1")
            .onBehalfOf("onBehalfOf1")
            .visitAddress("visitAddress1")
            .cancellationReason("cancellationReason1");
    }

    public static Booking getBookingSample2() {
        return new Booking()
            .id(2L)
            .reference("reference2")
            .customerLogin("customerLogin2")
            .customerName("customerName2")
            .professionalRef("professionalRef2")
            .serviceRef("serviceRef2")
            .serviceName("serviceName2")
            .priceMinor(2L)
            .currency("currency2")
            .scheduledTime("scheduledTime2")
            .customerNote("customerNote2")
            .onBehalfOf("onBehalfOf2")
            .visitAddress("visitAddress2")
            .cancellationReason("cancellationReason2");
    }

    public static Booking getBookingRandomSampleGenerator() {
        return new Booking()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .customerLogin(UUID.randomUUID().toString())
            .customerName(UUID.randomUUID().toString())
            .professionalRef(UUID.randomUUID().toString())
            .serviceRef(UUID.randomUUID().toString())
            .serviceName(UUID.randomUUID().toString())
            .priceMinor(longCount.incrementAndGet())
            .currency(UUID.randomUUID().toString())
            .scheduledTime(UUID.randomUUID().toString())
            .customerNote(UUID.randomUUID().toString())
            .onBehalfOf(UUID.randomUUID().toString())
            .visitAddress(UUID.randomUUID().toString())
            .cancellationReason(UUID.randomUUID().toString());
    }
}
