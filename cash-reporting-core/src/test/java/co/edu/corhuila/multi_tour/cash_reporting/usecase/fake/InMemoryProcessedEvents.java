package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import java.util.HashSet;
import java.util.Set;

/** The processed event ids in memory: unique per tenant and event, like the table. */
public final class InMemoryProcessedEvents implements ProcessedEvents {

    private final Set<String> seen = new HashSet<>();

    public int count() {
        return seen.size();
    }

    @Override
    public boolean firstTime(String tenantId, String eventId, String eventType) {
        return seen.add(tenantId + "|" + eventId);
    }
}
