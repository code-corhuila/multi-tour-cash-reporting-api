package co.edu.corhuila.multi_tour.cash_reporting.adapter.in.http;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** The wire shape of the outbox routes of job 7. */
final class OutboxViews {

    private OutboxViews() {}

    /** OutboxEvent: the envelope of ADR-004 D4 and the delivery state. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OutboxEventResponse(String eventId, String eventType, int eventVersion, String tenantId, String aggregateId,
            Map<String, Object> payload, String occurredAt, String publishedAt, String status, int attempts) {
        static OutboxEventResponse of(OutboxEntry e) {
            return new OutboxEventResponse(e.eventId(), e.eventType(), 1, e.tenantId(), e.aggregateId(), e.payload(), e.occurredAt().toString(),
                    e.publishedAt() == null ? null : e.publishedAt().toString(), e.status(), e.attempts());
        }
    }
}
