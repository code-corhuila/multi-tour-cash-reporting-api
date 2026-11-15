package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxDelivery;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxEntry;
import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import co.edu.corhuila.multi_tour.cash_reporting.port.Page;
import co.edu.corhuila.multi_tour.cash_reporting.port.PageRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** In-memory fake of the outbox: it keeps the appended events in order and the delivery state the relay moves (job 7). */
public final class InMemoryOutbox implements OutboxWriter, OutboxDelivery {

    /** One appended event. */
    public record Event(String tenantId, String eventType, String aggregateId, Map<String, Object> payload, Instant occurredAt) {}

    private static final Instant OCCURRED = Instant.parse("2026-11-03T12:00:00Z");
    private final List<Event> events = new ArrayList<>();
    private final List<OutboxEntry> rows = new ArrayList<>();

    public List<Event> events() {
        return events;
    }

    @Override
    public synchronized void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload) {
        events.add(new Event(tenantId, eventType, aggregateId, payload, null));
        rows.add(new OutboxEntry(UUID.randomUUID().toString(), eventType, tenantId, aggregateId, payload,
                OCCURRED.plusSeconds(rows.size()), null, "PENDING", 0));
    }

    /** Appends with the instant the caller fixes for the envelope (occurred_at), as the real writer does. */
    public synchronized void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload, Instant occurredAt) {
        events.add(new Event(tenantId, eventType, aggregateId, payload, occurredAt));
        rows.add(new OutboxEntry(UUID.randomUUID().toString(), eventType, tenantId, aggregateId, payload, occurredAt, null, "PENDING", 0));
    }

    @Override
    public synchronized Page<OutboxEntry> pending(PageRequest request) {
        List<OutboxEntry> pending = rows.stream().filter(row -> row.status().equals("PENDING")).toList();
        int from = Math.min(request.offset(), pending.size());
        return new Page<>(pending.subList(from, Math.min(from + request.limit(), pending.size())), request.page(), request.limit(),
                pending.size());
    }

    @Override
    public synchronized Optional<OutboxEntry> find(String eventId) {
        return rows.stream().filter(row -> row.eventId().equals(eventId)).findFirst();
    }

    @Override
    public synchronized boolean markPublished(String eventId, Instant at) {
        return replace(eventId, -1, row -> row.with("PUBLISHED", at, row.attempts()));
    }

    @Override
    public synchronized boolean recordAttempt(String eventId, int expectedAttempts, boolean failed) {
        return replace(eventId, expectedAttempts, row -> row.with(failed ? "FAILED" : "PENDING", null, row.attempts() + 1));
    }

    private boolean replace(String eventId, int expectedAttempts, UnaryOperator<OutboxEntry> change) {
        for (int i = 0; i < rows.size(); i++) {
            OutboxEntry row = rows.get(i);
            boolean sameAttempts = expectedAttempts < 0 || row.attempts() == expectedAttempts;
            if (row.eventId().equals(eventId) && row.status().equals("PENDING") && sameAttempts) {
                rows.set(i, change.apply(row));
                return true;
            }
        }
        return false;
    }
}
