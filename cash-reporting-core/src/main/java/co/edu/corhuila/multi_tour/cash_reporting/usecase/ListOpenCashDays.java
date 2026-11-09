package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Objects;

/**
 * The first step of the sweep of job 8 (ADR-004 D5): every open cash of every tenant, oldest first, each one with its
 * tenant. Only a service may read it; the worker then acts tenant by tenant.
 */
public final class ListOpenCashDays {

    private final CashSessionRepository sessions;
    private final TenantCalendar calendar;

    public ListOpenCashDays(CashSessionRepository sessions, TenantCalendar calendar) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public Page<OpenCashDayItem> execute(Actor actor, PageRequest request) {
        CashGuards.requireService(actor);
        return sessions.listOpen(request).map(session -> new OpenCashDayItem(
                session.tenantId(),
                session.date(),
                session.zone(),
                session.origin(),
                session.hasOperationalOpening(),
                session.isPending(calendar.today(session.tenantId()))));
    }
}
