package co.edu.corhuila.multi_tour.cash_reporting.calendar;

import java.time.ZoneId;

/** Port: the calendar of a tenant. "Today" is decided by the service clock and the tenant zone, never by the client. */
public interface TenantCalendar {

    ZoneId zoneOf(String tenantId);

    BusinessDate today(String tenantId);

    /** A date after today is never valid (the opening and the movements only accept today). */
    default void requireNotAfterToday(String tenantId, BusinessDate date) {
        if (date.isAfter(today(tenantId))) {
            throw new IllegalArgumentException("The date is after today: " + date.value());
        }
    }
}
