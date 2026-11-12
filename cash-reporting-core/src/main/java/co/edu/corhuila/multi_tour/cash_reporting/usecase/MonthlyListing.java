package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * The one reading of the periods that the report, the consolidation and the summary share (RN-12, AC-03.8): newest first,
 * with the period of the report date always present, in zeros when nothing was recorded in it (RN-04, DP-RM1). The report
 * date is the calendar day of the tenant, so the zero period uses the zone in force (AC-03.16).
 */
final class MonthlyListing {

    private MonthlyListing() {}

    static ReportedPeriod current(TenantCalendar calendar, String tenantId) {
        return ReportedPeriod.of(calendar.today(tenantId));
    }

    static Page<MonthlyPeriod> page(
            MonthlyReportSource source, TenantCalendar calendar, String tenantId, PageRequest request) {
        ReportedPeriod current = current(calendar, tenantId);
        boolean missing = source.find(tenantId, current).isEmpty();
        long newer = missing ? source.countNewerThan(tenantId, current) : -1;
        long total = source.count(tenantId) + (missing ? 1 : 0);

        long from = Math.min(request.offset(), total);
        long to = Math.min(from + request.limit(), total);
        boolean zeroInside = missing && newer >= from && newer < to;
        long storedBefore = missing && newer < from ? from - 1 : from;
        int storedWanted = (int) (to - from) - (zeroInside ? 1 : 0);

        List<MonthlyPeriod> items =
                storedWanted == 0 ? new ArrayList<>() : new ArrayList<>(source.newestFirst(tenantId, (int) storedBefore, storedWanted));
        if (zeroInside) {
            items.add((int) (newer - from), MonthlyPeriod.zero(current));
        }
        return new Page<>(items, request.page(), request.limit(), total);
    }

    /** The figures of a period: the stored row, or zeros when the period has none. */
    static MonthlyPeriod of(MonthlyReportSource source, String tenantId, ReportedPeriod period) {
        return source.find(tenantId, period).orElseGet(() -> MonthlyPeriod.zero(period));
    }
}
