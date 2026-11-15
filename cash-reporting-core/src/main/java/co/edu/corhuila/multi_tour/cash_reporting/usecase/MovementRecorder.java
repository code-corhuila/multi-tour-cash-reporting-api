package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyKeyTaken;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.Acceptance;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The write shared by the manual movement and the executed refund (plan section 3, "Register a movement"). Shape, key
 * replay, then in one unit of work: the day, the row, the key and the events.
 */
final class MovementRecorder {

    static final String RESOURCE_TYPE = "CASH_MOVEMENT";
    private static final int MAX_CONCEPT = 200;

    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final IdempotencyStore keys;
    private final OutboxWriter outbox;
    private final TenantCalendar calendar;
    private final Clock clock;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;

    MovementRecorder(
            CashSessionRepository sessions,
            CashMovementRepository movements,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    Outcome<RecordedMovement> record(
            Actor actor,
            BusinessDate date,
            MovementType type,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String key,
            RefundDetail refund) {
        return record(actor, date, type, medium, concept, amountCents, key, refund, null, null);
    }

    Outcome<RecordedMovement> record(
            Actor actor,
            BusinessDate date,
            MovementType type,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String key,
            RefundDetail refund,
            String reservationId) {
        return record(actor, date, type, medium, concept, amountCents, key, refund, reservationId, null, null);
    }

    Outcome<RecordedMovement> record(
            Actor actor,
            BusinessDate date,
            MovementType type,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String key,
            RefundDetail refund,
            String reservationId,
            String reversesMovementId) {
        return record(actor, date, type, medium, concept, amountCents, key, refund, reservationId, reversesMovementId, null);
    }

    /** paymentReference is the payment an income registers (null for any other movement): it is recorded once. */
    Outcome<RecordedMovement> record(
            Actor actor,
            BusinessDate date,
            MovementType type,
            PaymentMedium medium,
            String concept,
            Long amountCents,
            String key,
            RefundDetail refund,
            String reservationId,
            String reversesMovementId,
            String paymentReference) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(medium, "medium");
        if (concept == null || concept.isBlank() || concept.length() > MAX_CONCEPT) {
            throw new IllegalArgumentException("The concept is required, up to " + MAX_CONCEPT + " characters");
        }
        if (amountCents == null) {
            throw new IllegalArgumentException("The amount is required");
        }
        CashMovement movement = new CashMovement(type, medium, Cents.amount(amountCents));
        if (key == null || key.length() < 8 || key.length() > 128) {
            throw new IllegalArgumentException("The Idempotency-Key must have 8 to 128 characters");
        }
        String tenantId = actor.tenantId();
        calendar.requireNotAfterToday(tenantId, date);

        var stored = keys.find(tenantId, key);
        if (stored.isPresent()) {
            return Outcome.replay(storedMovement(tenantId, stored.get()));
        }
        if (paymentReference != null && movements.findIncomeOfPayment(tenantId, paymentReference).isPresent()) {
            throw new CashRuleViolation.PaymentAlreadyRecorded("The payment " + paymentReference + " already has its income");
        }
        try {
            return Outcome.created(attempt(actor, date, movement, concept, key, refund, reservationId, reversesMovementId, paymentReference));
        } catch (IdempotencyKeyTaken lostTheKey) {
            // Another writer committed the same key after our empty read: our transaction is rolled back, answer theirs.
            var winner = keys.find(tenantId, key).orElseThrow(() -> lostTheKey);
            return Outcome.replay(storedMovement(tenantId, winner));
        }
    }

    private RecordedMovement attempt(
            Actor actor, BusinessDate date, CashMovement movement, String concept, String key, RefundDetail refund,
            String reservationId, String reversesMovementId, String paymentReference) {
        try {
            return unitOfWork.inTransaction(() -> write(actor, date, movement, concept, key, refund, reservationId, reversesMovementId, paymentReference));
        } catch (CashRuleViolation.AlreadyOpen lostTheCreation) {
            // Another writer created the day after our empty read: record over that day.
            return unitOfWork.inTransaction(() -> write(actor, date, movement, concept, key, refund, reservationId, reversesMovementId, paymentReference));
        }
    }

    private RecordedMovement storedMovement(String tenantId, IdempotencyStore.Stored stored) {
        if (!RESOURCE_TYPE.equals(stored.resourceType())) {
            throw new IllegalArgumentException("The Idempotency-Key belongs to another operation");
        }
        return movements.findById(tenantId, stored.resourceId())
                .orElseThrow(() -> new CashRuleViolation.NotFound("The stored movement no longer exists"));
    }

    private RecordedMovement write(
            Actor actor, BusinessDate date, CashMovement movement, String concept, String key, RefundDetail refund,
            String reservationId, String reversesMovementId, String paymentReference) {
        String tenantId = actor.tenantId();
        BusinessDate today = calendar.today(tenantId);
        var existing = sessions.findForUpdate(tenantId, date);
        CashSession session;
        if (existing.isPresent()) {
            session = existing.get();
        } else {
            if (!date.equals(today)) {
                throw new CashRuleViolation.RuleViolation("Only today accepts movements: " + date.value());
            }
            if (movement.medium() == PaymentMedium.CASH) {
                throw new CashRuleViolation.NoOpenCash("The cash of " + date.value() + " has no operational opening");
            }
            session = CashSession.automatic(tenantId, date, calendar.zoneOf(tenantId));
        }

        Acceptance acceptance = session.accepts(movement, today);
        switch (acceptance) {
            case PENDING_CLOSURE -> throw new CashRuleViolation.PendingClosure(
                    "The cash of " + date.value() + " is pending closure");
            case NOT_TODAY -> throw new CashRuleViolation.RuleViolation(
                    "Only today accepts movements: " + date.value());
            case NO_OPEN_CASH -> throw new CashRuleViolation.NoOpenCash(
                    "The cash of " + date.value() + " has no operational opening");
            case ALREADY_CLOSED -> throw new CashRuleViolation.AlreadyClosed(
                    "The cash of " + date.value() + " is closed");
            default -> { }
        }

        if (existing.isEmpty()) {
            sessions.insert(tenantId, session);
            String day = date.value().toString();
            outbox.append(
                    tenantId, CashEvents.CASH_SESSION_AUTO_CREATED, day, Map.of("businessDate", day));
        }
        var recorded = new RecordedMovement(
                ids.next(),
                date,
                movement,
                concept.trim(),
                acceptance == Acceptance.ACCEPTED_AFTER_CLOSING,
                clock.now(),
                actor.userId(),
                refund,
                reservationId,
                reversesMovementId,
                paymentReference);
        movements.insert(tenantId, recorded);
        keys.put(tenantId, key, RESOURCE_TYPE, recorded.id());
        outbox.append(tenantId, eventOf(movement.type()), recorded.id(), movementPayload(recorded));
        return recorded;
    }

    private static String eventOf(MovementType type) {
        return switch (type) {
            case INCOME -> CashEvents.CASH_INCOME_RECORDED;
            case OPERATIONAL_PAYMENT -> CashEvents.OPERATIONAL_PAYMENT_RECORDED;
            case OPERATIONAL_EXPENSE -> CashEvents.OPERATIONAL_EXPENSE_RECORDED;
            case REFUND -> CashEvents.REFUND_EXECUTED;
        };
    }


    /** Ids, date, type, medium and amount only: never the concept (RNF-A1.4). */
    private static Map<String, Object> movementPayload(RecordedMovement recorded) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movementId", recorded.id());
        payload.put("businessDate", recorded.date().value().toString());
        payload.put("movementType", recorded.movement().type().name());
        payload.put("paymentMedium", recorded.movement().medium().name());
        payload.put("amountCents", recorded.movement().amount().value());
        payload.put("afterClosing", recorded.afterClosing());
        if (recorded.reservationId() != null) {
            payload.put("reservationId", recorded.reservationId());
        } else if (recorded.refund() != null) {
            payload.put("reservationId", recorded.refund().reservationId());
        }
        payload.put("registeredAt", recorded.registeredAt().toString());
        if (recorded.movement().type() == MovementType.REFUND) {
            payload.put("executorId", recorded.registeredBy());
            if (recorded.refund() != null && recorded.refund().authorizationId() != null) {
                payload.put("refundAuthorizationId", recorded.refund().authorizationId());
            }
        } else {
            payload.put("actorId", recorded.registeredBy());
        }
        return payload;
    }
}
