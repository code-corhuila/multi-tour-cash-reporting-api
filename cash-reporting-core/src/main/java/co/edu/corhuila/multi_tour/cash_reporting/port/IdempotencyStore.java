package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.util.Optional;

/** Outbound port of the idempotency keys. A key belongs to one tenant: the same text of two tenants is two records. */
public interface IdempotencyStore {

    /** The resource a key already produced: its kind and its id. */
    record Stored(String resourceType, String resourceId) {}

    Optional<Stored> find(String tenantId, String key);

    void put(String tenantId, String key, String resourceType, String resourceId);
}
