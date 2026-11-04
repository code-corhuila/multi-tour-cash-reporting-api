package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of the closure port: insert-only, it counts the writes. */
public final class InMemoryCashClosureRepository implements CashClosureRepository {

    private final List<CashClosure> rows = new ArrayList<>();
    private int writes;

    public int writes() {
        return writes;
    }

    @Override
    public CashClosure insert(String tenantId, CashClosure closure) {
        writes++;
        rows.add(closure);
        return closure;
    }

    @Override
    public Page<CashClosure> list(String tenantId, PageRequest request) {
        List<CashClosure> found = new ArrayList<>(
                rows.stream().filter(row -> row.tenantId().equals(tenantId)).toList());
        Collections.reverse(found);
        found.sort(Comparator.comparing(CashClosure::closedAt).reversed());
        return Pages.slice(found, request);
    }

    @Override
    public Map<BusinessDate, CashClosure> findLatestByDates(String tenantId, List<BusinessDate> dates) {
        Map<BusinessDate, CashClosure> latest = new HashMap<>();
        dates.forEach(date -> findLatest(tenantId, date).ifPresent(closure -> latest.put(date, closure)));
        return latest;
    }

    /** Every closure of the day, oldest first: a reopened day keeps each one. */
    public List<CashClosure> all(String tenantId, BusinessDate date) {
        return rows.stream()
                .filter(row -> row.tenantId().equals(tenantId) && row.date().equals(date))
                .toList();
    }

    @Override
    public Optional<CashClosure> findLatest(String tenantId, BusinessDate date) {
        CashClosure latest = null;
        for (CashClosure row : rows) {
            if (row.tenantId().equals(tenantId) && row.date().equals(date)) {
                latest = row;
            }
        }
        return Optional.ofNullable(latest);
    }
}
