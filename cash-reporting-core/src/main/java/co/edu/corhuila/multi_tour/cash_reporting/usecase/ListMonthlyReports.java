package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Objects;

/**
 * Lists the monthly report of the tenant of the token, newest period first (AC-03.1, AC-03.2, AC-03.3). It only reads: the
 * figures are the ones the projection holds, never a sum of the daily closings.
 */
public final class ListMonthlyReports {

    private final CashPermissions permissions;
    private final MonthlyReportSource source;
    private final TenantCalendar calendar;

    public ListMonthlyReports(CashPermissions permissions, MonthlyReportSource source, TenantCalendar calendar) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.source = Objects.requireNonNull(source, "source");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public Page<MonthlyPeriod> execute(Actor actor, PageRequest page) {
        CashGuards.requireAllowed(permissions, actor, Operation.LIST_MONTHLY_REPORTS);
        return MonthlyListing.page(source, calendar, actor.tenantId(), page);
    }
}
