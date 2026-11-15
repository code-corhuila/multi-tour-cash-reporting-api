package co.edu.corhuila.multi_tour.cash_reporting.calendar;

import java.time.ZoneId;

/** Port: where the zone of a tenant comes from. NO DEFINIDO (H-06): adapters use a fake until it is closed. */
@FunctionalInterface
public interface TenantZones {

    ZoneId of(String tenantId);
}
