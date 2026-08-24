package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CategoryTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Category getCategorySample1() {
        return new Category().id(1L).code("code1").name("name1").blurb("blurb1").icon("icon1").sortOrder(1);
    }

    public static Category getCategorySample2() {
        return new Category().id(2L).code("code2").name("name2").blurb("blurb2").icon("icon2").sortOrder(2);
    }

    public static Category getCategoryRandomSampleGenerator() {
        return new Category()
            .id(longCount.incrementAndGet())
            .code(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .blurb(UUID.randomUUID().toString())
            .icon(UUID.randomUUID().toString())
            .sortOrder(intCount.incrementAndGet());
    }
}
