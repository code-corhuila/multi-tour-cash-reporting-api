package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Table cash_reporting.monthly_administrative_report: the projection of one month of one tenant (one row per tenant and period).
 * The total and the income are derived columns the table checks; the sales stay null until a source gives a value.
 */
@Entity
@Table(schema = "cash_reporting", name = "monthly_administrative_report")
class MonthlyReportEntity {

    @Id @Column(name = "monthly_report_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "starts_on") LocalDate startsOn;
    @Column(name = "ends_on") LocalDate endsOn;
    @Column(name = "period_income_cents") long incomeCents;
    @Column(name = "operational_payments_cents") long paymentsCents;
    @Column(name = "operational_expenses_cents") long expensesCents;
    @Column(name = "executed_refunds_cents") long refundsCents;
    @Column(name = "consolidated_cash_total_cents") long totalCents;
    @Column(name = "period_income_cash_cents") long cashIncomeCents;
    @Column(name = "period_income_transfer_cents") long transferIncomeCents;
    @Column(name = "counted_movements") int countedMovements;
    @Column(name = "confirmed_sales_cents") Long confirmedSalesCents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registered_cancellations") List<Map<String, Object>> cancellations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registered_operational_costs") List<Map<String, Object>> costs;

    @Column(name = "updated_at") Instant updatedAt;
}
