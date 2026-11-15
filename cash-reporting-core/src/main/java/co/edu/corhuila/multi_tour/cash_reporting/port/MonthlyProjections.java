package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import java.util.Optional;

/**
 * Outbound port, write side of the monthly projection (table {@code monthly_administrative_report}): one row per tenant and
 * period. It is read and written in the same transaction as the processed event of the fact that changes it (ADR-004 D10).
 */
public interface MonthlyProjections {

    Optional<MonthlyProjection> find(String tenantId, ReportedPeriod period);

    void save(String tenantId, ReportedPeriod period, MonthlyProjection projection);
}
