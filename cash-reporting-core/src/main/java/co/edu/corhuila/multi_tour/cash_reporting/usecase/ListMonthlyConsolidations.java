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
 * Lists the consolidation of the month cash of the tenant of the token, newest period first (AC-03.7, AC-03.8). It reads the
 * same source as the report, so both list the same periods with the same values (RN-12); the split of the income and the
 * counted movements are in the same {@link MonthlyPeriod}.
 */
public final class ListMonthlyConsolidations {

    private final CashPermissions permissions;
    private final MonthlyReportSource source;
    private final TenantCalendar calendar;

    public ListMonthlyConsolidations(CashPermissions permissions, MonthlyReportSource source, TenantCalendar calendar) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.source = Objects.requireNonNull(source, "source");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public Page<MonthlyPeriod> execute(Actor actor, PageRequest page) {
        CashGuards.requireAllowed(permissions, actor, Operation.LIST_MONTHLY_CONSOLIDATIONS);
        return MonthlyListing.page(source, calendar, actor.tenantId(), page);
    }
}
