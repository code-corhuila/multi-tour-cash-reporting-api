package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.ParametrizedBase;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.Objects;

/**
 * Reads the cash of a date, or of today. Read only. A date without cash is the NO_OPENING view, never an error; a date
 * after today is refused. The figures are computed on every read from the base and the movements (RN-CJ1).
 */
public final class GetCashDay {

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final CashClosureRepository closures;
    private final TenantCalendar calendar;
    private final ParametrizedBase parametrizedBase;

    public GetCashDay(
            CashPermissions permissions,
            CashSessionRepository sessions,
            CashMovementRepository movements,
            CashClosureRepository closures,
            TenantCalendar calendar,
            ParametrizedBase parametrizedBase) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.parametrizedBase = Objects.requireNonNull(parametrizedBase, "parametrizedBase");
    }

    /** The cash of today in the zone of the tenant: the service decides which day that is. */
    public CashDay current(Actor actor) {
        CashGuards.requireAllowed(permissions, actor, Operation.GET_CURRENT_CASH_DAY);
        return read(actor.tenantId(), calendar.today(actor.tenantId()));
    }

    public CashDay execute(Actor actor, BusinessDate date) {
        CashGuards.requireAllowed(permissions, actor, Operation.GET_CASH_DAY);
        Objects.requireNonNull(date, "date");
        calendar.requireNotAfterToday(actor.tenantId(), date);
        return read(actor.tenantId(), date);
    }

    private CashDay read(String tenantId, BusinessDate date) {
        CashSession session = sessions.find(tenantId, date).orElse(null);
        if (session == null) {
            return CashDay.noOpening(date, calendar.zoneOf(tenantId), parametrizedBase.forTenant(tenantId));
        }
        CashClosure closure = closures.findLatest(tenantId, date).orElse(null);
        Cents base = session.hasOperationalOpening() ? null : parametrizedBase.forTenant(tenantId);
        return CashDay.of(session, movements.findByDate(tenantId, date), closure, calendar.today(tenantId), base);
    }
}
