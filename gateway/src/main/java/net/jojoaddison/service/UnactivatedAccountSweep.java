package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import net.jojoaddison.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Deletes accounts that were registered and never activated, on a schedule and after a window this
 * estate states rather than inherits — {@code decisions.md} D94, backlog NEW-47.
 *
 * <h2>This replaces a {@code @Scheduled} in a GENERATED file</h2>
 *
 * <p>{@code UserService.removeNotActivatedUsers} carried {@code @Scheduled(cron = "0 0 1 * * ?")} and
 * queried three days with {@code Instant.now().minus(3, ChronoUnit.DAYS)}. Both numbers were literals
 * in a file JHipster regenerates, which is precisely why nobody had ever read them: you do not look
 * for your organisation's retention policy in a file you did not write. The annotation is gone from
 * there and the policy lives here, in a <em>new</em> file, so a regeneration cannot quietly take the
 * configurability away — and if one puts the annotation back, this estate acquires a second sweep on
 * a hard-coded three days that would delete early on any estate that had chosen longer. Two things go
 * red in that case: {@code ThereIsOneAccountSweepTest} (ArchUnit, over the whole main tree) and CI's
 * <em>"The unactivated-account sweep must be the estate's only one"</em>.
 *
 * <h2>Why {@code SchedulingConfigurer} and not {@code @Scheduled}</h2>
 *
 * <p>Because the cron expression is configuration, and compose's {@code ${X:-}} sets an empty variable
 * rather than leaving one unset. {@code @Scheduled(cron = "${...:0 0 1 * * ?}")} prefers the empty
 * environment value to its own default and fails the context on every estate that passes the variable
 * through without setting it — and passing it through is what stops it being a variable that silently
 * does nothing (D46, D50). Blank-handling therefore lives in {@link AccountRetention}, in Java, where
 * it is tested; this class asks for the value once, at startup, and registers a cron task with it.
 *
 * <p>A malformed cron or an unreadable window is refused there, so it fails at startup rather than at
 * 01:00 on a morning nobody is watching. The scheduling itself is switched on by the generated
 * {@code AsyncConfiguration}'s {@code @EnableScheduling}, under {@code @Profile("!testdev &
 * !testprod")} — neither of which is active on any estate, so this runs in {@code dev}, {@code test}
 * and {@code prod} exactly as the generated annotation did (D91 §3).
 *
 * <h2>It logs a COUNT, and never a user</h2>
 *
 * <p>The generated method it replaces ends with {@code LOG.debug("Deleted User: {}", user)}, and
 * {@code User.toString()} renders the login, both names, the email address and the activation key —
 * at DEBUG, which is the level {@code net.jojoaddison} runs at under the {@code dev} profile, which is
 * what the quality box runs. Nothing has ever leaked, because no unactivated account has ever existed
 * on any estate for it to delete (D91 §7). It stays available to the two generated integration tests
 * that call it and it is not what this estate schedules; what this estate schedules logs how many rows
 * it removed and over what window, at INFO, because the point of D94 is that an irreversible deletion
 * of personal data leaves a record.
 */
@Component
public class UnactivatedAccountSweep implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(UnactivatedAccountSweep.class);

    private final UserRepository userRepository;

    private final AccountRetention retention;

    public UnactivatedAccountSweep(UserRepository userRepository, AccountRetention retention) {
        this.userRepository = userRepository;
        this.retention = retention;
    }

    /**
     * Registers the sweep. Both values are read here rather than per run, so a value this estate
     * cannot use is a startup failure and not a silent no-op at 01:00.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Duration window = retention.unactivatedRetention();
        String cron = retention.unactivatedSweepCron();
        LOG.info(
            "Unactivated accounts are deleted {} days after registration, swept on '{}' — the estate's stated policy, " +
                "docs/privacy-notice.md 7.1 and decisions.md D94",
            window.toDays(),
            cron
        );
        registrar.addCronTask(this::sweep, cron);
    }

    /**
     * Deletes every account that is still unactivated, HAS an activation key, and was CREATED longer
     * ago than the configured window.
     *
     * <p>The three conditions are the generated query's and the wording matters: it filters on
     * {@code createdDate}, not on the key's own age — the key carries no timestamp — and an
     * unactivated account with a {@code null} key is left alone, because that is an account somebody
     * was meant to be handed a password for rather than one that was self-registered. Earlier drafts
     * of this javadoc and of {@code docs/processing-record.md} said "activation key older than",
     * which names a column the code does not read.
     *
     * <p>Blocks, on a scheduler thread rather than on the event loop — the same thing the generated
     * method did, for the same reason: a scheduled task has nowhere to return a {@code Mono} to.
     *
     * <p>Package-private on purpose. Nothing may reach this over HTTP: an endpoint that runs a
     * retention sweep on demand is a way to make somebody's account disappear a day early, and the
     * only callers that should exist are this class's own task and the integration test that watches
     * the boundary.
     */
    void sweep() {
        Duration window = retention.unactivatedRetention();
        long deleted = deleteRegisteredBefore(Instant.now().minus(window));
        if (deleted > 0) {
            LOG.info(
                "Deleted {} account(s) that were registered more than {} days ago and never activated (decisions.md D94)",
                deleted,
                window.toDays()
            );
        } else {
            LOG.debug("No unactivated account is older than {} days", window.toDays());
        }
    }

    /**
     * The deletion itself, taking the cutoff as a parameter so a test can put the boundary where it
     * needs it without waiting three days or moving a clock.
     *
     * @param cutoff accounts CREATED strictly before this — {@code createdDate}, which is what the
     *               generated query reads — are deleted.
     * @return how many rows were deleted — rows that <em>changed</em>, never rows that matched, which
     *         is the rule an erasure receipt is held to (D39) and is worth the same here: this number
     *         is the only record that the deletion happened.
     */
    long deleteRegisteredBefore(Instant cutoff) {
        Long deleted = userRepository
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(cutoff)
            .flatMap(user -> userRepository.delete(user).thenReturn(user))
            .count()
            .block();
        return deleted == null ? 0 : deleted;
    }
}
