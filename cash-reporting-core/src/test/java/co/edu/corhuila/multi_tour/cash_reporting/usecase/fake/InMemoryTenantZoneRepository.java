package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.port.TenantZoneRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The zones in memory, keyed by tenant, with the instant of the event that set each one. */
public final class InMemoryTenantZoneRepository implements TenantZoneRepository {

    private record Stored(ZoneId zone, Instant at) {}

    private final Map<String, Stored> rows = new HashMap<>();

    @Override
    public Optional<ZoneId> find(String tenantId) {
        return Optional.ofNullable(rows.get(tenantId)).map(Stored::zone);
    }

    @Override
    public boolean save(String tenantId, ZoneId zone, Instant occurredAt) {
        Stored current = rows.get(tenantId);
        if (current != null && current.at().isAfter(occurredAt)) {
            return false;
        }
        rows.put(tenantId, new Stored(zone, occurredAt));
        return true;
    }
}
