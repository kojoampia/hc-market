package net.jojoaddison.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jojoaddison.domain.AvailabilityOverride;
import net.jojoaddison.domain.AvailabilityRule;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.AvailabilityOverrideQueryRepository;
import net.jojoaddison.repository.AvailabilityRuleQueryRepository;
import net.jojoaddison.repository.AvailabilitySlotQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns recurrence rules into the bookable {@link AvailabilitySlot} rows a customer sees.
 *
 * <h2>Why slots stay rows</h2>
 *
 * <p>decisions.md D20. A professional thinks in rules — "Tuesdays and Thursdays, 07:00 to 11:00,
 * hourly" — but {@code taken} needs a <em>row</em> to lock. Two customers booking the same 07:00
 * must collide on {@code ux_availability_slot_professional_date_time}; with availability computed at
 * read time there is nothing to contend on and the double booking is silent. The rule is what a
 * professional edits; the slot is what a customer books.
 *
 * <h2>What this will not do</h2>
 *
 * <p><strong>It never removes a slot that is taken.</strong> A booked appointment is a commitment to
 * a customer, and deleting it by editing working hours would cancel someone's session without
 * telling them. Removing a booked slot is a cancellation and cancellations go through the booking
 * service, where they raise an event. {@code ProWorkspaceResource.setAvailability} already refuses
 * for the same reason; generation must not be a way around it.
 *
 * <h2>Known limitation, stated rather than hidden</h2>
 *
 * <p>Generation is <strong>additive</strong> on ordinary days: it creates slots the rules justify
 * and leaves everything else alone. It removes untaken slots only where an override says the day is
 * closed, or where an override replaces the day's hours.
 *
 * <p>The consequence is that a hand-edited day — {@code PUT /api/pro/availability} — is authoritative
 * only until the next generation run, which will re-add the rule's slots for that day. Making a day
 * genuinely different means recording an override. Doing better needs an origin marker on the slot
 * so generated and hand-added rows can be told apart, which is a schema change and a separate piece
 * of work; adding it silently here would mean guessing which of two sources of truth wins, and
 * guessing wrong deletes a professional's working day.
 */
@Service
public class AvailabilityPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityPlanner.class);

    /** Used when an override opens a window on a day no rule covers, so there is no interval to borrow. */
    private static final int FALLBACK_SLOT_MINUTES = 60;

    private final AvailabilityRuleQueryRepository rules;
    private final AvailabilityOverrideQueryRepository overrides;
    private final AvailabilitySlotQueryRepository slots;
    private final int defaultHorizonWeeks;

    public AvailabilityPlanner(
        AvailabilityRuleQueryRepository rules,
        AvailabilityOverrideQueryRepository overrides,
        AvailabilitySlotQueryRepository slots,
        @Value("${healthconnect.availability.horizon-weeks:8}") int defaultHorizonWeeks
    ) {
        this.rules = rules;
        this.overrides = overrides;
        this.slots = slots;
        this.defaultHorizonWeeks = defaultHorizonWeeks;
    }

    public int defaultHorizonWeeks() {
        return defaultHorizonWeeks;
    }

    /** What a generation run did. Returned to the professional so the effect is visible, not guessed at. */
    public record Generated(LocalDate from, LocalDate to, int created, int removed, int daysClosed, int keptBecauseTaken) {}

    /**
     * Materialises {@code from}..{@code to} inclusive.
     *
     * <p>Idempotent: re-running over the same window creates nothing new, because every slot it
     * would write already exists and the unique constraint is what makes that true rather than a
     * hopeful check.
     */
    @Transactional
    public Generated generate(Professional professional, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("the end of the window is before its start: %s..%s".formatted(from, to));
        }
        Long proId = professional.getId();

        Map<LocalDate, AvailabilityOverride> overrideByDate = overrides
            .findByProfessionalIdAndOverrideDateBetween(proId, from, to)
            .stream()
            .collect(Collectors.toMap(AvailabilityOverride::getOverrideDate, Function.identity(), (a, b) -> a));

        // One read for the whole window rather than one per day. Existing slots are keyed by
        // (date, time) so "does this already exist" is a set membership test, not a query.
        List<AvailabilitySlot> existing = slots.findByProfessionalIdAndSlotDateBetween(proId, from, to);
        Set<String> present = existing.stream().map(AvailabilityPlanner::key).collect(Collectors.toCollection(HashSet::new));

        int created = 0;
        int removed = 0;
        int daysClosed = 0;
        int keptBecauseTaken = 0;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            AvailabilityOverride override = overrideByDate.get(day);
            List<LocalTime> wanted;

            if (override != null && Boolean.TRUE.equals(override.getClosed())) {
                wanted = List.of();
                daysClosed++;
            } else if (override != null && override.getStartTime() != null && override.getEndTime() != null) {
                wanted = window(override.getStartTime(), override.getEndTime(), intervalFor(proId, day));
            } else {
                wanted = fromRules(proId, day);
            }

            // Replace the day only when an override says so. On an ordinary rule day this is
            // additive -- see the class comment.
            if (override != null) {
                for (AvailabilitySlot slot : slots.findByProfessionalIdAndSlotDate(proId, day)) {
                    if (wanted.contains(slot.getSlotTime())) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(slot.getTaken())) {
                        keptBecauseTaken++;
                        LOG.info(
                            "keeping {} {} for {} — it is booked; closing a day does not cancel an appointment",
                            day,
                            slot.getSlotTime(),
                            professional.getReference()
                        );
                        continue;
                    }
                    slots.delete(slot);
                    present.remove(key(slot));
                    removed++;
                }
            }

            for (LocalTime time : wanted) {
                if (present.add(day + "T" + time)) {
                    slots.save(new AvailabilitySlot().slotDate(day).slotTime(time).taken(false).professional(professional));
                    created++;
                }
            }
        }

        LOG.info(
            "availability for {} {}..{}: {} created, {} removed, {} days closed, {} kept because booked",
            professional.getReference(),
            from,
            to,
            created,
            removed,
            daysClosed,
            keptBecauseTaken
        );
        return new Generated(from, to, created, removed, daysClosed, keptBecauseTaken);
    }

    /** Rules that apply on this weekday, whether or not they are open-ended. */
    private List<LocalTime> fromRules(Long proId, LocalDate day) {
        List<AvailabilityRule> applicable = new ArrayList<>(
            rules.findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilIsNull(proId, day)
        );
        applicable.addAll(rules.findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(proId, day, day));

        List<LocalTime> times = new ArrayList<>();
        for (AvailabilityRule rule : applicable) {
            if (rule.getWeekday() == null || DayOfWeek.valueOf(rule.getWeekday().name()) != day.getDayOfWeek()) {
                continue;
            }
            for (LocalTime t : window(rule.getStartTime(), rule.getEndTime(), rule.getSlotMinutes())) {
                if (!times.contains(t)) {
                    times.add(t); // two rules may overlap; a slot is still one slot
                }
            }
        }
        return times;
    }

    /**
     * An override opening a window borrows the interval from a rule on that weekday, so
     * "same hours, different day" does not silently change the slot length. With no rule to borrow
     * from, an hour.
     */
    private int intervalFor(Long proId, LocalDate day) {
        return rules
            .findByProfessionalIdOrderByWeekdayAscStartTimeAsc(proId)
            .stream()
            .filter(r -> r.getWeekday() != null && DayOfWeek.valueOf(r.getWeekday().name()) == day.getDayOfWeek())
            .map(AvailabilityRule::getSlotMinutes)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(FALLBACK_SLOT_MINUTES);
    }

    /**
     * Slot starts from {@code start} up to but NOT including {@code end}, so a 07:00–11:00 window at
     * 60 minutes is four sessions ending at 11:00 rather than five with one running to midday.
     */
    private static List<LocalTime> window(LocalTime start, LocalTime end, Integer minutes) {
        int step = minutes == null || minutes <= 0 ? FALLBACK_SLOT_MINUTES : minutes;
        List<LocalTime> times = new ArrayList<>();
        if (start == null || end == null || !start.isBefore(end)) {
            return times;
        }
        for (LocalTime t = start; t.isBefore(end); t = t.plusMinutes(step)) {
            // A window ending at midnight would wrap to 00:00 and loop forever; plusMinutes rolls
            // over silently. Guard on the roll rather than trusting the times to be sensible.
            if (!times.isEmpty() && !t.isAfter(times.get(times.size() - 1))) {
                break;
            }
            times.add(t);
        }
        return times;
    }

    public List<AvailabilityRule> rulesOf(Professional professional) {
        return rules.findByProfessionalIdOrderByWeekdayAscStartTimeAsc(professional.getId());
    }

    public List<AvailabilityOverride> overridesOf(Professional professional) {
        return overrides.findByProfessionalIdOrderByOverrideDateAsc(professional.getId());
    }

    public Optional<AvailabilityOverride> overrideOn(Professional professional, LocalDate date) {
        return overrides.findByProfessionalIdAndOverrideDate(professional.getId(), date);
    }

    public AvailabilityRule saveRule(AvailabilityRule rule) {
        return rules.save(rule);
    }

    public void deleteRule(AvailabilityRule rule) {
        rules.delete(rule);
    }

    public AvailabilityOverride saveOverride(AvailabilityOverride override) {
        return overrides.save(override);
    }

    public void deleteOverride(AvailabilityOverride override) {
        overrides.delete(override);
    }

    private static String key(AvailabilitySlot slot) {
        return slot.getSlotDate() + "T" + slot.getSlotTime();
    }
}
