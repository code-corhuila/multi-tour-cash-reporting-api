package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The stored periods in memory, keyed by tenant first and ordered by period, as the table is. */
public final class InMemoryMonthlyReportSource implements MonthlyReportSource {

    private final Map<String, TreeMap<ReportedPeriod, MonthlyPeriod>> rows = new HashMap<>();

    public void put(String tenantId, MonthlyPeriod period) {
        rows.computeIfAbsent(tenantId, t -> new TreeMap<>()).put(period.period(), period);
    }

    @Override
    public long count(String tenantId) {
        return rows.getOrDefault(tenantId, new TreeMap<>()).size();
    }

    @Override
    public long countNewerThan(String tenantId, ReportedPeriod period) {
        return rows.getOrDefault(tenantId, new TreeMap<>()).tailMap(period, false).size();
    }

    @Override
    public List<MonthlyPeriod> newestFirst(String tenantId, int offset, int limit) {
        return rows.getOrDefault(tenantId, new TreeMap<>()).descendingMap().values().stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<MonthlyPeriod> find(String tenantId, ReportedPeriod period) {
        return Optional.ofNullable(rows.getOrDefault(tenantId, new TreeMap<>()).get(period));
    }
}
