package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class VerificationReviewTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static VerificationReview getVerificationReviewSample1() {
        return new VerificationReview().id(1L).reference("reference1").reviewer("reviewer1").evidenceRef("evidenceRef1").note("note1");
    }

    public static VerificationReview getVerificationReviewSample2() {
        return new VerificationReview().id(2L).reference("reference2").reviewer("reviewer2").evidenceRef("evidenceRef2").note("note2");
    }

    public static VerificationReview getVerificationReviewRandomSampleGenerator() {
        return new VerificationReview()
            .id(longCount.incrementAndGet())
            .reference(UUID.randomUUID().toString())
            .reviewer(UUID.randomUUID().toString())
            .evidenceRef(UUID.randomUUID().toString())
            .note(UUID.randomUUID().toString());
    }
}
