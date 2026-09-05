package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.repository.PayoutQueryRepository;
import net.jojoaddison.service.BookingScheduleClient;
import net.jojoaddison.service.EarningsService;
import net.jojoaddison.service.MarketCalendar;
import net.jojoaddison.service.dto.EarningsDtos.Earnings;
import net.jojoaddison.service.dto.EarningsDtos.MonthToDate;
import net.jojoaddison.service.dto.EarningsDtos.Totals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The "today" the month-to-date slice is bounded by — NEW-10, {@code decisions.md} D51.
 *
 * <p>This ranks below the three writes and is a different kind of wrong: a stored date is wrong for
 * ever, a rendered one for a single request. It matters anyway because it is a <strong>bound on a
 * column written in the marketplace's calendar</strong>. Read in the JVM's zone it slices Accra-dated
 * rows with a European day, so on the first of a month the tile reports the whole of the previous
 * month's last day as this month's — and the lifetime total, which is the only figure anybody has an
 * independent copy of, does not move.
 *
 * <p>Both endpoints that take a "today" are covered, and each from both sides of Accra, in separate
 * tests: an eastward clock alone leaves the westward half unwatched, because a zone ahead of Accra
 * agrees with it at 02:30 UTC by accident.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EarningsAreSlicedOnTheMarketplacesDayTest {

    private static final Instant LATE_EVENING = Instant.parse("2026-09-05T23:30:00Z");
    private static final Instant EARLY_MORNING = Instant.parse("2026-09-05T02:30:00Z");
    private static final LocalDate ACCRAS_DAY = LocalDate.of(2026, 9, 5);

    @Mock
    private EarningsService earnings;

    @Mock
    private EarningsRepository ledger;

    @Mock
    private PayoutQueryRepository payouts;

    @Mock
    private BookingScheduleClient schedule;

    @AfterEach
    void clearTheSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/pro/earnings late in Accra's evening slices from that day, not the next")
    void earningsLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> {
            resource(LATE_EVENING, "Europe/Berlin").earnings(7);
            assertThat(capturedToday()).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("GET /api/pro/earnings early in Accra's morning slices from that day, not the previous one")
    void earningsEarlyMorning() {
        underDefaultZone("America/New_York", () -> {
            resource(EARLY_MORNING, "America/New_York").earnings(7);
            assertThat(capturedToday()).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("GET /api/pro/overview late in Accra's evening slices from that day, not the next")
    void overviewLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> {
            resource(LATE_EVENING, "Europe/Berlin").overview(7, "Bearer irrelevant");
            assertThat(capturedToday()).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("GET /api/pro/overview early in Accra's morning slices from that day, not the previous one")
    void overviewEarlyMorning() {
        underDefaultZone("America/New_York", () -> {
            resource(EARLY_MORNING, "America/New_York").overview(7, "Bearer irrelevant");
            assertThat(capturedToday()).isEqualTo(ACCRAS_DAY);
        });
    }

    // -------------------------------------------------------------- the fixtures --

    private ProEarningsResource resource(Instant instant, String clockZone) {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("akosua.mensah", null, List.of()));
        when(earnings.forProfessional(anyString(), anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(emptyEarnings());
        when(schedule.nextConfirmedDay(anyString())).thenReturn(Optional.empty());
        // The clock's own zone sits on the same side of Accra as the test using it, so an
        // implementation reading the clock's zone rather than the calendar's is caught here too.
        return new ProEarningsResource(earnings, ledger, payouts, schedule, MarketCalendar.at(Clock.fixed(instant, ZoneId.of(clockZone))));
    }

    private LocalDate capturedToday() {
        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        verify(earnings).forProfessional(anyString(), anyInt(), today.capture());
        return today.getValue();
    }

    private static Earnings emptyEarnings() {
        return new Earnings(
            "akosua.mensah",
            List.of(),
            new Totals(0, 0, 0, 0),
            new MonthToDate(0, 0, 0, 0),
            0,
            List.of(),
            List.of()
        );
    }

    private static void underDefaultZone(String zone, Runnable assertion) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            assertion.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
