package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.access.Actor;
import co.edu.corhuila.multi_tour.cash_reporting.access.CashPermissions;
import co.edu.corhuila.multi_tour.cash_reporting.access.Operation;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.Clock;
import co.edu.corhuila.multi_tour.cash_reporting.calendar.TenantCalendar;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.ParametrizedBase;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Opens the cash of today. Order of the checks (plan D-06): permission, base rule, shape, key replay, then the state of
 * the day. The Administrator may send the base; a Collaborator never does and opens with the parametrized one.
 */
public final class OpenCashDay {

    static final String RESOURCE_TYPE = "OPEN_CASH_DAY";

    private final CashPermissions permissions;
    private final CashSessionRepository sessions;
    private final ParametrizedBase parametrizedBase;
    private final IdempotencyStore keys;
    private final OutboxWriter outbox;
    private final TenantCalendar calendar;
    private final Clock clock;
    private final UnitOfWork unitOfWork;

    public OpenCashDay(
            CashPermissions permissions,
            CashSessionRepository sessions,
            ParametrizedBase parametrizedBase,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock) {
        this(permissions, sessions, parametrizedBase, keys, outbox, calendar, clock, new Untransacted());
    }

    /** The day, its key and its event commit together (RN-CS5): the composition root passes the real unit of work. */
    public OpenCashDay(
            CashPermissions permissions,
            CashSessionRepository sessions,
            ParametrizedBase parametrizedBase,
            IdempotencyStore keys,
            OutboxWriter outbox,
            TenantCalendar calendar,
            Clock clock,
            UnitOfWork unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.parametrizedBase = Objects.requireNonNull(parametrizedBase, "parametrizedBase");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @param openingBaseCents the base typed by the Administrator, or null to use the parametrized one */
    public CashSession execute(Actor actor, BusinessDate date, Long openingBaseCents, String idempotencyKey) {
        CashGuards.requireAllowed(permissions, actor, Operation.OPEN_CASH_DAY);
        if (openingBaseCents != null && !permissions.allows(actor, Operation.ADJUST_CASH_BASE)) {
            throw new CashRuleViolation.Forbidden("Only the administrator may type the base at opening");
        }
        Cents typedBase = openingBaseCents == null ? null : CashGuards.requireBase(openingBaseCents);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("The Idempotency-Key is required");
        }
        String tenantId = actor.tenantId();
        calendar.requireNotAfterToday(tenantId, date);

        var stored = keys.find(tenantId, idempotencyKey);
        if (stored.isPresent()) {
            if (!RESOURCE_TYPE.equals(stored.get().resourceType())) {
                throw new IllegalArgumentException("The Idempotency-Key belongs to another operation");
            }
            return sessions.find(tenantId, BusinessDate.of(LocalDate.parse(stored.get().resourceId())))
                    .orElseThrow(() -> new CashRuleViolation.NotFound("The stored cash day no longer exists"));
        }
        try {
            return unitOfWork.inTransaction(() -> write(actor, date, typedBase, idempotencyKey));
        } catch (CashRuleViolation.AlreadyOpen lostTheCreation) {
            // The unique key said another writer created the day after our empty read: decide again over that day.
            return unitOfWork.inTransaction(() -> write(actor, date, typedBase, idempotencyKey));
        }
    }

    private CashSession write(Actor actor, BusinessDate date, Cents typedBase, String idempotencyKey) {
        String tenantId = actor.tenantId();
        BusinessDate today = calendar.today(tenantId);
        var existing = sessions.findForUpdate(tenantId, date);
        existing.ifPresent(session -> CashGuards.requireNotPending(session, today));
        if (!date.equals(today)) {
            throw new CashRuleViolation.RuleViolation("Only today can be opened: " + date.value());
        }
        existing.ifPresent(session -> {
            CashGuards.requireNotClosed(session);
            if (session.hasOperationalOpening()) {
                throw new CashRuleViolation.AlreadyOpen("The cash of " + date.value() + " is already open");
            }
        });

        Cents base = typedBase != null ? typedBase : parametrizedBase.forTenant(tenantId);
        var opening = new CashSession.Opening(base, clock.now(), actor.userId());
        CashSession opened = existing.isPresent()
                ? existing.get().open(opening, today)
                : CashSession.openNew(tenantId, date, calendar.zoneOf(tenantId), opening, today);

        CashSession saved = existing.isPresent() ? sessions.save(tenantId, opened) : sessions.insert(tenantId, opened);
        keys.put(tenantId, idempotencyKey, RESOURCE_TYPE, date.value().toString());
        outbox.append(tenantId, CashEvents.CASH_SESSION_OPENED, date.value().toString(), payload(date, base));
        return saved;
    }

    private static Map<String, Object> payload(BusinessDate date, Cents base) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessDate", date.value().toString());
        payload.put("openingBaseCents", base.value());
        return payload;
    }
}
