package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BookingStatusChangeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static BookingStatusChange getBookingStatusChangeSample1() {
        return new BookingStatusChange().id(1L).actor("actor1").note("note1");
    }

    public static BookingStatusChange getBookingStatusChangeSample2() {
        return new BookingStatusChange().id(2L).actor("actor2").note("note2");
    }

    public static BookingStatusChange getBookingStatusChangeRandomSampleGenerator() {
        return new BookingStatusChange()
            .id(longCount.incrementAndGet())
            .actor(UUID.randomUUID().toString())
            .note(UUID.randomUUID().toString());
    }
}
