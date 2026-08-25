package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.service.dto.EarningsDtos.Earnings;
import net.jojoaddison.service.dto.EarningsDtos.MonthToDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The earnings screen's arithmetic.
 *
 * <p>The calculation worth testing here is month-to-date against the <strong>same slice of days</strong>
 * in the previous month. It is easy to write, easy to get wrong, and wrong in a way nobody reports:
 * comparing 1–14 August against the whole of July makes a perfectly healthy month read as a 50%
 * collapse, every month, until its final day. The prototype compared like for like; so must this.
 */
@ExtendWith(MockitoExtension.class)
class EarningsServiceTest {

    private static final String LOGIN = "akosua.mensah";

    @Mock
    private EarningsRepository earnings;

    private EarningsService service;

    @BeforeEach
    void setUp() {
        service = new EarningsService(earnings);
    }

    /** count, gross, commission, net — the shape {@code lifetime()} returns. */
    private void stubLifetime(long sessions, long gross, long commission, long net) {
        when(earnings.lifetime(LOGIN)).thenReturn(List.<Object[]>of(new Object[] { sessions, gross, commission, net }));
    }

    /** gross, count — note {@code grossBetween} returns them the other way round. */
    private void stubWindows(Object[] current, Object[] prior) {
        when(earnings.grossBetween(eq(LOGIN), any(), any())).thenReturn(List.<Object[]>of(current)).thenReturn(List.<Object[]>of(prior));
    }

    @Test
    @DisplayName("month-to-date compares against the same slice of days, not the whole prior month")
    void sameSliceOfDays() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        when(earnings.earningsByMonth(LOGIN)).thenReturn(List.of());
        stubLifetime(0, 0, 0, 0);
        stubWindows(new Object[] { 250000L, 10L }, new Object[] { 300000L, 12L });

        service.forProfessional(LOGIN, 7, today);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(earnings, org.mockito.Mockito.times(2)).grossBetween(eq(LOGIN), from.capture(), to.capture());

        assertThat(from.getAllValues().get(0)).as("current month starts on the 1st").isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(to.getAllValues().get(0)).as("current window ends today").isEqualTo(today);
        assertThat(from.getAllValues().get(1)).as("prior month starts on the 1st").isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(to.getAllValues().get(1))
            .as("prior window must stop on the SAME day of the month, not run to the end of July")
            .isEqualTo(LocalDate.of(2026, 7, 14));
    }

    /**
     * 31 March has no counterpart in February. Without a clamp the prior window would overflow into
     * March and compare a month against itself.
     */
    @Test
    @DisplayName("the day is clamped to the shorter previous month")
    void clampsToShorterMonth() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        when(earnings.earningsByMonth(LOGIN)).thenReturn(List.of());
        stubLifetime(0, 0, 0, 0);
        stubWindows(new Object[] { 0L, 0L }, new Object[] { 0L, 0L });

        service.forProfessional(LOGIN, 7, today);

        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(earnings, org.mockito.Mockito.times(2)).grossBetween(eq(LOGIN), any(), to.capture());
        assertThat(to.getAllValues().get(1))
            .as("2026 is not a leap year, so February ends on the 28th")
            .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("month-to-date reports both slices, with gross and count the right way round")
    void monthToDateFieldsAreNotTransposed() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        when(earnings.earningsByMonth(LOGIN)).thenReturn(List.of());
        stubLifetime(0, 0, 0, 0);
        // grossBetween returns (gross, count)
        stubWindows(new Object[] { 250000L, 10L }, new Object[] { 300000L, 12L });

        MonthToDate mtd = service.forProfessional(LOGIN, 7, today).monthToDate();

        assertThat(mtd.grossMinor()).isEqualTo(250000L);
        assertThat(mtd.sessions()).isEqualTo(10L);
        assertThat(mtd.priorGrossMinor()).isEqualTo(300000L);
        assertThat(mtd.priorSessions()).isEqualTo(12L);
    }

    @Test
    @DisplayName("percentage change is signed, rounded to one decimal, and null with no base")
    void changePct() {
        assertThat(new MonthToDate(10, 250000, 12, 300000).changePct()).isEqualTo(-16.7);
        assertThat(new MonthToDate(12, 300000, 10, 250000).changePct()).isEqualTo(20.0);
        assertThat(new MonthToDate(1, 100, 0, 0).changePct())
            .as("dividing by a zero base is undefined, not a 100% rise")
            .isNull();
    }

    @Test
    @DisplayName("the months window takes the most recent N, keeping the current month")
    void windowTakesTheTail() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        when(earnings.earningsByMonth(LOGIN))
            .thenReturn(
                List.<Object[]>of(
                    new Object[] { "2026-02", 31L, 1115000L, 133800L, 981200L },
                    new Object[] { "2026-03", 43L, 1319000L, 158280L, 1160720L },
                    new Object[] { "2026-04", 48L, 1482000L, 177840L, 1304160L },
                    new Object[] { "2026-05", 37L, 1058000L, 126960L, 931040L },
                    new Object[] { "2026-06", 44L, 1563000L, 187560L, 1375440L },
                    new Object[] { "2026-07", 43L, 1375000L, 165000L, 1210000L },
                    new Object[] { "2026-08", 10L, 250000L, 30000L, 220000L }
                )
            );
        stubLifetime(256, 8162000, 979440, 7182560);
        stubWindows(new Object[] { 250000L, 10L }, new Object[] { 300000L, 12L });

        Earnings e = service.forProfessional(LOGIN, 3, today);

        assertThat(e.months()).hasSize(3);
        assertThat(e.months().get(0).month()).isEqualTo("2026-06");
        assertThat(e.months().get(2).month()).as("the current month is always the last row").isEqualTo("2026-08");
    }

    @Test
    @DisplayName("asking for more months than exist returns all of them, not padding")
    void windowLongerThanHistory() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        when(earnings.earningsByMonth(LOGIN)).thenReturn(List.<Object[]>of(new Object[] { "2026-08", 10L, 250000L, 30000L, 220000L }));
        stubLifetime(10, 250000, 30000, 220000);
        stubWindows(new Object[] { 250000L, 10L }, new Object[] { 0L, 0L });

        assertThat(service.forProfessional(LOGIN, 24, today).months()).hasSize(1);
    }

    @Test
    @DisplayName("average session value is gross over sessions, and zero sessions is not a crash")
    void averageSessionValue() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        when(earnings.earningsByMonth(LOGIN)).thenReturn(List.of());
        stubLifetime(256, 8162000, 979440, 7182560);
        stubWindows(new Object[] { 0L, 0L }, new Object[] { 0L, 0L });

        // 8162000 / 256 = 31882.8125 -> 31883
        assertThat(service.forProfessional(LOGIN, 7, today).averageSessionValueMinor()).isEqualTo(31883L);
    }

    @Test
    @DisplayName("a professional with no ledger rows reports zeroes rather than dividing by zero")
    void noEarningsAtAll() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        when(earnings.earningsByMonth(LOGIN)).thenReturn(List.of());
        stubLifetime(0, 0, 0, 0);
        stubWindows(new Object[] { 0L, 0L }, new Object[] { 0L, 0L });

        Earnings e = service.forProfessional(LOGIN, 7, today);

        assertThat(e.lifetime().sessions()).isZero();
        assertThat(e.averageSessionValueMinor()).isZero();
        assertThat(e.months()).isEmpty();
    }
}
