package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.Objects;

/**
 * Replaces the base of an open day with operational opening. Administrator only. It takes no key: the same value again
 * writes nothing. It publishes no event (none exists in the catalog).
 */
public final class AdjustCashBase {

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final TenantCalendar calendar;

    private final UnitOfWork unitOfWork;

    public AdjustCashBase(CashPermissions permissions, CashSessionRepository sessions, TenantCalendar calendar) {
        this(permissions, sessions, calendar, new Untransacted());
    }

    /** The row lock of the day needs a transaction: the composition root passes the real unit of work. */
    public AdjustCashBase(
            CashPermissions permissions, CashSessionRepository sessions, TenantCalendar calendar, UnitOfWork unitOfWork) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CashSession execute(Actor actor, BusinessDate date, Long baseCents) {
        CashGuards.requireAllowed(permissions, actor, Operation.ADJUST_CASH_BASE);
        Cents base = CashGuards.requireBase(baseCents);
        calendar.requireNotAfterToday(actor.tenantId(), date);
        return unitOfWork.inTransaction(() -> write(actor, date, base));
    }

    private CashSession write(Actor actor, BusinessDate date, Cents base) {
        String tenantId = actor.tenantId();
        CashSession session = sessions.findForUpdate(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NoOpenCash("The cash of " + date.value() + " is not open"));
        CashGuards.requireNotPending(session, calendar.today(tenantId));
        CashGuards.requireNotClosed(session);
        if (!session.hasOperationalOpening()) {
            throw new CashRuleViolation.NoOpenCash("The cash of " + date.value() + " has no operational opening");
        }
        if (session.opening().base().equals(base)) {
            return session;
        }
        return sessions.save(tenantId, session.adjustBase(base));
    }
}
