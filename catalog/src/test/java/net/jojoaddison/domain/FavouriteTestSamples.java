package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class FavouriteTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Favourite getFavouriteSample1() {
        return new Favourite().id(1L).customerLogin("customerLogin1").professionalRef("professionalRef1");
    }

    public static Favourite getFavouriteSample2() {
        return new Favourite().id(2L).customerLogin("customerLogin2").professionalRef("professionalRef2");
    }

    public static Favourite getFavouriteRandomSampleGenerator() {
        return new Favourite()
            .id(longCount.incrementAndGet())
            .customerLogin(UUID.randomUUID().toString())
            .professionalRef(UUID.randomUUID().toString());
    }
}
