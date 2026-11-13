package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Table cash_reporting.tenant_time_zone: the zone of a tenant, at most one row per tenant. */
@Entity
@Table(schema = "cash_reporting", name = "tenant_time_zone")
class TenantZoneEntity {

    @Id @Column(name = "tenant_time_zone_id") UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "time_zone") String timeZone;
    @Column(name = "updated_at") Instant updatedAt;
}
