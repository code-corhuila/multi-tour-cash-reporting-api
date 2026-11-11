package co.edu.corhuila.multi_tour.cash_reporting.adapter.out.persistence;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Appends the event in the transaction of the change; a name outside the ten of events.md and the event catalog fails the table check. */
@Component
@Transactional
public class JpaOutboxWriter implements OutboxWriter {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private Clock clock;

    @Override
    public void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload) {
        var row = new OutboxEventEntity();
        row.id = UUID.randomUUID();
        row.tenantId = UUID.fromString(tenantId);
        row.eventType = eventType;
        row.aggregateId = ResourceIds.toUuid(em, tenantId, aggregateId);
        row.payload = payload;
        row.occurredAt = clock.instant();
        row.status = "PENDING";
        row.attempts = 0;
        em.persist(row);
        em.flush();
    }
}
