package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import net.jojoaddison.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * That the sweep is actually scheduled, and on the expression the estate configured — the half of
 * {@code decisions.md} D94 that no integration test can see.
 *
 * <h2>Why this asks the registrar</h2>
 *
 * <p>An integration test can watch the sweep delete at the boundary
 * ({@code UnactivatedAccountSweepIT}) and prove nothing at all about whether anything ever calls it.
 * Delete the {@code registrar.addCronTask} line and every other test in this repository stays green
 * while unactivated accounts accumulate for ever — which is the opposite of the defect D94 is about
 * and is just as silent.
 *
 * <p>So this drives {@code configureTasks} with a real {@link ScheduledTaskRegistrar} and reads back
 * the task it registered, and it asserts the <strong>expression</strong> rather than the count: a
 * task registered on the wrong cron is a task nobody notices until the day somebody looks for a
 * deletion that did not happen.
 */
class UnactivatedAccountSweepUnitTest {

    private List<CronTask> registeredTasks(AccountRetention retention) {
        UnactivatedAccountSweep sweep = new UnactivatedAccountSweep(mock(UserRepository.class), retention);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        sweep.configureTasks(registrar);
        return registrar.getCronTaskList();
    }

    @Test
    void theSweepIsRegisteredOnTheDefaultCron() {
        List<CronTask> tasks = registeredTasks(new AccountRetention());
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getExpression()).isEqualTo("0 0 1 * * ?");
    }

    @Test
    void theSweepIsRegisteredOnAConfiguredCron() {
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedSweepCron("0 */5 * * * *");
        assertThat(registeredTasks(retention).get(0).getExpression()).isEqualTo("0 */5 * * * *");
    }

    @Test
    void anUnusableWindowRefusesAtStartupRatherThanAtOneInTheMorning() {
        // Both values are read in configureTasks for this reason: a window that cannot be parsed is a
        // startup failure, not a task that throws at 01:00 on a morning nobody is watching.
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedRetentionDays("not a number");
        assertThatThrownBy(() -> registeredTasks(retention)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnusableCronRefusesAtStartup() {
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedSweepCron("0 0 1 * *");
        assertThatThrownBy(() -> registeredTasks(retention)).isInstanceOf(IllegalStateException.class);
    }
}
