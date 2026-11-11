package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.util.Objects;

/**
 * The compensation of the saga register-payment (docs 843 and 848): a movement is never updated or deleted, so the
 * reversal is a new movement of the opposite kind linked to the one it reverses. A REFUND reverses an income and an INCOME
 * reverses a refund, with the same amount, medium and reservation, once only. It is recorded today of the tenant under the
 * rules of every movement of the day: a cash movement needs the opening and a closed day refuses it, a transfer after the
 * closing is stored marked and does not reopen the day.
 */
public final class ReverseCashMovement {

    private final CashMovementRepository movements;
    private final IdempotencyStore keys;
    private final TenantCalendar calendar;
    private final MovementRecorder recorder;

    public ReverseCashMovement(
            CashSessionRepository sessions,
            CashMovementRepository movements,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.movements = Objects.requireNonNull(movements, "movements");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.recorder = new MovementRecorder(sessions, movements, keys, outbox, calendar, clock, ids, unitOfWork);
    }

    public Outcome<RecordedMovement> execute(
            Actor service, String tenantId, String onBehalfOf, String movementId, String reason, String idempotencyKey) {
        Actor actor = ServiceActing.actor(service, Objects.requireNonNull(tenantId, "tenantId"), onBehalfOf);
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new IllegalArgumentException("The reason is required, up to 200 characters");
        }
        RecordedMovement original = movements.findById(tenantId, movementId)
                .orElseThrow(() -> new CashRuleViolation.NotFound("There is no movement " + movementId));
        boolean replay = idempotencyKey != null && keys.find(tenantId, idempotencyKey).isPresent();
        if (!replay && movements.findReversalOf(tenantId, movementId).isPresent()) {
            throw new CashRuleViolation.RuleViolation("The movement " + movementId + " is already reversed");
        }
        MovementType type = original.movement().type();
        if (type != MovementType.INCOME && type != MovementType.REFUND) {
            throw new CashRuleViolation.RuleViolation("Only an income or a refund can be reversed");
        }
        String reservationId = type == MovementType.INCOME ? original.reservationId() : original.refund().reservationId();
        if (reservationId == null) {
            throw new CashRuleViolation.RuleViolation("The movement has no reservation: it is not a movement of a saga");
        }
        MovementType opposite = type == MovementType.INCOME ? MovementType.REFUND : MovementType.INCOME;
        return recorder.record(
                actor,
                calendar.today(tenantId),
                opposite,
                original.movement().medium(),
                reason.trim(),
                original.movement().amount().value(),
                idempotencyKey,
                opposite == MovementType.REFUND ? new RefundDetail(reservationId) : null,
                reservationId,
                movementId);
    }
}
