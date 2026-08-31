package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProfessionalTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Professional getProfessionalSample1() {
        return new Professional()
            .id(1L)
            .reference("reference1")
            .userLogin("userLogin1")
            .displayName("displayName1")
            .initials("initials1")
            .headline("headline1")
            .speciality("speciality1")
            .city("city1")
            .countryCode("countryCode1")
            .yearsPractising(1)
            .responseMinutes(1)
            .rebookRatePct(1)
            .languages("languages1")
            .deliveryModes("deliveryModes1")
            .avatarGradientFrom("avatarGradientFrom1")
            .avatarGradientTo("avatarGradientTo1")
            .zoneId("zoneId1");
    }

    public static Professional getProfessionalSample2() {
        return new Professional()
            .id(2L)
            .reference("reference2")
            .userLogin("userLogin2")
            .displayName("displayName2")
            .initials("initials2")
            .headline("headline2")
            .speciality("speciality2")
            .city("city2")
            .countryCode("countryCode2")
            .yearsPractising(2)
            .responseMinutes(2)
            .rebookRatePct(2)
            .languages("languages2")
            .deliveryModes("deliveryModes2")
            .avatarGradientFrom("avatarGradientFrom2")
            .avatarGradientTo("avatarGradientTo2")
            .zoneId("zoneId2");
    }

    public static Professional getProfessionalRandomSampleGenerator() {
        return new Professional()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .userLogin(UUID.randomUUID().toString())
            .displayName(UUID.randomUUID().toString())
            .initials(UUID.randomUUID().toString())
            .headline(UUID.randomUUID().toString())
            .speciality(UUID.randomUUID().toString())
            .city(UUID.randomUUID().toString())
            .countryCode(UUID.randomUUID().toString())
            .yearsPractising(intCount.incrementAndGet())
            .responseMinutes(intCount.incrementAndGet())
            .rebookRatePct(intCount.incrementAndGet())
            .languages(UUID.randomUUID().toString())
            .deliveryModes(UUID.randomUUID().toString())
            .avatarGradientFrom(UUID.randomUUID().toString())
            .avatarGradientTo(UUID.randomUUID().toString())
            .zoneId(UUID.randomUUID().toString());
    }
}
