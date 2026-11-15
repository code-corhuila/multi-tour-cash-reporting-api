package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashReopeningRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashReopening;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reopens a closed day (plan section 3, "Reopen"). Permission, reason, key replay, then in one unit of work: the state
 * rules, the reopening that points at the closure it reopens, the same cash back to open, the key and the event. The
 * closure is only read: it stays as history and a second closing makes a new one (DP-CJ8).
 */
public final class ReopenCashDay {

    static final String RESOURCE_TYPE = "REOPEN_CASH_DAY";

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final CashClosureRepository closures;
    private final CashReopeningRepository reopenings;
    private final IdempotencyStore keys;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;

    public ReopenCashDay(
            CashPermissions permissions,
            CashSessionRepository sessions,
            CashClosureRepository closures,
            CashReopeningRepository reopenings,
            IdempotencyStore keys,
            OutboxWriter outbox,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.reopenings = Objects.requireNonNull(reopenings, "reopenings");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CashSession execute(Actor actor, BusinessDate date, String reason, String idempotencyKey) {
        CashGuards.requireAllowed(permissions, actor, Operation.REOPEN_CASH_DAY);
        Objects.requireNonNull(date, "date");
        String cleanReason = CashGuards.requireReason(reason, "reopening reason");
        CashGuards.requireKey(idempotencyKey);
        String tenantId = actor.tenantId();

        var stored = keys.find(tenantId, idempotencyKey);
        if (stored.isPresent()) {
            if (!RESOURCE_TYPE.equals(stored.get().resourceType())) {
                throw new IllegalArgumentException("The Idempotency-Key belongs to another operation");
            }
            BusinessDate day = BusinessDate.of(LocalDate.parse(stored.get().resourceId()));
            return sessions.find(tenantId, day)
                    .orElseThrow(() -> new CashRuleViolation.NotFound("The stored cash day no longer exists"));
        }
        return unitOfWork.inTransaction(() -> write(actor, date, cleanReason, idempotencyKey));
    }

    private CashSession write(Actor actor, BusinessDate date, String reason, String key) {
        String tenantId = actor.tenantId();
        CashSession session = sessions.findForUpdate(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NotFound("There is no cash on " + date.value()));
        if (session.status() != SessionStatus.CLOSED) {
            throw new CashRuleViolation.AlreadyOpen("The cash of " + date.value() + " is not closed");
        }
        if (session.closingMode() == ClosingMode.AUTOMATIC) {
            throw new CashRuleViolation.RuleViolation(
                    "The cash of " + date.value() + " was closed automatically and cannot be reopened");
        }
        CashClosure closure = closures.findLatest(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NotFound("The closure of " + date.value() + " is missing"));

        var reopening =
                new CashReopening(ids.next(), tenantId, date, closure.id(), reason, actor.userId(), clock.now());
        reopenings.insert(tenantId, reopening);
        CashSession reopened = sessions.save(tenantId, session.reopen());
        keys.put(tenantId, key, RESOURCE_TYPE, date.value().toString());
        outbox.append(tenantId, CashEvents.CASH_SESSION_REOPENED, date.value().toString(), payload(reopening));
        return reopened;
    }

    /** Ids and date only: never the reason or a name (RNF-A1.4). */
    private static Map<String, Object> payload(CashReopening reopening) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessDate", reopening.date().value().toString());
        payload.put("closureId", reopening.closureId());
        payload.put("reopeningId", reopening.id());
        return payload;
    }
}
