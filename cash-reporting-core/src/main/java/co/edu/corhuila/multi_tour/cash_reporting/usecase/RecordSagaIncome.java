package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.Objects;

/**
 * The income the saga register-payment records for a reservation (ADR-004 D4): the commercial api never writes in the
 * cash. It follows the rules of every movement of the day and stores the reservation it was paid for.
 */
public final class RecordSagaIncome {

    private final MovementRecorder recorder;

    public RecordSagaIncome(
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

    public Outcome<RecordedMovement> execute(
            Actor service,
            String tenantId,
            String onBehalfOf,
            BusinessDate date,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String reservationId,
            String idempotencyKey) {
        return execute(service, tenantId, onBehalfOf, date, medium, concept, amountCents, reservationId, null, idempotencyKey);
    }

    /** paymentId is the payment of domain 2 this income registers: it has one income whatever the key (null: a movement of no payment). */
    public Outcome<RecordedMovement> execute(
            Actor service,
            String tenantId,
            String onBehalfOf,
            BusinessDate date,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String reservationId,
            String paymentId,
            String idempotencyKey) {
        Actor actor = ServiceActing.actor(service, Objects.requireNonNull(tenantId, "tenantId"), onBehalfOf);
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("An income of a saga needs its reservation");
        }
        return recorder.record(actor, date, MovementType.INCOME, medium, concept, amountCents, idempotencyKey, null,
                reservationId, null, paymentId);
    }
}
