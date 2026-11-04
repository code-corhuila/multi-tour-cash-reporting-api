package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.Objects;

/**
 * Lists the movements of a day, most recent first. Read only. A day without cash is an empty list. Only stored
 * movements are rows: a transfer pending validation is never stored, so it is never listed (AC2). A refund row carries
 * its reservation.
 */
public final class ListCashMovements {

    private final CashPermissions permissions;
    private final CashMovementRepository movements;
    private final TenantCalendar calendar;

    public ListCashMovements(CashPermissions permissions, CashMovementRepository movements, TenantCalendar calendar) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public Page<RecordedMovement> execute(Actor actor, BusinessDate date, PageRequest request) {
        CashGuards.requireAllowed(permissions, actor, Operation.LIST_CASH_MOVEMENTS);
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(request, "request");
        calendar.requireNotAfterToday(actor.tenantId(), date);
        return movements.list(actor.tenantId(), date, request);
    }
}
