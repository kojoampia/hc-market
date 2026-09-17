package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The retention window and the sweep's schedule — {@code decisions.md} D94, backlog NEW-47.
 *
 * <h2>What these assert, and what would make them worthless</h2>
 *
 * <p>Every case here asserts a <strong>bound value</strong>, never that a property exists. A test that
 * asserted "the key is present in application.yml" would pass against a window that binds to nothing,
 * which is the shape three tests written earlier in this project's life had: they were green with and
 * without the thing they existed to prove.
 *
 * <p>Half of them go through a real Spring binder ({@link ApplicationContextRunner}) rather than
 * calling a setter, because the interesting failure is in the <em>name</em>: relaxed binding is what
 * maps {@code HC_UNACTIVATED_ACCOUNT_RETENTION_DAYS} through application.yml's placeholder to
 * {@code healthconnect.accounts.unactivated-retention-days} to
 * {@code setUnactivatedRetentionDays}, and a prefix or a field renamed on one side of that chain
 * leaves a default silently in force. Calling the setter directly cannot see it.
 */
class AccountRetentionUnitTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner().withUserConfiguration(BindAccountRetention.class);

    @Configuration
    @EnableConfigurationProperties(AccountRetention.class)
    static class BindAccountRetention {}

    @Test
    void theDefaultWindowIsThreeDays() {
        // The number that must not move. D91's objection was that an undecided framework default was
        // destroying personal data unrecorded, not that three days is wrong — so an estate that
        // configures nothing has to behave exactly as every estate that has ever run.
        contexts.run(context -> assertThat(context.getBean(AccountRetention.class).unactivatedRetention()).isEqualTo(Duration.ofDays(3)));
    }

    @Test
    void theDefaultCronIsTheGeneratedOne() {
        contexts.run(context -> assertThat(context.getBean(AccountRetention.class).unactivatedSweepCron()).isEqualTo("0 0 1 * * ?"));
    }

    @Test
    void aConfiguredWindowBinds() {
        contexts
            .withPropertyValues("healthconnect.accounts.unactivated-retention-days=14")
            .run(context -> assertThat(context.getBean(AccountRetention.class).unactivatedRetention()).isEqualTo(Duration.ofDays(14)));
    }

    @Test
    void aConfiguredCronBinds() {
        contexts
            .withPropertyValues("healthconnect.accounts.unactivated-sweep-cron=0 */5 * * * *")
            .run(context -> assertThat(context.getBean(AccountRetention.class).unactivatedSweepCron()).isEqualTo("0 */5 * * * *"));
    }

    @Test
    void blankCountsAsAbsentForBoth() {
        // This is what every estate that passes the variables through without setting them actually
        // sends: compose's ${X:-} sets an EMPTY variable, which Spring reads as a value that is
        // present. A typed int field would fail to bind here, and an empty cron is an INVALID cron
        // rather than a missing one.
        contexts
            .withPropertyValues("healthconnect.accounts.unactivated-retention-days=", "healthconnect.accounts.unactivated-sweep-cron=")
            .run(context -> {
                AccountRetention retention = context.getBean(AccountRetention.class);
                assertThat(retention.unactivatedRetention()).isEqualTo(Duration.ofDays(3));
                assertThat(retention.unactivatedSweepCron()).isEqualTo("0 0 1 * * ?");
            });
    }

    @Test
    void aWindowThatIsNotANumberIsRefusedAndNamesTheProperty() {
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedRetentionDays("three");
        assertThatThrownBy(retention::unactivatedRetention)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("healthconnect.accounts.unactivated-retention-days")
            .hasMessageContaining("three");
    }

    @Test
    void zeroDaysIsRefused() {
        // Zero deletes an account registered a second ago on the next sweep. It is one keystroke from
        // a plausible policy and it is not one.
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedRetentionDays("0");
        assertThatThrownBy(retention::unactivatedRetention)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least one day");
    }

    @Test
    void aNegativeWindowIsRefused() {
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedRetentionDays("-1");
        assertThatThrownBy(retention::unactivatedRetention).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnreadableCronIsRefusedAndNamesTheProperty() {
        AccountRetention retention = new AccountRetention();
        retention.setUnactivatedSweepCron("every tuesday");
        assertThatThrownBy(retention::unactivatedSweepCron)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("healthconnect.accounts.unactivated-sweep-cron")
            .hasMessageContaining("every tuesday");
    }

    @Test
    void theDefaultsAreTheGeneratedFiguresAndAreNotRewrittenQuietly() {
        // Pins the two constants themselves, because they are quoted as numbers a data subject reads
        // in docs/privacy-notice.md 7.1 and a regulator reads in docs/processing-record.md 3.1. A
        // change here is a change to both documents and to D94.
        assertThat(AccountRetention.DEFAULT_RETENTION_DAYS).isEqualTo("3");
        assertThat(AccountRetention.DEFAULT_SWEEP_CRON).isEqualTo("0 0 1 * * ?");
    }
}
