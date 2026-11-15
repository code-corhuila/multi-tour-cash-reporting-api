package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port, read side: the periods the projection holds for a tenant. Every method is scoped to the tenant of the token.
 * Only periods that have a row are here; the period without movements is a reading rule of the use case, never a stored row.
 */
public interface MonthlyReportSource {

    /** How many periods the tenant has stored. */
    long count(String tenantId);

    /** How many stored periods are newer than the given one. */
    long countNewerThan(String tenantId, ReportedPeriod period);

    /** A slice of the stored periods, newest first. */
    List<MonthlyPeriod> newestFirst(String tenantId, int offset, int limit);

    Optional<MonthlyPeriod> find(String tenantId, ReportedPeriod period);
}
