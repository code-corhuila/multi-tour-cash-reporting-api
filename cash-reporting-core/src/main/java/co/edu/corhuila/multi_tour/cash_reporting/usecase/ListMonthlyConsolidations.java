package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public final class ListMonthlyConsolidations {

    public ListMonthlyConsolidations(CashPermissions permissions, MonthlyReportSource source, TenantCalendar calendar) {}

    public Page<MonthlyPeriod> execute(Actor actor, PageRequest page) {
        throw new UnsupportedOperationException();
    }
}
