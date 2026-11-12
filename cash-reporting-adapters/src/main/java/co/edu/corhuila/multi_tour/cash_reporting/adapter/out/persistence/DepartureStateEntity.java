package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Table cash_reporting.departure_state: the last known state of a departure, one row per tenant and departure. */
@Entity
@Table(schema = "cash_reporting", name = "departure_state")
class DepartureStateEntity {

    @Id @Column(name = "departure_state_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "departure_id") String departureId;
    String status;
    @Column(name = "scheduled_start") Instant scheduledStart;
    @Column(name = "last_event_occurred_at") Instant lastEventOccurredAt;
    @Column(name = "last_event_id") String lastEventId;
    @Column(name = "updated_at") Instant updatedAt;
}
