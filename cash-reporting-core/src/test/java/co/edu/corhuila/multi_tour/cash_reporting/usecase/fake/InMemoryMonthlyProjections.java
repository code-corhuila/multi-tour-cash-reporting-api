package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyProjections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The monthly projection rows in memory, keyed by tenant and period, as the table is. */
public final class InMemoryMonthlyProjections implements MonthlyProjections {

    private final Map<String, MonthlyProjection> rows = new HashMap<>();

    private static String key(String tenantId, ReportedPeriod period) {
        return tenantId + "|" + period;
    }

    public int rows() {
        return rows.size();
    }

    @Override
    public Optional<MonthlyProjection> find(String tenantId, ReportedPeriod period) {
        return Optional.ofNullable(rows.get(key(tenantId, period)));
    }

    @Override
    public void save(String tenantId, ReportedPeriod period, MonthlyProjection projection) {
        rows.put(key(tenantId, period), projection);
    }
}
