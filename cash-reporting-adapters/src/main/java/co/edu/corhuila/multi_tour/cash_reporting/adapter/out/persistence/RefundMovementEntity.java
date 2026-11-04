package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Table cash_reporting.refund_movement: the detail of a REFUND. Its column movement_type is generated, not mapped. */
@Entity
@Immutable
@Table(schema = "cash_reporting", name = "refund_movement")
class RefundMovementEntity {

    @Id @Column(name = "refund_movement_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "cash_session_id") UUID sessionId;
    @Column(name = "cash_movement_id") UUID movementId;
    @Column(name = "refund_authorization_id") UUID authorizationId;
    @Column(name = "reservation_id") UUID reservationId;
    @Column(name = "amount_cents") Long amountCents;
    String reason;
    @Column(name = "executed_by") UUID executedBy;
    @Column(name = "outflow_method") String outflowMethod;
    @Column(name = "executed_at") Instant executedAt;
}
