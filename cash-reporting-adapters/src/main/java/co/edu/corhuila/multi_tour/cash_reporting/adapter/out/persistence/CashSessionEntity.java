package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.session.ClosingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Table cash_reporting.cash_session. The column created_at is filled by the database. */
@Entity
@Table(schema = "cash_reporting", name = "cash_session")
class CashSessionEntity {

    @Id @Column(name = "cash_session_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "business_date") LocalDate businessDate;
    @Column(name = "time_zone") String timeZone;
    String origin;
    String status;
    @Column(name = "opening_base_cents") Long openingBaseCents;
    @Column(name = "opened_at") Instant openedAt;
    @Column(name = "opened_by") UUID openedBy;
    @Column(name = "closed_at") Instant closedAt;
    @Column(name = "closing_mode") String closingMode;
    @Column(name = "closing_balance_cents") Long closingBalanceCents;
    @Column(name = "closed_by") UUID closedBy;
    @Column(name = "pending_closure") boolean pendingClosure;

    /** The database says FORMAL where the domain says MANUAL. */
    static String column(ClosingMode mode) {
        return mode == null ? null : mode == ClosingMode.MANUAL ? "FORMAL" : mode.name();
    }

    static ClosingMode mode(String column) {
        return column == null ? null : "FORMAL".equals(column) ? ClosingMode.MANUAL : ClosingMode.valueOf(column);
    }
}
