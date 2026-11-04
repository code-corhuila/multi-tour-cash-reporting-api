package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Table cash_reporting.cash_movement. A movement is a fact: the entity is immutable and never rewritten. */
@Entity
@Immutable
@Table(schema = "cash_reporting", name = "cash_movement")
class CashMovementEntity {

    @Id @Column(name = "cash_movement_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "cash_session_id") UUID sessionId;
    @Column(name = "movement_type") String type;
    @Column(name = "payment_medium") String medium;
    @Column(name = "amount_cents") Long amountCents;
    @Column(name = "after_closing") boolean afterClosing;
    @Column(name = "registered_at") Instant registeredAt;
    @Column(name = "registered_by") UUID registeredBy;
    String concept;
}
