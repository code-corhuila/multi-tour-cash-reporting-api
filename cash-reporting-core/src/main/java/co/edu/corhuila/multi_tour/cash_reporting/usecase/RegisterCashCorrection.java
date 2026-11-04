package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashCorrectionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdGenerator;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashCorrection;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import java.util.Objects;

/**
 * Registers a note that justifies a correction of a closed day (AC-X4, DP-CJ3: Administrator only). It changes no
 * figure, writes neither the session nor the closure (the closure is only read, to point at it) and publishes no
 * event (none exists in events.md). It runs behind a port that only an in-memory fake implements (DD-4).
 */
public final class RegisterCashCorrection {

    static final String RESOURCE_TYPE = "CASH_CORRECTION";

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final CashClosureRepository closures;
    private final CashCorrectionRepository corrections;
    private final IdempotencyStore keys;
    private final Clock clock;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;

    public RegisterCashCorrection(
            CashPermissions permissions,
            CashSessionRepository sessions,
            CashClosureRepository closures,
            CashCorrectionRepository corrections,
            IdempotencyStore keys,
            Clock clock,
            IdGenerator ids,
            UnitOfWork unitOfWork) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.closures = Objects.requireNonNull(closures, "closures");
        this.corrections = Objects.requireNonNull(corrections, "corrections");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CashCorrection execute(Actor actor, BusinessDate date, String justification, String idempotencyKey) {
        CashGuards.requireAllowed(permissions, actor, Operation.REGISTER_CASH_CORRECTION);
        Objects.requireNonNull(date, "date");
        String cleanJustification = CashGuards.requireReason(justification, "justification");
        CashGuards.requireKey(idempotencyKey);
        String tenantId = actor.tenantId();

        var stored = keys.find(tenantId, idempotencyKey);
        if (stored.isPresent()) {
            if (!RESOURCE_TYPE.equals(stored.get().resourceType())) {
                throw new IllegalArgumentException("The Idempotency-Key belongs to another operation");
            }
            return corrections.find(tenantId, stored.get().resourceId())
                    .orElseThrow(() -> new CashRuleViolation.NotFound("The stored correction no longer exists"));
        }
        return unitOfWork.inTransaction(() -> write(actor, date, cleanJustification, idempotencyKey));
    }

    private CashCorrection write(Actor actor, BusinessDate date, String justification, String key) {
        String tenantId = actor.tenantId();
        CashSession session = sessions.find(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NotFound("There is no cash on " + date.value()));
        if (session.status() != SessionStatus.CLOSED) {
            throw new CashRuleViolation.InvalidTransition("The cash of " + date.value() + " is not closed");
        }
        if (session.closingMode() == ClosingMode.AUTOMATIC) {
            throw new CashRuleViolation.RuleViolation(
                    "The cash of " + date.value() + " was closed automatically and takes no correction");
        }
        CashClosure closure = closures.findLatest(tenantId, date)
                .orElseThrow(() -> new CashRuleViolation.NotFound("The closure of " + date.value() + " is missing"));

        var correction = new CashCorrection(
                ids.next(), tenantId, date, closure.id(), justification, actor.userId(), clock.now());
        corrections.insert(tenantId, correction);
        keys.put(tenantId, key, RESOURCE_TYPE, correction.id());
        return correction;
    }
}
