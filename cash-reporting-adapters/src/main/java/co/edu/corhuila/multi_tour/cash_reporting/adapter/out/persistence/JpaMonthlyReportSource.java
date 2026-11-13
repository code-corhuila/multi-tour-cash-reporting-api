package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyReportSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The periods the projection holds, read for the tenant of the token only: the tenant leads every query. */
@Component
@Transactional(readOnly = true)
public class JpaMonthlyReportSource implements MonthlyReportSource {

    @PersistenceContext
    private EntityManager em;

    @Override
    public long count(String tenantId) {
        return em.createQuery("select count(r) from MonthlyReportEntity r where r.tenantId = :tenant", Long.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .getSingleResult();
    }

    @Override
    public long countNewerThan(String tenantId, ReportedPeriod period) {
        return em.createQuery(
                        "select count(r) from MonthlyReportEntity r where r.tenantId = :tenant and r.startsOn > :start", Long.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("start", period.value().atDay(1))
                .getSingleResult();
    }

    @Override
    public List<MonthlyPeriod> newestFirst(String tenantId, int offset, int limit) {
        return em.createQuery("select r from MonthlyReportEntity r where r.tenantId = :tenant order by r.startsOn desc",
                        MonthlyReportEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(JpaMonthlyReportSource::toPeriod)
                .toList();
    }

    @Override
    public Optional<MonthlyPeriod> find(String tenantId, ReportedPeriod period) {
        return em.createQuery("select r from MonthlyReportEntity r where r.tenantId = :tenant and r.startsOn = :start",
                        MonthlyReportEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("start", period.value().atDay(1))
                .getResultStream()
                .findFirst()
                .map(JpaMonthlyReportSource::toPeriod);
    }

    private static MonthlyPeriod toPeriod(MonthlyReportEntity row) {
        return JpaMonthlyProjections.toProjection(row).toPeriod(ReportedPeriod.of(YearMonth.from(row.startsOn)));
    }
}
