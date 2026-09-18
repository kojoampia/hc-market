package net.jojoaddison.aop.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * What an ERROR line means in this estate, asserted against the aspect that used to author one for
 * every refusal — {@code decisions.md} D97, backlog NEW-65.
 *
 * <p><strong>This file is byte-identical in all five services and CI diffs the copies.</strong>
 * {@code LoggingAspect} is a <em>generated</em> file, so {@code jhipster jdl --force} restores its
 * {@code log.error} and its {@code Arrays.toString(joinPoint.getArgs())} in silence; this test is a
 * new file, survives that regeneration, and is then the only thing in the estate that goes red. The
 * CI grep beside it reads the source; this reads the behaviour, and neither covers the other — a
 * grep cannot see a level decided at run time and a test cannot see a fifth service nobody wrote one
 * for.
 *
 * <p><strong>Why WARN rather than ERROR.</strong> An ERROR line on the quality box is the estate's one
 * free signal — the only way a dead collector or an unattached agent is visible at all (D64, D73, and
 * {@code quality/compose.yml}'s own note). This aspect cannot tell a refusal from a fault: it sees an
 * exception crossing a boundary and nothing else. So it may not author that signal, and it logs at
 * WARN on every arm. The estate's ERROR channel stays with the code that knows something is wrong.
 *
 * <p>No live count is quoted here on purpose. The count was zero across every service's whole life
 * when D97 was written and moved the same day — a collector blip and a broker wobble, neither of them
 * an application fault and neither of them noticed by anything (backlog NEW-70). The argument for the
 * level rests on an ERROR line <em>meaning</em> something, not on today's number being zero.
 *
 * <p><strong>Why the arguments are gone.</strong> D44's rule, one service along: a message this
 * estate composes, never a value it was handed. {@code PayoutRun.settle} takes a bank reference, so
 * {@code Arrays.toString(joinPoint.getArgs())} put one in the log by this route — which is why the
 * fixture below is bank-reference shaped rather than a neutral string.
 */
class LoggingAspectRefusalUnitTest {

    /**
     * The type the advised method pretends to be declared on. It decides the logger's name, and it is
     * under {@code net.jojoaddison} on purpose: every service's {@code src/test/resources/logback.xml}
     * pins that package at INFO, so WARN is enabled and a WARN assertion is not silently vacuous.
     */
    private static final String ADVISED_TYPE = "net.jojoaddison.service.PretendRefusingService";

    /**
     * A value that must never reach a log line. Shaped like the third argument of
     * {@code PayoutRun.settle}, which is the site D95's desk actually refuses at.
     */
    private static final String BANK_REFERENCE = "GCB-TRF-99881726";

    private final LoggingAspect devAspect = new LoggingAspect(environment("dev"));
    private final LoggingAspect prodAspect = new LoggingAspect(environment("prod"));

    @Test
    @DisplayName("a deliberate refusal is a WARN, and never an ERROR")
    void aRefusalIsAWarning() throws Throwable {
        IllegalArgumentException refusal = new IllegalArgumentException("a settlement needs the day the transfer was made");
        ProceedingJoinPoint joinPoint = throwing(refusal, "PAY-202602-p1-01", BANK_REFERENCE);

        List<ILoggingEvent> heard = whileListening(() -> assertThatThrownBy(() -> devAspect.logAround(joinPoint)).isSameAs(refusal));

        assertThat(heard).hasSize(1);
        assertThat(heard.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(heard).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("the refusal line names the method and the reason, and echoes no argument")
    void theRefusalLineEchoesNothingItWasHanded() throws Throwable {
        IllegalArgumentException refusal = new IllegalArgumentException("a settlement needs the bank's own reference for the transfer");
        ProceedingJoinPoint joinPoint = throwing(refusal, "PAY-202602-p1-01", BANK_REFERENCE);

        List<ILoggingEvent> heard = whileListening(() -> assertThatThrownBy(() -> devAspect.logAround(joinPoint)).isSameAs(refusal));

        String line = heard.get(0).getFormattedMessage();
        assertThat(line).contains("settle").contains(refusal.getMessage());
        // The whole point of the item. Asserted on the bank reference AND on the rendered argument
        // array, because dropping `Arrays.toString` while keeping `getArgs()[0]` would satisfy one.
        assertThat(line).doesNotContain(BANK_REFERENCE).doesNotContain("PAY-202602-p1-01").doesNotContain("[");
    }

    @Test
    @DisplayName("a refusal propagates unchanged — the aspect logs, it does not swallow or wrap")
    void theRefusalStillPropagates() throws Throwable {
        IllegalArgumentException refusal = new IllegalArgumentException("periodStart 2026-03-01 is after periodEnd 2026-02-01");
        ProceedingJoinPoint joinPoint = throwing(refusal, "p1");

        whileListening(() ->
            assertThatThrownBy(() -> devAspect.logAround(joinPoint)).isSameAs(refusal).hasMessage(refusal.getMessage()).hasNoCause()
        );
    }

    @Test
    @DisplayName("a method that returns is not logged at all above DEBUG")
    void theQuietPathStaysQuiet() throws Throwable {
        ProceedingJoinPoint joinPoint = returning("a batch view");

        List<ILoggingEvent> heard = whileListening(() -> {
            try {
                assertThat(devAspect.logAround(joinPoint)).isEqualTo("a batch view");
            } catch (Throwable thrown) {
                throw new AssertionError(thrown);
            }
        });

        assertThat(heard).isEmpty();
    }

    @Test
    @DisplayName("an exception this estate did NOT mean is still reported, and also at WARN")
    void anUnexpectedExceptionIsStillReported() {
        // Not an IllegalArgumentException, so `logAround` does not catch it at all — the advice that
        // reports it is `logAfterThrowing`, which fires for every Throwable on the same pointcut.
        // Both arms are WARN for one reason: neither can tell a refusal from a fault.
        IllegalStateException fault = new IllegalStateException(
            "could not handle booking event: connection reset",
            new RuntimeException("reset")
        );

        List<ILoggingEvent> heard = whileListening(() -> devAspect.logAfterThrowing(joinPointOf(signature()), fault));

        assertThat(heard).hasSize(1);
        assertThat(heard.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(heard.get(0).getFormattedMessage()).contains("settle").contains(fault.getMessage());
        // The dev arm still attaches the throwable, so nothing diagnostic was traded for the level.
        assertThat(heard.get(0).getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("the not-dev arm of logAfterThrowing is a WARN too")
    void theOtherArmIsAWarningAsWell() {
        // Unreachable in this estate as long as `LoggingAspectConfiguration` keeps its
        // `@Profile("dev")` on the bean — with the bean present, `dev` is active by definition, so
        // `acceptsProfiles(Profiles.of("dev"))` cannot be false. It is asserted anyway because it
        // becomes live the moment somebody drops that annotation, and because a level left at ERROR
        // in a branch nobody reads is exactly how this item was found.
        IllegalStateException fault = new IllegalStateException("no BrokerageConfig in force");

        List<ILoggingEvent> heard = whileListening(() -> prodAspect.logAfterThrowing(joinPointOf(signature()), fault));

        assertThat(heard).hasSize(1);
        assertThat(heard.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    private static Signature signature() {
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn(ADVISED_TYPE);
        when(signature.getName()).thenReturn("settle");
        return signature;
    }

    private static JoinPoint joinPointOf(Signature signature) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private static ProceedingJoinPoint throwing(Throwable toThrow, Object... args) throws Throwable {
        // The signature is built BEFORE the `when(...)` it is handed to. Creating a mock inside an
        // unfinished stubbing makes Mockito report `UnfinishedStubbing` against this line, which
        // reads as a defect in the aspect rather than in the harness.
        Signature signature = signature();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenThrow(toThrow);
        return joinPoint;
    }

    private static ProceedingJoinPoint returning(Object result) throws Throwable {
        Signature signature = signature();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { BANK_REFERENCE });
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    /**
     * Collects what the advised type's logger hears while the given work runs.
     *
     * <p>Attached to that one logger — the aspect logs to
     * {@code joinPoint.getSignature().getDeclaringTypeName()}, not to its own class — and detached in
     * a {@code finally}, so a failure inside the block cannot leave it attached for the rest of the
     * suite. The house pattern is {@code PaymentConfigurationUnitTest}'s, in booking.
     */
    private static List<ILoggingEvent> whileListening(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(ADVISED_TYPE);
        ListAppender<ILoggingEvent> heard = new ListAppender<>();
        heard.start();
        logger.addAppender(heard);
        try {
            work.run();
        } finally {
            logger.detachAppender(heard);
            heard.stop();
        }
        return List.copyOf(heard.list);
    }
}
