package net.jojoaddison.service.dto;

import java.util.List;

/**
 * The earnings payload — spec §6, {@code GET /api/pro/earnings}.
 *
 * <p><strong>Contract rule.</strong> Chart endpoints return the <em>rows</em>, not a rendered
 * series. The client draws either the chart or the table view from this same payload, which is
 * exactly how the prototype's chart/table toggle stays honest: two views of one array cannot
 * disagree, whereas a chart series computed separately from a table can.
 *
 * <p>All money is minor units (pesewas). The 12% brokerage fee is inside {@code grossMinor}, not
 * added to it, so {@code grossMinor - commissionMinor == netMinor} always holds.
 */
public final class EarningsDtos {

    private EarningsDtos() {}

    public record Earnings(
        String professionalLogin,
        List<MonthRow> months,
        Totals lifetime,
        MonthToDate monthToDate,
        long averageSessionValueMinor,
        List<Slice> byDeliveryMode,
        List<Slice> byService
    ) {}

    /** One month. {@code month} is {@code YYYY-MM}, so it sorts lexicographically across years. */
    public record MonthRow(String month, long sessions, long grossMinor, long commissionMinor, long netMinor) {}

    public record Totals(long sessions, long grossMinor, long commissionMinor, long netMinor) {}

    /**
     * Month-to-date, and the <strong>same slice of days</strong> in the previous month.
     *
     * <p>Deliberately its own type rather than two {@link Totals}: the comparison is the point, and
     * a caller that has to remember which of two identically-shaped objects is the prior period is
     * a caller that will eventually get it backwards.
     */
    public record MonthToDate(long sessions, long grossMinor, long priorSessions, long priorGrossMinor) {
        /** Percentage change against the like-for-like prior slice, or null when there is no base. */
        public Double changePct() {
            if (priorGrossMinor == 0) {
                return null;
            }
            return Math.round(((double) (grossMinor - priorGrossMinor) / priorGrossMinor) * 1000d) / 10d;
        }
    }

    /** A breakdown bucket — by delivery format, or by service. {@code label} is null for formats. */
    public record Slice(String key, String label, long sessions, long grossMinor) {}
}
