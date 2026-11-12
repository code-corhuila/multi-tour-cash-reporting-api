package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The consumed event ids in PostgreSQL. The insert does nothing on a repeat, so a duplicate does not abort the transaction of
 * the consumer: the unique key of tenant and event decides, and the answer is whether the row was new.
 */
@Component
@Transactional
public class JpaProcessedEvents implements ProcessedEvents {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean firstTime(String tenantId, String eventId, String eventType) {
        int inserted = em.createNativeQuery(
                        "insert into cash_reporting.processed_event (tenant_id, event_id, event_type, processed_at)"
                                + " values (?1, ?2, ?3, now()) on conflict (tenant_id, event_id) do nothing")
                .setParameter(1, UUID.fromString(tenantId))
                .setParameter(2, UUID.fromString(eventId))
                .setParameter(3, eventType)
                .executeUpdate();
        return inserted == 1;
    }
}
