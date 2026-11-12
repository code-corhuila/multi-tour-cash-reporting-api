package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.PeriodSummary;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public final class GetMonthlyReportSummary {

    public GetMonthlyReportSummary(CashPermissions permissions, MonthlyReportSource source, TenantCalendar calendar) {}

    public PeriodSummary execute(Actor actor, ReportedPeriod period) {
        throw new UnsupportedOperationException();
    }
}
