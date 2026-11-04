package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementPolicy;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementSource;
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
 * Registers a movement of a day. A person (MANUAL) goes through the permission table and never types a REFUND; a saga
 * step (SAGA) is a trusted internal caller that still acts for a tenant. A cash movement needs the operational opening;
 * a transfer creates the cash of the day or, on a closed day, is stored after the closing (plan section 3).
 */
public final class RegisterCashMovement {

    private final CashPermissions permissions;
    private final MovementRecorder recorder;

    public RegisterCashMovement(
            CashPermissions permissions,
            CashSessionRepository sessions,
            CashMovementRepository movements,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.recorder = new MovementRecorder(sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    public RecordedMovement execute(
            Actor actor,
            MovementSource source,
            BusinessDate date,
            MovementType type,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String idempotencyKey) {
        Objects.requireNonNull(source, "source");
        if (source == MovementSource.MANUAL) {
            CashGuards.requireAllowed(permissions, actor, Operation.REGISTER_CASH_MOVEMENT);
        } else {
            CashGuards.requireTenant(actor);
        }
        Objects.requireNonNull(type, "type");
        if (type == MovementType.REFUND || (source == MovementSource.MANUAL && !MovementPolicy.mayBeTypedByHand(type))) {
            throw new IllegalArgumentException("A " + type + " cannot be registered here");
        }
        return recorder.record(actor, date, type, medium, concept, amountCents, idempotencyKey, null);
    }
}
