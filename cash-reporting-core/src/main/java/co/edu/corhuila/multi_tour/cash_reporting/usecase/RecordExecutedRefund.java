package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;

/**
 * Writes the REFUND row of a refund the saga already executed, with its reservation (RN-CJ7). No HTTP route serves it
 * yet (plan D-15): the saga step will call it. It follows the same rules as any other movement of the day.
 */
public final class RecordExecutedRefund {

    private final MovementRecorder recorder;

    public RecordExecutedRefund(
            CashSessionRepository sessions,
            CashMovementRepository movements,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.recorder = new MovementRecorder(sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    public RecordedMovement execute(
            Actor actor,
            BusinessDate date,
            PaymentMedium medium,
            String reservationId,
            String concept,
            Long amountCents,
            String idempotencyKey) {
        CashGuards.requireTenant(actor);
        return recorder.record(
                actor,
                date,
                MovementType.REFUND,
                medium,
                concept,
                amountCents,
                idempotencyKey,
                new RefundDetail(reservationId));
    }
}
