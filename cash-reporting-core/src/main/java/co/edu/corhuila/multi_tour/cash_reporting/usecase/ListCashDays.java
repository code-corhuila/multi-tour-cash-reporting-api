package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.List;
import java.util.Objects;

/**
 * Lists the cash days of the tenant, most recent first. Read only. With the pending filter it keeps only the days open
 * with operational opening whose calendar day is over (D-05). The movements and the closures of the whole page are read
 * in one call each, so a page costs a fixed number of queries.
 */
public final class ListCashDays {

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final CashClosureRepository closures;
    private final TenantCalendar calendar;

    public ListCashDays(
            CashPermissions permissions,
            CashSessionRepository sessions,
            CashMovementRepository movements,
            CashClosureRepository closures,
            TenantCalendar calendar) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public Page<CashDay> execute(Actor actor, boolean pendingOnly, PageRequest request) {
        CashGuards.requireAllowed(permissions, actor, Operation.LIST_CASH_DAYS);
        Objects.requireNonNull(request, "request");
        String tenantId = actor.tenantId();
        BusinessDate today = calendar.today(tenantId);

        Page<CashSession> page = sessions.list(tenantId, request, pendingOnly ? today : null);
        List<BusinessDate> dates = page.items().stream().map(CashSession::date).toList();
        var movementsByDate = movements.findByDates(tenantId, dates);
        var closureByDate = closures.findLatestByDates(tenantId, dates);
        return page.map(session -> CashDay.of(
                session,
                movementsByDate.getOrDefault(session.date(), List.of()),
                closureByDate.get(session.date()),
                today));
    }
}
