package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashSessionRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import co.edu.corhuila.multi_tour.cash_reporting.session.SessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The cash days in PostgreSQL. The unique key of the tenant and date is what makes one cash per day (plan D-08). */
@Component
@Transactional
public class JpaCashSessionRepository implements CashSessionRepository {

    private static final String UNIQUE_DAY = "uq_cash_session_tenant_business_date";
    private static final String BY_DAY =
            "select s from CashSessionEntity s where s.tenantId = :tenant and s.businessDate = :date";

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<CashSession> find(String tenantId, BusinessDate date) {
        return byDay(tenantId, date).getResultStream().findFirst().map(JpaCashSessionRepository::toDomain);
    }

    /** SELECT ... FOR UPDATE: the writes of one cash day are serial. Needs the transaction of the caller. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CashSession> findForUpdate(String tenantId, BusinessDate date) {
        return byDay(tenantId, date)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .map(JpaCashSessionRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashSession> list(String tenantId, PageRequest request, BusinessDate pendingAsOf) {
        String where = "from CashSessionEntity s where s.tenantId = :tenant"
                + (pendingAsOf == null
                        ? ""
                        : " and s.status = 'OPEN' and s.openedAt is not null"
                                + " and (s.pendingClosure = true or s.businessDate < :asOf)");
        TypedQuery<Long> count = em.createQuery("select count(s) " + where, Long.class);
        TypedQuery<CashSessionEntity> items =
                em.createQuery("select s " + where + " order by s.businessDate desc", CashSessionEntity.class);
        for (TypedQuery<?> query : new TypedQuery<?>[] {count, items}) {
            query.setParameter("tenant", UUID.fromString(tenantId));
            if (pendingAsOf != null) {
                query.setParameter("asOf", pendingAsOf.value());
            }
        }
        items.setFirstResult(request.offset()).setMaxResults(request.limit());
        return new Page<>(
                items.getResultList().stream().map(JpaCashSessionRepository::toDomain).toList(),
                request.page(),
                request.limit(),
                count.getSingleResult());
    }

    /** Inserts the day, or rewrites the mutable columns of the one that exists. A second insert is ALREADY_OPEN. */
    @Override
    public CashSession save(String tenantId, CashSession session) {
        CashSessionEntity row =
                byDay(tenantId, session.date()).getResultStream().findFirst().orElse(null);
        boolean isNew = row == null;
        if (isNew) {
            row = new CashSessionEntity();
            row.id = UUID.randomUUID();
        }
        fill(row, tenantId, session);
        try {
            if (isNew) {
                em.persist(row);
            }
            em.flush();
        } catch (PersistenceException e) {
            if (isNew && mentions(e, UNIQUE_DAY)) {
                throw new CashRuleViolation.AlreadyOpen("The cash of " + session.date().value() + " is already open");
            }
            throw e;
        }
        return toDomain(row);
    }

    /** The id of the day, which the closures and reopenings point at. */
    static UUID idOf(EntityManager em, String tenantId, BusinessDate date) {
        return em.createQuery(
                        "select s.id from CashSessionEntity s where s.tenantId = :tenant and s.businessDate = :date",
                        UUID.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("date", date.value())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new CashRuleViolation.NotFound("There is no cash on " + date.value()));
    }

    private TypedQuery<CashSessionEntity> byDay(String tenantId, BusinessDate date) {
        return em.createQuery(BY_DAY, CashSessionEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("date", date.value());
    }

    /** A closed day takes its closing columns from its latest closure, which is inserted before the day is saved. */
    private void fill(CashSessionEntity row, String tenantId, CashSession session) {
        row.tenantId = UUID.fromString(tenantId);
        row.businessDate = session.date().value();
        row.timeZone = session.zone().getId();
        row.origin = session.origin().name();
        row.status = session.status().name();
        row.pendingClosure = session.storedPendingMark();
        var opening = session.opening();
        row.openingBaseCents = opening == null ? null : opening.base().value();
        row.openedAt = opening == null ? null : opening.at();
        row.openedBy = opening == null ? null : UUID.fromString(opening.by());
        row.closingMode = CashSessionEntity.column(session.closingMode());
        row.closedAt = null;
        row.closingBalanceCents = null;
        row.closedBy = null;
        if (session.status() == SessionStatus.CLOSED) {
            CashClosureEntity closure = em.createQuery(
                            "select c from CashClosureEntity c where c.tenantId = :tenant and c.sessionId = :id"
                                    + " order by c.closedAt desc, c.id desc",
                            CashClosureEntity.class)
                    .setParameter("tenant", row.tenantId)
                    .setParameter("id", row.id)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("A closed cash needs its closure inserted first"));
            row.closedAt = closure.closedAt;
            row.closingBalanceCents = closure.closingBalanceCents;
            row.closedBy = closure.closedBy;
        }
    }

    static CashSession toDomain(CashSessionEntity row) {
        var opening = row.openedAt == null
                ? null
                : new CashSession.Opening(Cents.of(row.openingBaseCents), row.openedAt, row.openedBy.toString());
        return new CashSession(
                row.tenantId.toString(),
                BusinessDate.of(row.businessDate),
                ZoneId.of(row.timeZone),
                Origin.valueOf(row.origin),
                opening,
                SessionStatus.valueOf(row.status),
                CashSessionEntity.mode(row.closingMode),
                row.pendingClosure);
    }

    /** True when the constraint name appears in the exception or in any of its causes. */
    static boolean mentions(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
