package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DashboardIndicators;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Reads the dashboard of the tenant's local day (AC-DB.1, AC-DB.4 to AC-DB.7). It is computed from the last known facts at
 * every read, so a day without events still shows the stock of the earlier ones; nothing is written. The tenant is the one of
 * the token and "today" is the tenant's calendar day, decided by the service.
 */
public final class GetDailyDashboard {

    private final CashPermissions permissions;
    private final DashboardFacts facts;
    private final TenantCalendar calendar;

    public GetDailyDashboard(CashPermissions permissions, DashboardFacts facts, TenantCalendar calendar) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public DashboardIndicators execute(Actor actor) {
        CashGuards.requireAllowed(permissions, actor, Operation.GET_DAILY_DASHBOARD);
        String tenantId = actor.tenantId();
        BusinessDate today = calendar.today(tenantId);
        ZoneId zone = calendar.zoneOf(tenantId);
        return DashboardIndicators.of(facts.reservations(tenantId), facts.departures(tenantId), today, zone);
    }
}
