package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.TenantZoneRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The zone of each tenant in PostgreSQL: one row per tenant, the tenant first. The row keeps the instant of the event that set
 * it (the column updated_at), so a change that arrives late never overrides a later one.
 */
@Component
@Transactional
public class JpaTenantZoneRepository implements TenantZoneRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<ZoneId> find(String tenantId) {
        return row(tenantId).map(row -> ZoneId.of(row.timeZone));
    }

    @Override
    public boolean save(String tenantId, ZoneId zone, Instant occurredAt) {
        TenantZoneEntity row = row(tenantId).orElseGet(() -> {
            var created = new TenantZoneEntity();
            created.id = UUID.randomUUID();
            created.tenantId = UUID.fromString(tenantId);
            return created;
        });
        if (row.updatedAt != null && row.updatedAt.isAfter(occurredAt)) {
            return false;
        }
        row.timeZone = zone.getId();
        row.updatedAt = occurredAt;
        em.persist(row);
        em.flush();
        return true;
    }

    private Optional<TenantZoneEntity> row(String tenantId) {
        return em.createQuery("select z from TenantZoneEntity z where z.tenantId = :tenant", TenantZoneEntity.class)
                .setParameter("tenant", UUID.fromString(tenantId))
                .getResultStream()
                .findFirst();
    }
}
