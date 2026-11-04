package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import co.edu.corhuila.multi_tour.cash_reporting.session.Origin;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The closures in PostgreSQL: insert-only. The table keeps no date, origin or late mark; they come from the day the
 * closure points at, and a closure signed on a later date than its day is the formal closure of a pending day.
 */
@Component
@Transactional
public class JpaCashClosureRepository implements CashClosureRepository {

    /**
     * The table forbids a user on an AUTOMATIC closing (chk_cash_session_closure_closed_by) and the domain record asks
     * for a non-null one. A closure read back from a null closed_by carries this sentinel; on insert the user of an
     * automatic closing is not stored. Review with the Project Lead if the record should allow a null instead.
     */
    static final String AUTOMATIC_CLOSER = "SYSTEM";

    private static final String FROM = "from CashClosureEntity c join CashSessionEntity s"
            + " on s.id = c.sessionId and s.tenantId = c.tenantId where c.tenantId = :tenant";

    @PersistenceContext
    private EntityManager em;

    @Override
    public CashClosure insert(String tenantId, CashClosure closure) {
        var row = new CashClosureEntity();
        row.id = UUID.fromString(closure.id());
        row.tenantId = UUID.fromString(tenantId);
        row.sessionId = JpaCashSessionRepository.idOf(em, tenantId, closure.date());
        row.closedAt = closure.closedAt();
        row.closingMode = CashSessionEntity.column(closure.mode());
        row.closedBy = closure.mode() == ClosingMode.AUTOMATIC ? null : UUID.fromString(closure.closedBy());
        DayView view = closure.view();
        row.openingBaseCents = value(view.openingBaseCents());
        row.incomeCents = view.incomeCents().value();
        row.operationalPaymentsCents = view.operationalPaymentsCents().value();
        row.operationalExpensesCents = view.expensesCents().value();
        row.refundsCents = view.refundsCents().value();
        row.cashEntriesCents = view.cashEntriesCents().value();
        row.transferEntriesCents = view.transferEntriesCents().value();
        row.cashExitsCents = view.cashExitsCents().value();
        row.transferExitsCents = view.transferExitsCents().value();
        row.expectedCashCents = value(view.expectedCashCents());
        row.closingBalanceCents = closure.closingBalance().value();
        em.persist(row);
        em.flush();
        return closure;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashClosure> findLatest(String tenantId, BusinessDate date) {
        return em.createQuery(
                        "select c, s " + FROM + " and s.businessDate = :date order by c.closedAt desc, c.id desc",
                        Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("date", date.value())
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(JpaCashClosureRepository::toDomain);
    }

    /** One read for all the days: the rows come oldest first, so the latest closure of a day overwrites the earlier. */
    @Override
    @Transactional(readOnly = true)
    public Map<BusinessDate, CashClosure> findLatestByDates(String tenantId, List<BusinessDate> dates) {
        Map<BusinessDate, CashClosure> latest = new LinkedHashMap<>();
        if (dates.isEmpty()) {
            return latest;
        }
        em.createQuery("select c, s " + FROM + " and s.businessDate in :dates order by c.closedAt, c.id", Object[].class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("dates", dates.stream().map(BusinessDate::value).toList())
                .getResultList()
                .stream()
                .map(JpaCashClosureRepository::toDomain)
                .forEach(closure -> latest.put(closure.date(), closure));
        return latest;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashClosure> list(String tenantId, PageRequest request) {
        UUID tenant = UUID.fromString(tenantId);
        long total = em.createQuery("select count(c) " + FROM, Long.class)
                .setParameter("tenant", tenant)
                .getSingleResult();
        List<CashClosure> items = em.createQuery(
                        "select c, s " + FROM + " order by c.closedAt desc, c.id desc", Object[].class)
                .setParameter("tenant", tenant)
                .setFirstResult(request.offset())
                .setMaxResults(request.limit())
                .getResultList()
                .stream()
                .map(JpaCashClosureRepository::toDomain)
                .toList();
        return new Page<>(items, request.page(), request.limit(), total);
    }

    /**
     * The origin comes from the day (it never changes after creation) and formalClosureOfPending is derived: the
     * closure was signed on a date after the day it closes, in the zone of that day. This is what the close use case
     * stores (date before today at closing time); the table has no column for it.
     */
    private static CashClosure toDomain(Object[] pair) {
        var c = (CashClosureEntity) pair[0];
        var s = (CashSessionEntity) pair[1];
        BusinessDate date = BusinessDate.of(s.businessDate);
        var view = new DayView(
                cents(c.openingBaseCents),
                Cents.of(c.incomeCents),
                Cents.of(c.operationalPaymentsCents),
                Cents.of(c.operationalExpensesCents),
                Cents.of(c.refundsCents),
                Cents.of(c.closingBalanceCents),
                Cents.of(c.cashEntriesCents),
                Cents.of(c.cashExitsCents),
                Cents.of(c.transferEntriesCents),
                Cents.of(c.transferExitsCents),
                cents(c.expectedCashCents),
                Cents.of(c.transferEntriesCents - c.transferExitsCents));
        return new CashClosure(
                c.id.toString(),
                c.tenantId.toString(),
                date,
                Origin.valueOf(s.origin),
                CashSessionEntity.mode(c.closingMode),
                view,
                c.closedBy == null ? AUTOMATIC_CLOSER : c.closedBy.toString(),
                c.closedAt,
                BusinessDate.of(c.closedAt, ZoneId.of(s.timeZone)).isAfter(date));
    }

    private static Long value(Cents cents) {
        return cents == null ? null : cents.value();
    }

    private static Cents cents(Long value) {
        return value == null ? null : Cents.of(value);
    }
}
