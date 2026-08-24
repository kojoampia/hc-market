package net.jojoaddison.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import net.jojoaddison.repository.EarningsRepository;
import net.jojoaddison.service.dto.EarningsDtos.Earnings;
import net.jojoaddison.service.dto.EarningsDtos.MonthRow;
import net.jojoaddison.service.dto.EarningsDtos.Slice;
import net.jojoaddison.service.dto.EarningsDtos.MonthToDate;
import net.jojoaddison.service.dto.EarningsDtos.Totals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The professional's earnings, assembled entirely from aggregates over the ledger.
 *
 * <p>Nothing here is read from a stored total, because no stored total exists.
 */
@Service
@Transactional(readOnly = true)
public class EarningsService {

    private final EarningsRepository earnings;

    public EarningsService(EarningsRepository earnings) {
        this.earnings = earnings;
    }

    public Earnings forProfessional(String login, int months, LocalDate today) {
        List<MonthRow> all = earnings
            .earningsByMonth(login)
            .stream()
            .map(r -> new MonthRow(str(r[0]), num(r[1]), num(r[2]), num(r[3]), num(r[4])))
            .toList();

        // Trailing window, taken from the end so the current month is always included.
        List<MonthRow> window = all.size() <= months ? all : all.subList(all.size() - months, all.size());

        Object[] life = earnings.lifetime(login).get(0);
        Totals lifetime = new Totals(num(life[0]), num(life[1]), num(life[2]), num(life[3]));

        return new Earnings(
            login,
            window,
            lifetime,
            monthToDate(login, today),
            averageSessionValueMinor(lifetime),
            earnings.byDeliveryMode(login).stream().map(r -> new Slice(str(r[0]), null, num(r[1]), num(r[2]))).toList(),
            earnings.byService(login).stream().map(r -> new Slice(str(r[0]), str(r[1]), num(r[2]), num(r[3]))).toList()
        );
    }

    /**
     * Month-to-date against the <strong>same slice of days</strong> in the previous month.
     *
     * <p>This is the one calculation on this screen that is easy to get subtly wrong and hard to
     * notice: comparing 1–14 August against the whole of July makes a healthy month read as a 50%
     * collapse, every month, until its final day. The prototype compared like for like and so does
     * this.
     *
     * <p>The day is clamped to the previous month's length, so 31 March compares against 28 or 29
     * February rather than overflowing into March.
     */
    private MonthToDate monthToDate(String login, LocalDate today) {
        LocalDate currentFrom = today.withDayOfMonth(1);
        YearMonth previous = YearMonth.from(today).minusMonths(1);
        LocalDate previousFrom = previous.atDay(1);
        LocalDate previousTo = previous.atDay(Math.min(today.getDayOfMonth(), previous.lengthOfMonth()));

        Object[] current = earnings.grossBetween(login, currentFrom, today).get(0);
        Object[] prior = earnings.grossBetween(login, previousFrom, previousTo).get(0);
        return new MonthToDate(num(current[1]), num(current[0]), num(prior[1]), num(prior[0]));
    }

    /** Lifetime gross divided by sessions. Zero sessions yields zero rather than a division fault. */
    private static long averageSessionValueMinor(Totals lifetime) {
        return lifetime.sessions() == 0 ? 0 : Math.round((double) lifetime.grossMinor() / lifetime.sessions());
    }

    private static long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
