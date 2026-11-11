package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.Optional;

/** Outbound port of the cash days. Every method takes the tenant first: no query without it. */
public interface CashSessionRepository {

    Optional<CashSession> find(String tenantId, BusinessDate date);

    /** Same read, but locking the row for the transaction (plan D-08). */
    Optional<CashSession> findForUpdate(String tenantId, BusinessDate date);

    /**
     * The days of the tenant, most recent first. When {@code pendingAsOf} is not null only the days pending on that
     * date (open with operational opening, over or marked pending).
     */
    Page<CashSession> list(String tenantId, PageRequest request, BusinessDate pendingAsOf);

    /** Every open day of every tenant, oldest first: the sweep of job 8, the only read that crosses tenants. */
    Page<CashSession> listOpen(PageRequest request);

    /** Updates a day that the caller read with {@link #findForUpdate}; a day to create goes through {@link #insert}. */
    CashSession save(String tenantId, CashSession session);

    /**
     * Creates the day without reading it again. The unique key of the tenant and date decides between two writers that
     * both read "no cash": the loser gets {@code CashRuleViolation.AlreadyOpen} and its transaction rolls back.
     */
    CashSession insert(String tenantId, CashSession session);
}
