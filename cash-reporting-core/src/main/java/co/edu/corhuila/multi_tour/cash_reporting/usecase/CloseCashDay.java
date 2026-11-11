package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Closes the cash of a day (plan section 3, "Close"). Permission, shape, future date, key replay, then in one unit of
 * work: the state rules, the snapshot, the closed day, the key and the event. A past date with operational opening is
 * the formal close of a pending day and keeps its own date. Counted physical cash is not part of the closing
 * (DP-CJ4): the closing balance is the total of the day.
 */
public final class CloseCashDay {

    static final String RESOURCE_TYPE = "CLOSE_CASH_DAY";

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final CashClosureRepository closures;
    private final IdempotencyStore keys;
    private final OutboxWriter outbox;
    private final TenantCalendar calendar;
    private final Clock clock;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;

    public CloseCashDay(
            CashPermissions permissions,
            CashSessionRepository sessions,
            CashMovementRepository movements,
            CashClosureRepository closures,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CashClosure execute(Actor actor, BusinessDate date, String idempotencyKey) {
        CashGuards.requireAllowed(permissions, actor, Operation.CLOSE_CASH_DAY);
        Objects.requireNonNull(date, "date");
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("The Idempotency-Key must have 8 to 128 characters");
        }
        String tenantId = actor.tenantId();
        calendar.requireNotAfterToday(tenantId, date);

        var stored = keys.find(tenantId, idempotencyKey);
        if (stored.isPresent()) {
            if (!RESOURCE_TYPE.equals(stored.get().resourceType())) {
                throw new IllegalArgumentException("The Idempotency-Key belongs to another operation");
            }
            BusinessDate day = BusinessDate.of(LocalDate.parse(stored.get().resourceId()));
            return closures.findLatest(tenantId, day)
                    .orElseThrow(() -> new CashRuleViolation.NotFound("The stored closure no longer exists"));
        }
        return unitOfWork.inTransaction(() -> write(actor, date, idempotencyKey));
    }

    private CashClosure write(Actor actor, BusinessDate date, String key) {
        String tenantId = actor.tenantId();
        CashSession session = sessions.findForUpdate(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NoOpenCash(
                        "The cash of " + date.value() + " has no operational opening"));
        CashGuards.requireNotClosed(session);
        if (!session.hasOperationalOpening()) {
            throw new CashRuleViolation.NoOpenCash("The cash of " + date.value() + " has no operational opening");
        }

        boolean formalClosureOfPending = date.isBefore(calendar.today(tenantId));
        DayView view = DayTotals.compute(session.opening().base(), movements.findByDate(tenantId, date));
        CashClosure closure = CashClosure.snapshot(
                ids.next(), session, view, ClosingMode.MANUAL, actor.userId(), clock.now(), formalClosureOfPending);

        closures.insert(tenantId, closure);
        sessions.save(tenantId, session.close(ClosingMode.MANUAL));
        keys.put(tenantId, key, RESOURCE_TYPE, date.value().toString());
        outbox.append(tenantId, CashEvents.CASH_SESSION_CLOSED, date.value().toString(), payload(closure));
        return closure;
    }

    /** Ids, date, mode and amounts only: never a name or a reason (RNF-A1.4). */
    private static Map<String, Object> payload(CashClosure closure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessDate", closure.date().value().toString());
        payload.put("closureId", closure.id());
        payload.put("closingMode", closure.mode().name());
        payload.put("closingBalanceCents", closure.closingBalance().value());
        payload.put("formalClosureOfPending", closure.formalClosureOfPending());
        CashEvents.putTotals(payload, closure.view());
        payload.put("closedBy", closure.closedBy());
        return payload;
    }
}
