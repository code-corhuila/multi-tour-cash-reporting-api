package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Table cash_reporting.cash_closure_correction: the note of a closed cash, insert-only. */
@Entity
@Table(schema = "cash_reporting", name = "cash_closure_correction")
class CashClosureCorrectionEntity {

    @Id @Column(name = "cash_closure_correction_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "cash_session_id") UUID sessionId;
    @Column(name = "cash_session_closure_id") UUID closureId;
    String justification;
    @Column(name = "registered_by") UUID registeredBy;
    @Column(name = "registered_at") Instant registeredAt;
}
