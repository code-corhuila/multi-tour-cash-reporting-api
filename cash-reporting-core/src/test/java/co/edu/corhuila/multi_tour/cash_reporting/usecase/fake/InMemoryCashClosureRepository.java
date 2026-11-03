package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.closure.CashClosure;
import co.edu.corhuila.multi_tour.cash_reporting.port.CashClosureRepository;
import java.util.ArrayList;
import java.util.List;
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
