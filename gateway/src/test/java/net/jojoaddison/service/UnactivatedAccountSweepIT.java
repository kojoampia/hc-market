package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.User;
import net.jojoaddison.repository.UserRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tech.jhipster.security.RandomUtil;

/**
 * What the sweep deletes, at the boundary, against a real database — {@code decisions.md} D94,
 * backlog NEW-47.
 *
 * <h2>Why the boundary and not "it deletes something"</h2>
 *
 * <p>D91 §7 records that this deletion had never been watched removing an account: the cron was proven
 * to run, and the delete branch was proven only by reading it, because no unactivated account has ever
 * existed on any estate for it to find. So the interesting assertions are the two either side of the
 * cutoff — an account one hour past the window is gone, an account one hour inside it is untouched —
 * and each is made separately, because a test that only checks the far side passes against a sweep
 * that deletes everything.
 *
 * <p>The cutoff is passed in rather than waiting for a clock, which is what lets the boundary be
 * asserted at all. The window itself is asserted through Spring's binder in
 * {@code AccountRetentionUnitTest}, and that the task is registered on it in
 * {@code UnactivatedAccountSweepUnitTest} — three questions, three places, because one test that
 * answered all three would be green while any one of them was broken.
 */
@IntegrationTest
class UnactivatedAccountSweepIT {

    private static final String PAST_LOGIN = "sweep_past";

    private static final String RECENT_LOGIN = "sweep_recent";

    private static final String ACTIVATED_LOGIN = "sweep_activated";

    private static final String NO_KEY_LOGIN = "sweep_nokey";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnactivatedAccountSweep sweep;

    @AfterEach
    void removeWhateverSurvived() {
        for (String login : List.of(PAST_LOGIN, RECENT_LOGIN, ACTIVATED_LOGIN, NO_KEY_LOGIN)) {
            User user = userRepository.findOneByLogin(login).block();
            if (user != null) {
                userRepository.delete(user).block();
            }
        }
    }

    private User account(String login, boolean activated, String activationKey, Instant createdDate) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        user.setActivated(activated);
        user.setActivationKey(activationKey);
        user.setEmail(login + "@localhost");
        user.setFirstName("sweep");
        user.setLastName("candidate");
        user.setLangKey("en");
        User saved = userRepository.save(user).block();
        // createdDate is set by auditing on insert, so it has to be pushed back and saved again —
        // exactly as the generated UserServiceIT does it.
        saved.setCreatedDate(createdDate);
        return userRepository.save(saved).block();
    }

    @Test
    void anAccountOlderThanTheWindowAndNeverActivatedIsDeleted() {
        Instant now = Instant.now();
        account(PAST_LOGIN, false, RandomUtil.generateActivationKey(), now.minus(4, ChronoUnit.DAYS));

        long deleted = sweep.deleteRegisteredBefore(now.minus(3, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(1);
        assertThat(userRepository.findOneByLogin(PAST_LOGIN).block()).isNull();
    }

    @Test
    void anAccountJUSTINSIDETheWindowIsLeftAlone() {
        // One hour on the safe side of a three-day cutoff. This is the assertion that fails on a sweep
        // whose comparison drifted to "before now" or whose window lost a unit.
        Instant now = Instant.now();
        account(RECENT_LOGIN, false, RandomUtil.generateActivationKey(), now.minus(71, ChronoUnit.HOURS));

        long deleted = sweep.deleteRegisteredBefore(now.minus(72, ChronoUnit.HOURS));

        assertThat(deleted).isZero();
        assertThat(userRepository.findOneByLogin(RECENT_LOGIN).block()).isNotNull();
    }

    @Test
    void anActivatedAccountIsNeverTouchedHoweverOldItIs() {
        Instant now = Instant.now();
        account(ACTIVATED_LOGIN, true, null, now.minus(400, ChronoUnit.DAYS));

        long deleted = sweep.deleteRegisteredBefore(now);

        assertThat(deleted).isZero();
        assertThat(userRepository.findOneByLogin(ACTIVATED_LOGIN).block()).isNotNull();
    }

    @Test
    void anUnactivatedAccountWithNoActivationKeyIsLeftAlone() {
        // The administrator-created case: an account somebody is expected to be handed a password for
        // rather than one that was self-registered. The generated query excludes it and so must this.
        Instant now = Instant.now();
        account(NO_KEY_LOGIN, false, null, now.minus(400, ChronoUnit.DAYS));

        long deleted = sweep.deleteRegisteredBefore(now);

        assertThat(deleted).isZero();
        assertThat(userRepository.findOneByLogin(NO_KEY_LOGIN).block()).isNotNull();
    }

    @Test
    void theSweepTheEstateActuallyRunsUsesTheConfiguredWindow() {
        // sweep() reads the window itself, so this is the path the cron task takes. Three days is the
        // configured default, so the four-day-old account goes and nothing else does.
        Instant now = Instant.now();
        account(PAST_LOGIN, false, RandomUtil.generateActivationKey(), now.minus(4, ChronoUnit.DAYS));
        account(RECENT_LOGIN, false, RandomUtil.generateActivationKey(), now.minus(1, ChronoUnit.DAYS));

        sweep.sweep();

        assertThat(userRepository.findOneByLogin(PAST_LOGIN).block()).isNull();
        assertThat(userRepository.findOneByLogin(RECENT_LOGIN).block()).isNotNull();
    }
}
