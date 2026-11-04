package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Outbound port of the closures. Insert-only: a closure is history. Every method takes the tenant first. */
public interface CashClosureRepository {

    CashClosure insert(String tenantId, CashClosure closure);

    /** The most recent closure of the day, if it was ever closed. */
    Optional<CashClosure> findLatest(String tenantId, BusinessDate date);

    /** The latest closure of each given day that has one, in one read. */
    Map<BusinessDate, CashClosure> findLatestByDates(String tenantId, List<BusinessDate> dates);

    /** Every closing of the tenant, newest first: a reopened day has one per closing. */
    Page<CashClosure> list(String tenantId, PageRequest request);
}
