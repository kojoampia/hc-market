package net.jojoaddison.aop.logging;

import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tech.jhipster.config.JHipsterConstants;

/**
 * Aspect for logging execution of service and repository Spring components.
 *
 * By default, it only runs with the "dev" profile.
 *
 * <p>GENERATED FILE, EDITED — {@code decisions.md} D97, backlog NEW-65. Both advices below log at
 * WARN and the refusal arm echoes no argument; {@code --force} restores {@code log.error} and
 * {@code Arrays.toString(joinPoint.getArgs())} in silence, so the edit is on CLAUDE.md's
 * regeneration table and {@code LoggingAspectRefusalUnitTest} — a new file — is what goes red.
 *
 * <p><strong>Why WARN.</strong> An ERROR line is this estate's one free signal — the only way a dead
 * OTLP collector or an unattached agent is visible at all (D64, D73, and {@code quality/compose.yml}'s
 * note). This aspect sees an exception crossing a boundary and nothing else, so it cannot tell a
 * deliberate refusal from a fault and may not author that signal. The estate's ERROR channel belongs
 * to the code that knows something is wrong.
 *
 * <p>Deliberately <em>no live count is quoted here</em>. The quality estate's ERROR count was zero
 * across every service's whole life when D97 was written and stopped being zero the same day, when a
 * collector blip and a broker wobble produced OTLP exporter and Kafka listener errors (backlog
 * NEW-70). A javadoc asserting a number nothing watches is a javadoc that rots; the argument for the
 * level does not depend on the number being zero today, only on an ERROR line meaning something.
 *
 * <p><strong>Why no arguments.</strong> D44's rule — a message this estate composes, never a value
 * it was handed. {@code PayoutRun.settle} takes a bank reference, and this line printed it.
 */
@Aspect
public class LoggingAspect {

    private final Environment env;

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    /**
     * Pointcut that matches all repositories, services and Web REST endpoints.
     */
    @Pointcut(
        """
        within(@org.springframework.stereotype.Repository *)
        || within(@org.springframework.stereotype.Service *)
        || within(@org.springframework.web.bind.annotation.RestController *)
        """
    )
    public void springBeanPointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Pointcut that matches all Spring beans in the application's main packages.
     */
    @Pointcut(
        """
        within(net.jojoaddison.repository..*)
        || within(net.jojoaddison.service..*)
        || within(net.jojoaddison.web.rest..*)
        """
    )
    public void applicationPackagePointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Retrieves the {@link Logger} associated to the given {@link JoinPoint}.
     *
     * @param joinPoint join point we want the logger for.
     * @return {@link Logger} associated to the given {@link JoinPoint}.
     */
    private Logger logger(JoinPoint joinPoint) {
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringTypeName());
    }

    /**
     * Advice that logs methods throwing exceptions.
     *
     * @param joinPoint join point for advice.
     * @param e exception.
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        // WARN and not ERROR — D97. This arm fires for EVERY Throwable on the pointcut, so it is the
        // one the estate's deliberate refusals actually travel: PayoutRun's NotSettleable and every
        // other IllegalStateException the desk throws never reach the catch below. The dev branch
        // still passes `e`, so the stack trace is not what was traded away; only the level moved.
        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            logger(joinPoint).warn(
                "Exception in {}() with cause = '{}' and exception = '{}'",
                joinPoint.getSignature().getName(),
                e.getCause() != null ? e.getCause() : "NULL",
                e.getMessage(),
                e
            );
        } else {
            // Unreachable while LoggingAspectConfiguration keeps @Profile("dev") on the bean: with
            // the bean present, `dev` is active, so the test above cannot be false. Kept as
            // generated, and at WARN, because it becomes live the moment that annotation goes.
            logger(joinPoint).warn(
                "Exception in {}() with cause = {}",
                joinPoint.getSignature().getName(),
                e.getCause() != null ? String.valueOf(e.getCause()) : "NULL"
            );
        }
    }

    /**
     * Advice that logs when a method is entered and exited.
     *
     * @param joinPoint join point for advice.
     * @return result.
     * @throws Throwable throws {@link IllegalArgumentException}.
     */
    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        var log = logger(joinPoint);
        if (log.isDebugEnabled()) {
            log.debug("Enter: {}() with argument[s] = {}", joinPoint.getSignature().getName(), Arrays.toString(joinPoint.getArgs()));
        }
        try {
            Object result = joinPoint.proceed();
            if (log.isDebugEnabled()) {
                log.debug("Exit: {}() with result = {}", joinPoint.getSignature().getName(), result);
            }
            return result;
        } catch (IllegalArgumentException e) {
            // The method and the reason this estate composed, and nothing it was handed — D97/D44.
            // `Arrays.toString(joinPoint.getArgs())` was here and put a bank reference in the log by
            // this route; the debug line above may render the arguments because it is off unless
            // somebody asks for it, and this one is not.
            log.warn("Refused in {}(): {}", joinPoint.getSignature().getName(), e.getMessage());
            throw e;
        }
    }
}
