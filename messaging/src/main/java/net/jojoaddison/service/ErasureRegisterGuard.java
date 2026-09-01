package net.jojoaddison.service;

import net.jojoaddison.repository.ErasedSubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Refuses to start an unpeppered messaging service that has already erased somebody —
 * {@code decisions.md} D35.
 *
 * <h2>Why this exists in messaging and nowhere else</h2>
 *
 * <p>{@link SubjectPseudonym} argues at length that a missing pepper should not stop a service
 * starting: it degrades one desk endpoint, and taking three services down over it is a worse outcome
 * than a 503 on the one operation that must not proceed. That argument holds while nobody has been
 * erased. It stops holding the moment somebody has.
 *
 * <p>Messaging is the only service holding a register of erased subjects ({@code ErasedSubject}, D32),
 * and that register is what {@code BookingEventConsumer} consults before it writes a login. Run it
 * without the pepper and {@code isErased} answers {@code false} for everybody — including people it
 * erased — so a booking event arriving for an erased customer writes their real login into a fresh
 * conversation and a fresh notification. That is byte for byte the failure D32 exists to close,
 * arriving through a configuration mistake instead of a race, and it is silent: the service is
 * healthy, the consumer is making progress, and the rows look ordinary.
 *
 * <p>So the rule is narrow. <strong>Empty register and no pepper: start.</strong> Nothing can have
 * been erased, the desk answers 503, and the estate is merely missing a variable.
 * <strong>Non-empty register and no pepper: refuse.</strong> There is no safe way to run, and the
 * operator is told which variable and why, at the one moment they are watching a deploy.
 *
 * <p>The same refusal catches the other half of D35's migration note — a pepper that has
 * <em>changed</em> is indistinguishable from one that is absent, as far as the existing rows are
 * concerned, and this only detects the absent case. A changed pepper still needs the register cleared
 * deliberately, which is why D35 records it rather than leaving it to be found.
 *
 * <p>An {@code ApplicationRunner}, so a failure aborts startup and closes the context rather than
 * logging into a service that then serves. Liquibase is not asynchronous here
 * ({@code application.liquibase.async-start: false}), so the table exists by the time this runs — the
 * same ordering {@code SeedDataLoader} depends on, and the same race if it were ever turned back on.
 */
@Component
public class ErasureRegisterGuard implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ErasureRegisterGuard.class);

    private final ErasedSubjectRepository register;
    private final SubjectPseudonym pseudonyms;

    public ErasureRegisterGuard(ErasedSubjectRepository register, SubjectPseudonym pseudonyms) {
        this.register = register;
        this.pseudonyms = pseudonyms;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (pseudonyms.isConfigured()) {
            return;
        }
        long erased = register.count();
        if (erased > 0) {
            throw new IllegalStateException(
                ("this service has erased %d subject(s) and healthconnect.privacy.pepper is not set. " +
                    "Their aliases cannot be recomputed, so every booking event for an erased customer would " +
                    "store their real login again. Set HEALTHCONNECT_PRIVACY_PEPPER to the estate's value " +
                    "(decisions.md D35)").formatted(erased)
            );
        }
        LOG.warn("privacy: no pepper is set, and nobody has been erased yet — the erasure desk will answer 503 (decisions.md D35)");
    }
}
