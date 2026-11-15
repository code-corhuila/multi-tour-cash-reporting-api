package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Table cash_reporting.outbox_event. This service appends; job 7 moves the later states through the internal outbox routes (JpaOutboxDelivery). */
@Entity
@Table(schema = "cash_reporting", name = "outbox_event")
class OutboxEventEntity {

    @Id @Column(name = "outbox_event_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "event_type") String eventType;
    @Column(name = "aggregate_id") UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON) Map<String, Object> payload;
    @Column(name = "occurred_at") Instant occurredAt;
    String status;
    int attempts;
    @Column(name = "published_at") Instant publishedAt;
}
