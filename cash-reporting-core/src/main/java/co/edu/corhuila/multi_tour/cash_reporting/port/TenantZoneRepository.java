package co.edu.corhuila.multi_tour.cash_reporting.port;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Outbound port: the time zone each tenant has (table {@code tenant_time_zone}, at most one row per tenant). A tenant without a
 * row uses the zone the service is configured with. A change applies only to what is created afterwards (DP-API-2).
 */
public interface TenantZoneRepository {

    Optional<ZoneId> find(String tenantId);

    /** Stores the zone unless one set by a later event is already there; answers whether it was stored. */
    boolean save(String tenantId, ZoneId zone, Instant occurredAt);
}
