package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import java.util.Optional;

/** Outbound port of the closures. Insert-only: a closure is history. Every method takes the tenant first. */
public interface CashClosureRepository {

    CashClosure insert(String tenantId, CashClosure closure);

    /** The most recent closure of the day, if it was ever closed. */
    Optional<CashClosure> findLatest(String tenantId, BusinessDate date);
}
