package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.session.CashSession;
import java.util.Optional;

/** Outbound port of the cash days. Every method takes the tenant first: no query without it. */
public interface CashSessionRepository {

    Optional<CashSession> find(String tenantId, BusinessDate date);

    /** Same read, but locking the row for the transaction (plan D-08). */
    Optional<CashSession> findForUpdate(String tenantId, BusinessDate date);

    CashSession save(String tenantId, CashSession session);
}
