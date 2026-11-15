package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/** One row of the outbox as job 7 sees it: the envelope of the event and its delivery state (PENDING, PUBLISHED or FAILED). */
public record OutboxEntry(String eventId, String eventType, String tenantId, String aggregateId, Map<String, Object> payload,
        Instant occurredAt, Instant publishedAt, String status, int attempts) {

    public OutboxEntry {
        payload = Collections.unmodifiableMap(payload);
    }

    /** The same event in another delivery state. */
    public OutboxEntry with(String newStatus, Instant newPublishedAt, int newAttempts) {
        return new OutboxEntry(eventId, eventType, tenantId, aggregateId, payload, occurredAt, newPublishedAt, newStatus, newAttempts);
    }
}
