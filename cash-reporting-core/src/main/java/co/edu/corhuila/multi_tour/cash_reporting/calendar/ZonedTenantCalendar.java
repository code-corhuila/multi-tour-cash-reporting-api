package co.edu.corhuila.multi_tour.cash_reporting.calendar;

import java.time.ZoneId;
import java.util.Objects;

/** Today is the instant of the clock seen in the zone of the tenant. */
public final class ZonedTenantCalendar implements TenantCalendar {

    private final Clock clock;
    private final TenantZones zones;

    public ZonedTenantCalendar(Clock clock, TenantZones zones) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    @Override
    public ZoneId zoneOf(String tenantId) {
        return zones.of(tenantId);
    }

    @Override
    public BusinessDate today(String tenantId) {
        return BusinessDate.of(clock.now(), zoneOf(tenantId));
    }
}
