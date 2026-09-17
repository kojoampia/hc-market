package net.jojoaddison;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * No method in the gateway's main sources may carry {@code @Scheduled} — {@code decisions.md} D94,
 * backlog NEW-47.
 *
 * <h2>What this is guarding, and why a regeneration is the threat</h2>
 *
 * <p>JHipster generates {@code UserService.removeNotActivatedUsers} with
 * {@code @Scheduled(cron = "0 0 1 * * ?")} over a hard-coded three days. D94 moved that policy into
 * {@code AccountRetention} and the schedule into {@code UnactivatedAccountSweep}, which registers a
 * cron task through {@code SchedulingConfigurer} and carries no annotation.
 *
 * <p>The removal of an annotation from a <em>generated</em> file is the least durable edit in this
 * repository: {@code jhipster jdl --force} silently discards every edit to a generated file, and the
 * symptom of losing this one is not a failure. The estate would acquire a <strong>second</strong>
 * sweep, on a hard-coded three days, deleting accounts early on any estate that had configured
 * longer — with the configured sweep still running, still logging, and still reporting the operator's
 * window at {@code /management/info}. Every test in the gateway would stay green and
 * {@code docs/privacy-notice.md} §7.1 would state a period the estate does not keep.
 *
 * <p>An ArchUnit rule over the whole main tree is the only form of this check that cannot be
 * out-of-date: it names no file and no method, so a generated schedule reappearing anywhere — in
 * {@code UserService}, or in a class that does not exist yet — is red. CI carries the same demand
 * textually (<em>"The unactivated-account sweep must be the estate's only one"</em>) because a
 * regeneration is exactly the moment somebody is running a generator rather than a test suite.
 *
 * <p>It is deliberately an absolute ban rather than an allow-list of one. The estate has one piece of
 * scheduled work in this service and the next one is a decision, not a convenience: the reason this
 * item exists at all is that scheduled deletion of personal data was arrived at by nobody choosing
 * anything.
 */
@AnalyzeClasses(packagesOf = HealthconnectGatewayApp.class, importOptions = DoNotIncludeTests.class)
class ThereIsOneAccountSweepTest {

    @ArchTest
    static final ArchRule scheduledWorkIsDeclaredInOnePlace = noMethods()
        .should()
        .beAnnotatedWith(Scheduled.class)
        .because(
            "the gateway's only scheduled work is UnactivatedAccountSweep, which registers its task through " +
                "SchedulingConfigurer so that the window and the cron come from configuration (decisions.md D94). " +
                "An @Scheduled here is almost certainly JHipster's generated UserService.removeNotActivatedUsers " +
                "coming back on a regeneration, which gives this estate a second sweep on a hard-coded three days " +
                "while the configured one keeps reporting the operator's window"
        );
}
