package co.edu.corhuila.multi_tour.cash_reporting.port;

/**
 * The key of the tenant is already stored: another writer committed the same Idempotency-Key between our read and our
 * write. The store raises it from its unique key; the use case answers the stored result as a replay.
 */
public final class IdempotencyKeyTaken extends RuntimeException {

    public IdempotencyKeyTaken(String tenantId, String key) {
        super("The Idempotency-Key is already stored for tenant " + tenantId);
    }
}
