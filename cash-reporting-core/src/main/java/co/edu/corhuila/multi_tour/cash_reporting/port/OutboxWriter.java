package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.time.Instant;
import java.util.Map;

/**
 * Outbound port of the outbox: appends an event in the same unit of work as the change. The payload holds ids, dates
 * and amounts only, never a concept, a reason or a name.
 */
public interface OutboxWriter {

    void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload);

    /**
     * Appends an event whose envelope instant (occurred_at) is the one the caller fixes, for an event that also carries that same instant in its payload.
     * The default ignores the instant, so a writer that stamps its own keeps working.
     */
    default void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload, Instant occurredAt) {
        append(tenantId, eventType, aggregateId, payload);
    }
}
