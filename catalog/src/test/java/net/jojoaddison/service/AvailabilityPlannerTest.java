package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import net.jojoaddison.domain.AvailabilityOverride;
import net.jojoaddison.domain.AvailabilityRule;
import net.jojoaddison.domain.AvailabilitySlot;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.Weekday;
import net.jojoaddison.repository.AvailabilityOverrideQueryRepository;
import net.jojoaddison.repository.AvailabilityRuleQueryRepository;
import net.jojoaddison.repository.AvailabilitySlotQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Slot generation — decisions.md D20.
 *
 * <p>Two of these guard promises that are made in prose elsewhere and would otherwise be enforced by
 * nothing: that generation is idempotent, and that it never removes a booked slot. The second is the
 * one that matters. {@code ProWorkspaceResource.setAvailability} already refuses to delete a taken
 * slot because a booked appointment is a commitment to a customer; generation must not become a way
 * around that refusal, and "closing a day" is exactly the operation that would try.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityPlannerTest {

    /** 2026-08-10 is a Monday — the seed's anchor date, so the fixture matches the demo data. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    @Mock
    private AvailabilityRuleQueryRepository rules;

    @Mock
    private AvailabilityOverrideQueryRepository overrides;

    @Mock
    private AvailabilitySlotQueryRepository slots;

    private AvailabilityPlanner planner;
    private Professional pro;

    @BeforeEach
    void setUp() {
        planner = new AvailabilityPlanner(rules, overrides, slots, 8);
        pro = new Professional().reference("p1");
        pro.setId(1L);
        when(overrides.findByProfessionalIdAndOverrideDateBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(slots.findByProfessionalIdAndSlotDateBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(slots.findByProfessionalIdAndSlotDate(anyLong(), any())).thenReturn(List.of());
        when(rules.findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilIsNull(anyLong(), any())).thenReturn(List.of());
        when(
            rules.findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(anyLong(), any(), any())
        ).thenReturn(List.of());
        when(rules.findByProfessionalIdOrderByWeekdayAscStartTimeAsc(anyLong())).thenReturn(List.of());
    }

    private static AvailabilityRule mondayMorning() {
        return new AvailabilityRule()
            .weekday(Weekday.MONDAY)
            .startTime(LocalTime.of(7, 0))
            .endTime(LocalTime.of(11, 0))
            .slotMinutes(60)
            .validFrom(LocalDate.of(2026, 1, 1))
            .active(true);
    }

    private void withOpenEndedRule(AvailabilityRule... rule) {
        when(rules.findByProfessionalIdAndActiveIsTrueAndValidFromLessThanEqualAndValidUntilIsNull(anyLong(), any())).thenReturn(
            List.of(rule)
        );
        when(rules.findByProfessionalIdOrderByWeekdayAscStartTimeAsc(anyLong())).thenReturn(List.of(rule));
    }

    private List<LocalTime> savedTimes() {
        ArgumentCaptor<AvailabilitySlot> saved = ArgumentCaptor.forClass(AvailabilitySlot.class);
        verify(slots, org.mockito.Mockito.atLeast(0)).save(saved.capture());
        return saved.getAllValues().stream().map(AvailabilitySlot::getSlotTime).toList();
    }

    @Test
    @DisplayName("a 07:00-11:00 window at 60 minutes is four sessions, the last starting at 10:00")
    void windowExcludesItsEnd() {
        withOpenEndedRule(mondayMorning());

        var result = planner.generate(pro, MONDAY, MONDAY);

        assertThat(result.created()).isEqualTo(4);
        assertThat(savedTimes()).containsExactly(LocalTime.of(7, 0), LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0));
        // Not 11:00. A session starting at 11:00 runs to midday, past the hours the professional set.
        assertThat(savedTimes()).doesNotContain(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("generating twice creates nothing the second time")
    void isIdempotent() {
        withOpenEndedRule(mondayMorning());
        // The window is already fully materialised, as it would be on a second run.
        when(slots.findByProfessionalIdAndSlotDateBetween(anyLong(), any(), any())).thenReturn(
            List.of(
                slotAt(LocalTime.of(7, 0), false),
                slotAt(LocalTime.of(8, 0), false),
                slotAt(LocalTime.of(9, 0), false),
                slotAt(LocalTime.of(10, 0), false)
            )
        );

        var result = planner.generate(pro, MONDAY, MONDAY);

        assertThat(result.created()).isZero();
        verify(slots, never()).save(any());
    }

    @Test
    @DisplayName("closing a day removes untaken slots and KEEPS booked ones")
    void closingADayNeverCancelsAnAppointment() {
        withOpenEndedRule(mondayMorning());
        when(overrides.findByProfessionalIdAndOverrideDateBetween(anyLong(), any(), any())).thenReturn(
            List.of(new AvailabilityOverride().overrideDate(MONDAY).closed(true).note("public holiday"))
        );
        AvailabilitySlot free = slotAt(LocalTime.of(7, 0), false);
        AvailabilitySlot booked = slotAt(LocalTime.of(8, 0), true);
        when(slots.findByProfessionalIdAndSlotDate(anyLong(), any())).thenReturn(List.of(free, booked));

        var result = planner.generate(pro, MONDAY, MONDAY);

        assertThat(result.daysClosed()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.keptBecauseTaken()).isEqualTo(1);
        verify(slots).delete(free);
        // The whole point: a booked session survives its day being closed. Removing it is a
        // cancellation, and cancellations go through the booking service where they raise an event.
        verify(slots, never()).delete(booked);
        // And nothing is generated for a closed day.
        verify(slots, never()).save(any());
    }

    @Test
    @DisplayName("two overlapping rules produce one slot per time, not two")
    void overlappingRulesDoNotDuplicate() {
        AvailabilityRule second = new AvailabilityRule()
            .weekday(Weekday.MONDAY)
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(12, 0))
            .slotMinutes(60)
            .validFrom(LocalDate.of(2026, 1, 1))
            .active(true);
        withOpenEndedRule(mondayMorning(), second);

        var result = planner.generate(pro, MONDAY, MONDAY);

        // 07,08,09,10 from the first; 09,10,11 from the second. Union is 07,08,09,10,11.
        assertThat(result.created()).isEqualTo(5);
        assertThat(savedTimes()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a rule for another weekday does not generate on this one")
    void weekdayIsRespected() {
        withOpenEndedRule(mondayMorning());

        var result = planner.generate(pro, MONDAY.plusDays(1), MONDAY.plusDays(1)); // Tuesday

        assertThat(result.created()).isZero();
        verify(slots, never()).save(any());
    }

    @Test
    @DisplayName("an override opening different hours borrows the weekday's slot length")
    void overrideBorrowsTheInterval() {
        withOpenEndedRule(mondayMorning()); // 60-minute sessions on Mondays
        when(overrides.findByProfessionalIdAndOverrideDateBetween(anyLong(), any(), any())).thenReturn(
            List.of(new AvailabilityOverride().overrideDate(MONDAY).closed(false).startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(16, 0)))
        );

        var result = planner.generate(pro, MONDAY, MONDAY);

        assertThat(result.created()).isEqualTo(2);
        assertThat(savedTimes()).containsExactly(LocalTime.of(14, 0), LocalTime.of(15, 0));
    }

    @Test
    @DisplayName("a backwards window is refused rather than looping")
    void backwardsWindowIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> planner.generate(pro, MONDAY, MONDAY.minusDays(1))).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    private AvailabilitySlot slotAt(LocalTime time, boolean taken) {
        return new AvailabilitySlot().slotDate(MONDAY).slotTime(time).taken(taken).professional(pro);
    }
}
