package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Table cash_reporting.cash_session_closure: the snapshot taken at close, insert-only. */
@Entity
@Table(schema = "cash_reporting", name = "cash_session_closure")
class CashClosureEntity {

    @Id @Column(name = "cash_session_closure_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "cash_session_id") UUID sessionId;
    @Column(name = "closed_at") Instant closedAt;
    @Column(name = "closing_mode") String closingMode;
    @Column(name = "closed_by") UUID closedBy;
    @Column(name = "opening_base_cents") Long openingBaseCents;
    @Column(name = "income_cents") long incomeCents;
    @Column(name = "operational_payments_cents") long operationalPaymentsCents;
    @Column(name = "operational_expenses_cents") long operationalExpensesCents;
    @Column(name = "refunds_cents") long refundsCents;
    @Column(name = "cash_entries_cents") long cashEntriesCents;
    @Column(name = "transfer_entries_cents") long transferEntriesCents;
    @Column(name = "cash_exits_cents") long cashExitsCents;
    @Column(name = "transfer_exits_cents") long transferExitsCents;
    @Column(name = "expected_cash_cents") Long expectedCashCents;
    @Column(name = "closing_balance_cents") long closingBalanceCents;
}
