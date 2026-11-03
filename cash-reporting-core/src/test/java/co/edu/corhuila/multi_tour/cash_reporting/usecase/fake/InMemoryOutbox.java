package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.OutboxWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** In-memory fake of the outbox: it keeps the appended events in order. */
public final class InMemoryOutbox implements OutboxWriter {

    /** One appended event. */
    public record Event(String tenantId, String eventType, String aggregateId, Map<String, Object> payload) {}

    private final List<Event> events = new ArrayList<>();

    public List<Event> events() {
        return events;
    }

    @Override
    public void append(String tenantId, String eventType, String aggregateId, Map<String, Object> payload) {
        events.add(new Event(tenantId, eventType, aggregateId, payload));
    }
}
