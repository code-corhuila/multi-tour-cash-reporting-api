package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Table cash_reporting.reservation_state: the last known state of a reservation, one row per tenant and reservation. */
@Entity
@Table(schema = "cash_reporting", name = "reservation_state")
class ReservationStateEntity {

    @Id @Column(name = "reservation_state_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "reservation_id") String reservationId;
    String status;
    @Column(name = "financial_situation") String financialSituation;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "last_event_occurred_at") Instant lastEventOccurredAt;
    @Column(name = "last_event_id") String lastEventId;
    @Column(name = "updated_at") Instant updatedAt;
}
