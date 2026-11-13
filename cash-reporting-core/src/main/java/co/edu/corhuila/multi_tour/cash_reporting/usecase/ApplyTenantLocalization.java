package co.edu.corhuila.multi_tour.cash_reporting.usecase;

import co.edu.corhuila.multi_tour.cash_reporting.port.ProcessedEvents;
import co.edu.corhuila.multi_tour.cash_reporting.port.TenantZoneRepository;
import co.edu.corhuila.multi_tour.cash_reporting.port.UnitOfWork;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Keeps the time zone of a tenant when identity-audit says it changed (TenantLocalizationChanged, DP-API-2): in one transaction
 * it records the event id and stores the zone, unless a later change is already stored. It applies only to what is created
 * afterwards; nothing already stored is moved. The tenant is the one the receiving route gives, never the payload's.
 */
public final class ApplyTenantLocalization {

    private final ProcessedEvents processed;
    private final TenantZoneRepository zones;
    private final UnitOfWork unitOfWork;

    public ApplyTenantLocalization(ProcessedEvents processed, TenantZoneRepository zones, UnitOfWork unitOfWork) {
        this.processed = Objects.requireNonNull(processed, "processed");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public ConsumeOutcome execute(String tenantId, String eventId, String eventType, ZoneId zone, Instant occurredAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(zone, "zone");
        return unitOfWork.inTransaction(() -> {
            if (!processed.firstTime(tenantId, eventId, eventType)) {
                return ConsumeOutcome.DUPLICATE;
            }
            zones.save(tenantId, zone, occurredAt);
            return ConsumeOutcome.APPLIED;
        });
    }
}
