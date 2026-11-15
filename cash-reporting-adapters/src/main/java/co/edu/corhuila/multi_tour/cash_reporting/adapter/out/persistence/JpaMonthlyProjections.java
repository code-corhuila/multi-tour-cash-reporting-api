package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyProjection;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyTotals;
import co.edu.corhuila.multi_tour.cash_reporting.monthly.ReportedPeriod;
import co.edu.corhuila.multi_tour.cash_reporting.port.MonthlyProjections;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The monthly projection in PostgreSQL: one row per tenant and period, the tenant first in every query. The caller's transaction
 * (the processed event and this row commit together, ADR-004 D10) is the one this adapter joins.
 */
@Component
@Transactional
public class JpaMonthlyProjections implements MonthlyProjections {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<MonthlyProjection> find(String tenantId, ReportedPeriod period) {
        return row(tenantId, period).map(JpaMonthlyProjections::toProjection);
    }

    @Override
    public void save(String tenantId, ReportedPeriod period, MonthlyProjection projection) {
        MonthlyReportEntity row = row(tenantId, period).orElseGet(() -> {
            var created = new MonthlyReportEntity();
            created.id = UUID.randomUUID();
            created.tenantId = UUID.fromString(tenantId);
            created.startsOn = period.value().atDay(1);
            created.endsOn = period.value().atEndOfMonth();
            return created;
        });
        MonthlyTotals totals = projection.totals();
        row.incomeCents = totals.income().value();
        row.cashIncomeCents = totals.cashIncome().value();
        row.transferIncomeCents = totals.transferIncome().value();
        row.paymentsCents = totals.operationalPayments().value();
        row.expensesCents = totals.operationalExpenses().value();
        row.refundsCents = totals.executedRefunds().value();
        row.totalCents = totals.total().value();
        row.countedMovements = totals.movementCount();
        row.confirmedSalesCents = projection.confirmedSales() == null ? null : projection.confirmedSales().value();
        row.cancellations = new ArrayList<>();
        projection.cancelledReservations().stream().sorted()
                .forEach(id -> row.cancellations.add(Map.of("reservationId", id)));
        row.costs = new ArrayList<>();
        projection.costs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(cost -> row.costs.add(Map.of("costId", cost.getKey(), "amountCents", cost.getValue().value())));
        row.updatedAt = Instant.now();
        em.persist(row);
        em.flush();
    }

    private Optional<MonthlyReportEntity> row(String tenantId, ReportedPeriod period) {
        return em.createQuery("select r from MonthlyReportEntity r where r.tenantId = :tenant and r.startsOn = :start",
                        MonthlyReportEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .setParameter("start", period.value().atDay(1))
                .getResultStream()
                .findFirst();
    }

    static MonthlyProjection toProjection(MonthlyReportEntity row) {
        MonthlyTotals totals = new MonthlyTotals(Cents.of(row.cashIncomeCents), Cents.of(row.transferIncomeCents),
                Cents.of(row.paymentsCents), Cents.of(row.expensesCents), Cents.of(row.refundsCents), row.countedMovements);
        Set<String> cancelled = new LinkedHashSet<>();
        row.cancellations.forEach(item -> cancelled.add(String.valueOf(item.get("reservationId"))));
        Map<String, Cents> costs = new LinkedHashMap<>();
        row.costs.forEach(item ->
                costs.put(String.valueOf(item.get("costId")), Cents.of(((Number) item.get("amountCents")).longValue())));
        return new MonthlyProjection(totals, cancelled, costs,
                row.confirmedSalesCents == null ? null : Cents.of(row.confirmedSalesCents));
    }
}
