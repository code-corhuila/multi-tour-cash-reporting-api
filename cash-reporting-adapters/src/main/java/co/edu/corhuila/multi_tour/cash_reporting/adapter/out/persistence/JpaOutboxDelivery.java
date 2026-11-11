package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxDelivery;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxEntry;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The delivery side of cash_reporting.outbox_event for job 7: the pending rows of every tenant (the declared exception to the tenant filter, plan D-10)
 * and the two UPDATEs of the relay, each guarded by status PENDING so the table check and this class agree on what may change.
 */
@Component
public class JpaOutboxDelivery implements OutboxDelivery {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Page<OutboxEntry> pending(PageRequest request) {
        long total = em.createQuery("select count(e) from OutboxEventEntity e where e.status = 'PENDING'", Long.class).getSingleResult();
        var rows = em.createQuery("select e from OutboxEventEntity e where e.status = 'PENDING' order by e.occurredAt asc, e.id asc",
                OutboxEventEntity.class).setFirstResult(request.offset()).setMaxResults(request.limit()).getResultList();
        return new Page<>(rows.stream().map(JpaOutboxDelivery::toEntry).toList(), request.page(), request.limit(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEntry> find(String eventId) {
        var rows = em.createQuery("select e from OutboxEventEntity e where e.id = :id", OutboxEventEntity.class)
                .setParameter("id", UUID.fromString(eventId)).getResultList();
        return rows.stream().findFirst().map(JpaOutboxDelivery::toEntry);
    }

    @Override
    @Transactional
    public boolean markPublished(String eventId, Instant at) {
        return em.createQuery("update OutboxEventEntity e set e.status = 'PUBLISHED', e.publishedAt = :at where e.id = :id and e.status = 'PENDING'")
                .setParameter("at", at).setParameter("id", UUID.fromString(eventId)).executeUpdate() == 1;
    }

    @Override
    @Transactional
    public boolean recordAttempt(String eventId, int expectedAttempts, boolean failed) {
        return em.createQuery("update OutboxEventEntity e set e.attempts = e.attempts + 1, e.status = :status "
                + "where e.id = :id and e.attempts = :expected and e.status = 'PENDING'")
                .setParameter("status", failed ? "FAILED" : "PENDING").setParameter("id", UUID.fromString(eventId))
                .setParameter("expected", expectedAttempts).executeUpdate() == 1;
    }

    private static OutboxEntry toEntry(OutboxEventEntity row) {
        return new OutboxEntry(row.id.toString(), row.eventType, row.tenantId.toString(), row.aggregateId.toString(), row.payload, row.occurredAt,
                row.publishedAt, row.status, row.attempts);
    }
}
