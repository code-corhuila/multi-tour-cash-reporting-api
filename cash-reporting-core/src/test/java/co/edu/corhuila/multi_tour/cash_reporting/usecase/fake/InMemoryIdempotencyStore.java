package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory fake of the key store: per tenant and key, with a count of the writes. */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Stored> rows = new HashMap<>();
    private int writes;

    public int writes() {
        return writes;
    }

    @Override
    public Optional<Stored> find(String tenantId, String key) {
        return Optional.ofNullable(rows.get(tenantId + "|" + key));
    }

    @Override
    public void put(String tenantId, String key, String resourceType, String resourceId) {
        writes++;
        rows.put(tenantId + "|" + key, new Stored(resourceType, resourceId));
    }
}
