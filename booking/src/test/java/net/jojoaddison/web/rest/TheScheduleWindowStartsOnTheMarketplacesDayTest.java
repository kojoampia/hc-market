package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import net.jojoaddison.domain.enumeration.BookingStatus;
import net.jojoaddison.repository.BookingQueryRepository;
import net.jojoaddison.service.BookingMapper;
import net.jojoaddison.service.BookingWorkflow;
import net.jojoaddison.service.MarketCalendar;
import net.jojoaddison.service.dto.BookingDtos.ScheduleDay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The professional's schedule window opens on the marketplace's day — NEW-10, {@code decisions.md}
 * D51.
 *
 * <p>The lowest-ranked of the six sites this package closed: a rendered default, wrong for one
 * request rather than for ever. Read in a zone ahead of Accra late in the evening it silently drops
 * today's remaining appointments from the first screen a professional looks at, and read in a zone
 * behind it, it opens on a day that is already over.
 *
 * <p>Bracketed from both sides in two tests, for the reason D48's review gave: an eastward clock
 * alone would leave the westward half unwatched.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TheScheduleWindowStartsOnTheMarketplacesDayTest {

    private static final Instant LATE_EVENING = Instant.parse("2026-09-05T23:30:00Z");
    private static final Instant EARLY_MORNING = Instant.parse("2026-09-05T02:30:00Z");
    private static final LocalDate ACCRAS_DAY = LocalDate.of(2026, 9, 5);

    @Mock
    private BookingWorkflow bookings;

    @Mock
    private BookingQueryRepository repository;

    @Mock
    private BookingMapper mapper;

    @AfterEach
    void clearTheSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("late in Accra's evening the window still opens on that day, not tomorrow")
    void lateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> {
            List<ScheduleDay> days = resource(LATE_EVENING, "Europe/Berlin").schedule(null, null, null, null);
            assertThat(days).first().extracting(ScheduleDay::date).isEqualTo(ACCRAS_DAY);
        });
    }

    @Test
    @DisplayName("early in Accra's morning the window still opens on that day, not yesterday")
    void earlyMorning() {
        underDefaultZone("America/New_York", () -> {
            List<ScheduleDay> days = resource(EARLY_MORNING, "America/New_York").schedule(null, null, null, null);
            assertThat(days).first().extracting(ScheduleDay::date).isEqualTo(ACCRAS_DAY);
        });
    }

    private ProBookingResource resource(Instant instant, String clockZone) {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("akosua.mensah", null, List.of()));
        when(repository.findByProfessionalLoginAndStatusOrderByScheduledDateAsc(anyString(), any(BookingStatus.class))).thenReturn(List.of());
        // The clock's own zone sits on the same side of Accra as the test using it, so an
        // implementation reading the clock's zone rather than the calendar's is caught here too.
        return new ProBookingResource(bookings, repository, mapper, MarketCalendar.at(Clock.fixed(instant, ZoneId.of(clockZone))));
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
