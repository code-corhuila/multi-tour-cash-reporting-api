package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Table cash_reporting.cash_session_reopening: who, when and why, insert-only. */
@Entity
@Table(schema = "cash_reporting", name = "cash_session_reopening")
class CashReopeningEntity {

    @Id @Column(name = "cash_session_reopening_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "cash_session_id") UUID sessionId;
    @Column(name = "cash_session_closure_id") UUID closureId;
    @Column(name = "reopened_by") UUID reopenedBy;
    @Column(name = "reopened_at") Instant reopenedAt;
    String reason;
}
