package net.jojoaddison.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.Dispute;
import net.jojoaddison.repository.DisputeSlaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Says when a dispute has missed the five working days the prototype promises — {@code decisions.md}
 * D96, backlog NEW-52.
 *
 * <h2>The promise, and what was behind it</h2>
 *
 * <p>{@link DisputeWorkflow} stamps {@code dueBy} on every dispute it raises, from
 * {@code healthconnect.disputes.working-days-to-resolve} (5), and its javadoc said plainly that the
 * field was <em>"recorded and not enforced — there is no scheduler anywhere in this estate, so
 * nothing can escalate when it expires; the desk can sort by it, and that is all."</em> The first half
 * was true and the second was not: {@code @EnableScheduling} has been live here since the estate's
 * first boot (D91). So the column existed, the deadline was computed correctly, and nothing ever read
 * it back.
 *
 * <h2>It NOTIFIES. It does not mutate, escalate, or resolve anything</h2>
 *
 * <p>This is the whole of its scope and the reason it is a separate class from {@link RetentionSweep}
 * rather than a second method on it. It reads two columns off each overdue dispute and writes one log
 * line. It does not change {@code DisputeStatus} — no transition into an "overdue" state exists and
 * inventing one would put a value in an append-only audit trail that no desk decision produced. It
 * writes no {@code DisputeStatusChange}, because that table records <strong>acts</strong> (D34/D39)
 * and a deadline passing is not an act anybody took. And it is not {@code @Transactional}: there is
 * nothing to commit.
 *
 * <h2>Who is notified is a DECISION NOBODY HAS TAKEN, and the log is the interim answer</h2>
 *
 * <p>This is stated rather than quietly resolved, because the honest options all needed somebody
 * else's decision and the cheapest one is not obviously right — backlog NEW-69:
 *
 * <ul>
 *   <li><strong>The brokerage</strong> is who the promise binds, and this estate cannot name them. The
 *       desk is {@code ROLE_BROKERAGE}, an authority granted in the <em>gateway's</em> account store;
 *       booking holds no list of its holders and has no business acquiring one. Messaging's
 *       notification rows are keyed by <em>login</em>, so there is no recipient to write.
 *   <li><strong>An outbox event</strong> would reach messaging and be dropped by its
 *       {@code default -> LOG.debug("no notification defined for {}")} arm — a published event nothing
 *       consumes, which is precisely the silent nothing this repository keeps finding. Adding a
 *       consumer needs a recipient, which is the same question again.
 *   <li><strong>The customer</strong> could be told their dispute is late. That is a product decision
 *       with a commercial edge — telling somebody you have missed your own commitment — and it is not
 *       one to take inside a sweep.
 * </ul>
 *
 * <p>So the recipient is the estate's log, which is a real reader: the line is at <strong>WARN</strong>
 * and carries a count and the references, so it is greppable and alertable by whoever runs the box.
 *
 * <h2>WARN and deliberately not ERROR</h2>
 *
 * <p>{@code quality/compose.yml} rests an argument on payout and its siblings carrying <strong>zero
 * ERROR lines</strong> across their whole life — *"the estate's one free signal"*, and the only way an
 * unattached OTel agent or a dead collector is visible at all (D64, D73). An overdue dispute is a
 * normal operational fact on a busy marketplace, so logging it at ERROR would spend that signal on
 * routine business, every morning, for ever. The generated {@code LoggingAspect} was doing exactly
 * that by accident — backlog NEW-65, closed by D97, which states the rule this line already followed:
 * an ERROR is a fact about this estate that is wrong and that nobody chose, and everything else is a
 * WARN. This is that mistake declined on purpose, one package before the rule existed.
 *
 * <h2>No customer text and no login ever reaches the line</h2>
 *
 * <p>Only {@code reference} and {@code dueBy}. {@code Dispute.reason} is up to a thousand characters
 * the customer typed about what went wrong, and {@code raisedByLogin} identifies them — a log is a
 * place the erasure sweep does not reach and cannot re-key, so an identity written there survives the
 * erasure of the row it came from (D39's rule, one destination along). The repository hands over whole
 * entities because {@code dueBy} is needed; the restraint is here, at the site, because nothing about
 * that signature enforces it.
 */
@Component
public class DisputeSlaSweep implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(DisputeSlaSweep.class);

    /**
     * How many references the WARN line names before it abbreviates — see the comment at the log site.
     *
     * <p>Twelve is a judgement and not a measurement: enough that a normal morning's list is complete,
     * short enough that a pathological one stays one readable line. {@link Overdue#references()} is
     * always complete regardless.
     */
    private static final int REFERENCES_IN_THE_LOG = 12;

    private final DisputeSlaRepository disputes;

    private final DisputeSlaProperties properties;

    public DisputeSlaSweep(DisputeSlaRepository disputes, DisputeSlaProperties properties) {
        this.disputes = disputes;
        this.properties = properties;
    }

    /**
     * Registers the sweep, or deliberately registers nothing.
     *
     * <p>Both values are read here, so a cron this estate cannot parse fails at startup rather than at
     * 07:00 — {@code AccountRetention}'s rule (D94), and it applies to a harmless task for the
     * uninteresting reason that a scheduled task which silently never runs is indistinguishable from
     * one that finds nothing.
     *
     * <p><strong>Disabled is a WARN here and an INFO in {@link RetentionSweep}, and the asymmetry is
     * deliberate</strong> — stated because it looks like an inconsistency somebody would tidy. A
     * disabled retention sweep is this estate's <em>default and recommended</em> state, so announcing
     * it at WARN would train people to ignore a level that elsewhere means something. A disabled
     * dispute sweep is the opposite: it returns the estate to having a customer-facing promise that
     * <em>nothing whatsoever</em> reads back, which is the condition NEW-52 existed to end, so it
     * deserves a nag for as long as it lasts. The level tracks "is this state one somebody should be
     * reminded of", not "is this switch off".
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!properties.overdueSweepEnabled()) {
            LOG.warn(
                "disputes: no overdue sweep registered — healthconnect.disputes.overdue-sweep-enabled is false, so " +
                    "dueBy is recorded and nothing reads it back, which is what the estate did before decisions.md D96"
            );
            return;
        }
        String cron = properties.overdueSweepCron();
        LOG.info("disputes: overdue disputes are reported on '{}' (decisions.md D96)", cron);
        registrar.addCronTask(this::sweep, cron);
    }

    /**
     * One run, against the current instant.
     *
     * <p>Package-private, and nothing exposes it over HTTP. Unlike the retention sweep there would be
     * no harm in an endpoint — it changes nothing — but the desk's own {@code GET /api/desk/disputes}
     * is where a person should read this, and an endpoint that logs would be a way to write to the
     * estate's log from outside it.
     */
    Overdue sweep() {
        return sweepAsAt(Instant.now());
    }

    /**
     * The run itself, taking the instant as a parameter so a test can put the boundary exactly on the
     * deadline rather than near it.
     *
     * @param now disputes whose {@code dueBy} is strictly before this are overdue.
     * @return the count and the references, so a caller can assert on what was reported rather than on
     *         a log line.
     */
    Overdue sweepAsAt(Instant now) {
        List<Dispute> late = disputes.overdueAt(now);
        if (late.isEmpty()) {
            LOG.debug("disputes: none has missed its resolution deadline as at {}", now);
            return new Overdue(0, List.of());
        }
        List<String> references = late.stream().map(Dispute::getReference).toList();
        /* THE LINE IS CAPPED AND THE RECORD IS NOT. A backlog of four hundred overdue disputes would
           otherwise put four hundred references on one line, which stops being greppable at exactly
           the moment somebody needs to read it — and the count, which is the number that matters, ends
           up at the far left of a wrapped wall. `Overdue.references()` still carries every one, so a
           caller (a desk screen, NEW-69) loses nothing; only the log line is abbreviated, and it says
           so rather than appearing to be the whole list. */
        String shown = references.size() <= REFERENCES_IN_THE_LOG
            ? references.toString()
            : "%s … and %d more".formatted(references.subList(0, REFERENCES_IN_THE_LOG), references.size() - REFERENCES_IN_THE_LOG);
        /* The oldest is called out beside the count because a list of twelve references says how many
           and not how bad. Whole days, from the earliest deadline — the query orders by dueBy, so the
           first row is the worst one. */
        long worstDays = Duration.between(late.get(0).getDueBy(), now).toDays();
        /* THE WINDOW IS NOT NAMED IN THIS LINE, deliberately. `working-days-to-resolve` is
           configurable and is applied when a dispute is RAISED, so each of these rows carries its own
           deadline from whatever the setting was that day — printing today's number beside them would
           state a period that is not the one any of them was judged against. `dueBy` is the authority
           and the overdue-by figure is derived from it. */
        LOG.warn(
            "disputes: {} unresolved dispute(s) are past their recorded resolution deadline — the oldest by {} day(s). " +
                "References: {}. Nothing has been changed; this is a report (decisions.md D96, backlog NEW-69)",
            references.size(),
            worstDays,
            shown
        );
        return new Overdue(references.size(), references);
    }

    /**
     * @param count how many unresolved disputes are past {@code dueBy}.
     * @param references their references, oldest deadline first. Deliberately references and never
     *     {@code reason} or {@code raisedByLogin} — see the class comment; a reference names a row
     *     without naming a person, which is what a reader of this needs and all of it.
     */
    public record Overdue(int count, List<String> references) {}
}
