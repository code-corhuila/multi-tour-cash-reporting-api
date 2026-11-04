package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Table cash_reporting.idempotency_key. The column created_at is filled by the database. */
@Entity
@Table(schema = "cash_reporting", name = "idempotency_key")
class IdempotencyKeyEntity {

    @Id @Column(name = "idempotency_key_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "key") String keyText;
    @Column(name = "resource_type") String resourceType;
    @Column(name = "resource_id") UUID resourceId;
}
