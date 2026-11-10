package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyKeyTaken;
import co.edu.corhuila.multi_tour.cash_reporting.port.IdempotencyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake of the key store: per tenant and key, with a count of the writes. Like the unique key of the database,
 * a second put of the same key is refused with {@link IdempotencyKeyTaken}; {@link #blindNextFind()} makes the next read
 * answer "no key" as a writer that read just before another one committed.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Stored> rows = new HashMap<>();
    private int writes;
    private boolean blind;

    public int writes() {
        return writes;
    }

    /** The next find answers empty although the key exists: the loser of a race. */
    public void blindNextFind() {
        blind = true;
    }

    @Override
    public Optional<Stored> find(String tenantId, String key) {
        if (blind) {
            blind = false;
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(tenantId + "|" + key));
    }

    @Override
    public void put(String tenantId, String key, String resourceType, String resourceId) {
        if (rows.containsKey(tenantId + "|" + key)) {
            throw new IdempotencyKeyTaken(tenantId, key);
        }
        writes++;
        rows.put(tenantId + "|" + key, new Stored(resourceType, resourceId));
    }
}
