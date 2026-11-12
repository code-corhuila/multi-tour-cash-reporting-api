package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.PeriodSummary;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Objects;

/**
 * Reads the summary of one period of the tenant of the token (AC-03.4). Without a period it is the latest period of the
 * report; a period with no row is a summary in zeros, never an error (AC-03.2).
 */
public final class GetMonthlyReportSummary {

    private final CashPermissions permissions;
    private final MonthlyReportSource source;
    private final TenantCalendar calendar;

    public GetMonthlyReportSummary(CashPermissions permissions, MonthlyReportSource source, TenantCalendar calendar) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.source = Objects.requireNonNull(source, "source");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    /** @param period the period asked for, or null for the latest one of the report */
    public PeriodSummary execute(Actor actor, ReportedPeriod period) {
        CashGuards.requireAllowed(permissions, actor, Operation.GET_MONTHLY_REPORT_SUMMARY);
        String tenantId = actor.tenantId();
        if (period != null) {
            return PeriodSummary.of(MonthlyListing.of(source, tenantId, period));
        }
        return PeriodSummary.of(
                MonthlyListing.page(source, calendar, tenantId, new PageRequest(1, 1)).items().get(0));
    }
}
