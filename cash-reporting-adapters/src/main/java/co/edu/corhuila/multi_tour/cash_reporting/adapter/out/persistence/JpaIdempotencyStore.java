package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The keys in PostgreSQL. The unique key (tenant, key) makes the same text of two tenants two records. */
@Component
@Transactional
public class JpaIdempotencyStore implements IdempotencyStore {

    /** The kinds of key whose resource is a cash day: the table holds the id of the cash, the use case wants the date. */
    private static final java.util.Set<String> CASH_DAY = java.util.Set.of("OPEN_CASH_DAY", "CLOSE_CASH_DAY", "REOPEN_CASH_DAY");

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<Stored> find(String tenantId, String key) {
        UUID tenant = UUID.fromString(tenantId);
        return em.createQuery(
                        "select k from IdempotencyKeyEntity k where k.tenantId = :tenant and k.keyText = :key",
                        IdempotencyKeyEntity.class)
                .setParameter("tenant", tenant)
                .setParameter("key", key)
                .getResultStream()
                .findFirst()
                .map(row -> new Stored(row.resourceType, resourceOf(tenant, row)));
    }

    @Override
    public void put(String tenantId, String key, String resourceType, String resourceId) {
        var row = new IdempotencyKeyEntity();
        row.id = UUID.randomUUID();
        row.tenantId = UUID.fromString(tenantId);
        row.keyText = key;
        row.resourceType = resourceType;
        row.resourceId = ResourceIds.toUuid(em, tenantId, resourceId);
        em.persist(row);
        em.flush();
    }

    private String resourceOf(UUID tenant, IdempotencyKeyEntity row) {
        if (row.resourceId == null || !CASH_DAY.contains(row.resourceType)) {
            return Objects.toString(row.resourceId, null);
        }
        return em.createQuery(
                        "select s.businessDate from CashSessionEntity s where s.id = :id and s.tenantId = :tenant",
                        LocalDate.class)
                .setParameter("id", row.resourceId)
                .setParameter("tenant", tenant)
                .getSingleResult()
                .toString();
    }
}
