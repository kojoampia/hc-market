package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.repository.AvailabilitySlotRepository;
import net.jojoaddison.repository.CredentialRepository;
import net.jojoaddison.repository.HighlightRepository;
import net.jojoaddison.repository.MarketplaceQueryRepository;
import net.jojoaddison.repository.ReviewRepository;
import net.jojoaddison.repository.ServiceOfferingRepository;
import net.jojoaddison.service.AvailabilityPlanner;
import net.jojoaddison.service.MarketCalendar;
import net.jojoaddison.service.MarketplaceService;
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
 * Catalog's three defaulted availability windows open on the marketplace's day — NEW-12,
 * {@code decisions.md} D52.
 *
 * <p>The three that rank below {@code Review.publishedOn}: each is a default for "no window given",
 * so each is wrong for one request rather than for ever. They are still worth naming. Read in a zone
 * ahead of Accra late in the evening, the public strip a customer books from and the calendar a
 * professional opens both start on tomorrow and silently drop today's remaining slots; read in a zone
 * behind it, they open on a day that is already over.
 *
 * <p><strong>{@code /availability/generate} is the one that reaches the database</strong>, and it is
 * covered here rather than promoted: its start day is the first day of a window materialised into
 * {@code availability_slot} rows, so a wrong one leaves a professional bookable on a day they did not
 * open. It stays below the review because generation is explicit, idempotent and repeatable — running
 * it again with the right day corrects the calendar, and nothing can correct a published review's
 * date.
 *
 * <p>Every assertion is on the day handed to the collaborator, not on {@link MarketCalendar}, so each
 * is red against a resource holding an injected calendar it does not call. Bracketed from both sides,
 * for D48's review's reason: at 02:30 UTC a Berlin clock says 04:30 the same day and agrees with
 * Accra by accident, so an eastward clock alone watches only half of this.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityWindowsOpenOnTheMarketplacesDayTest {

    /** Dated 2021 deliberately — see {@code MarketCalendarUnitTest}'s class javadoc. */
    private static final Instant LATE_EVENING = Instant.parse("2021-09-05T23:30:00Z");
    private static final Instant EARLY_MORNING = Instant.parse("2021-09-05T02:30:00Z");
    private static final LocalDate ACCRAS_DAY = LocalDate.of(2021, 9, 5);

    @Mock
    private MarketplaceService marketplaceService;

    @Mock
    private MarketplaceQueryRepository marketplace;

    @Mock
    private ServiceOfferingRepository services;

    @Mock
    private AvailabilitySlotRepository slots;

    @Mock
    private ReviewRepository reviews;

    @Mock
    private CredentialRepository credentials;

    @Mock
    private HighlightRepository highlights;

    @Mock
    private AvailabilityPlanner planner;

    @AfterEach
    void clearTheSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ----------------------------------------------- the public ten-day strip, MarketplaceResource --

    @Test
    @DisplayName("late in Accra's evening the public strip still starts on that day")
    void publicStripLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> assertThat(publicStripStart(LATE_EVENING, "Europe/Berlin")).isEqualTo(ACCRAS_DAY));
    }

    @Test
    @DisplayName("early in Accra's morning the public strip still starts on that day")
    void publicStripEarlyMorning() {
        underDefaultZone("America/New_York", () -> assertThat(publicStripStart(EARLY_MORNING, "America/New_York")).isEqualTo(ACCRAS_DAY));
    }

    // ------------------------------------------- the professional's own calendar, ProWorkspaceResource --

    @Test
    @DisplayName("late in Accra's evening the professional's calendar still starts on that day")
    void proCalendarLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> assertThat(proCalendarStart(LATE_EVENING, "Europe/Berlin")).isEqualTo(ACCRAS_DAY));
    }

    @Test
    @DisplayName("early in Accra's morning the professional's calendar still starts on that day")
    void proCalendarEarlyMorning() {
        underDefaultZone("America/New_York", () -> assertThat(proCalendarStart(EARLY_MORNING, "America/New_York")).isEqualTo(ACCRAS_DAY));
    }

    // ------------------------------------------------------- slot generation, ProWorkspaceResource --

    @Test
    @DisplayName("late in Accra's evening generated slots still begin on that day")
    void generateLateEvening() {
        underDefaultZone("Pacific/Kiritimati", () -> assertThat(generateStart(LATE_EVENING, "Europe/Berlin")).isEqualTo(ACCRAS_DAY));
    }

    @Test
    @DisplayName("early in Accra's morning generated slots still begin on that day")
    void generateEarlyMorning() {
        underDefaultZone("America/New_York", () -> assertThat(generateStart(EARLY_MORNING, "America/New_York")).isEqualTo(ACCRAS_DAY));
    }

    // ------------------------------------------------------------------------------------ helpers --

    /** The first day {@code GET /api/professionals/{ref}/availability} asks the service for. */
    private LocalDate publicStripStart(Instant instant, String clockZone) {
        when(marketplaceService.exists("p1")).thenReturn(true);
        when(marketplaceService.availability(anyString(), any(), any())).thenReturn(List.of());

        new MarketplaceResource(marketplaceService, calendarAt(instant, clockZone)).availability("p1", null, null);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(marketplaceService).availability(eq("p1"), from.capture(), any());
        return from.getValue();
    }

    /** The first day {@code GET /api/pro/availability} asks the repository for. */
    private LocalDate proCalendarStart(Instant instant, String clockZone) {
        authenticateAsAProfessional();
        when(marketplace.findOwnedSlots(anyString(), any())).thenReturn(List.of());

        proWorkspace(instant, clockZone).availability(null);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(marketplace).findOwnedSlots(anyString(), from.capture());
        return from.getValue();
    }

    /** The first day {@code POST /api/pro/availability/generate} materialises slots from. */
    private LocalDate generateStart(Instant instant, String clockZone) {
        authenticateAsAProfessional();
        when(planner.defaultHorizonWeeks()).thenReturn(4);
        when(planner.generate(any(Professional.class), any(), any())).thenReturn(
            new AvailabilityPlanner.Generated(ACCRAS_DAY, ACCRAS_DAY, 0, 0, 0, 0)
        );

        proWorkspace(instant, clockZone).generate(null, null);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(planner).generate(any(Professional.class), from.capture(), any());
        return from.getValue();
    }

    private ProWorkspaceResource proWorkspace(Instant instant, String clockZone) {
        return new ProWorkspaceResource(
            marketplace,
            services,
            slots,
            reviews,
            credentials,
            highlights,
            marketplaceService,
            planner,
            calendarAt(instant, clockZone)
        );
    }

    private void authenticateAsAProfessional() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("akosua.mensah", null, List.of()));
        when(marketplace.findByUserLogin("akosua.mensah")).thenReturn(Optional.of(new Professional()));
    }

    /**
     * The clock's own zone sits on the same side of Accra as the test using it, so an implementation
     * reading the clock's zone rather than the calendar's is caught as well as one reading the JVM's.
     */
    private static MarketCalendar calendarAt(Instant instant, String clockZone) {
        return MarketCalendar.at(Clock.fixed(instant, ZoneId.of(clockZone)));
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
