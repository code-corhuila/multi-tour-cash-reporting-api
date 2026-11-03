package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.util.Map;

/**
 * Outbound port of the outbox: appends an event in the same unit of work as the change. The payload holds ids, dates
 * and amounts only, never a concept, a reason or a name.
 */
public interface OutboxWriter {

    void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload);
}
