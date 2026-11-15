package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashMovementRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.PendingClosureAlert;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Job 8: the automatic end of day (AB-02). It closes only a cash created automatically, which never had an operational
 * opening, once its day ended in the zone stored with it. A cash with operational opening is never closed here: it stays
 * pending closure (derived at read) and the alert is raised through its port (PDR 1.9, GAP-012). Repeating the call on a
 * day already closed automatically answers the stored closure and writes nothing: the state is the idempotency.
 */
public final class AutoCloseCashDay {

    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final CashClosureRepository closures;
    private final OutboxWriter outbox;
    private final PendingClosureAlert alerts;
    private final Clock clock;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;

    public AutoCloseCashDay(
            CashSessionRepository sessions,
            CashMovementRepository movements,
            CashClosureRepository closures,
            OutboxWriter outbox,
            PendingClosureAlert alerts,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.movements = Objects.requireNonNull(movements, "movements");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CashClosure execute(Actor service, String tenantId, BusinessDate date) {
        CashGuards.requireService(service);
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(date, "date");
        return unitOfWork.inTransaction(() -> write(service, tenantId, date));
    }

    private CashClosure write(Actor service, String tenantId, BusinessDate date) {
        CashSession session = sessions.findForUpdate(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NotFound("There is no cash on " + date.value()));
        if (session.status() == SessionStatus.CLOSED) {
            if (session.closingMode() != ClosingMode.AUTOMATIC) {
                throw new CashRuleViolation.AlreadyClosed("The cash of " + date.value() + " is closed");
            }
            return closures.findLatest(tenantId, date)
                    .orElseThrow(() -> new CashRuleViolation.NotFound("The closure of " + date.value() + " is missing"));
        }
        if (session.hasOperationalOpening()) {
            alerts.raise(tenantId, date);
            throw new CashRuleViolation.RuleViolation(
                    "The cash of " + date.value() + " has an operational opening: it needs its formal closing");
        }
        if (!date.value().isBefore(LocalDate.ofInstant(clock.now(), session.zone()))) {
            throw new CashRuleViolation.RuleViolation("The day " + date.value() + " has not ended yet");
        }

        DayView view = DayTotals.compute(null, movements.findByDate(tenantId, date));
        CashClosure closure = CashClosure.snapshot(
                ids.next(), session, view, ClosingMode.AUTOMATIC, service.userId(), clock.now(), false);
        closures.insert(tenantId, closure);
        sessions.save(tenantId, session.closeAutomatically());
        outbox.append(tenantId, CashEvents.CASH_SESSION_AUTO_CLOSED, date.value().toString(), payload(closure));
        return closure;
    }

    /** Ids, date, mode and the balance only: never a name or a concept (RNF-A1.4). */
    private static Map<String, Object> payload(CashClosure closure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessDate", closure.date().value().toString());
        payload.put("closureId", closure.id());
        payload.put("closingMode", closure.mode().name());
        payload.put("closingBalanceCents", closure.closingBalance().value());
        CashEvents.putTotals(payload, closure.view());
        payload.put("closedAt", closure.closedAt().toString());
        return payload;
    }
}
