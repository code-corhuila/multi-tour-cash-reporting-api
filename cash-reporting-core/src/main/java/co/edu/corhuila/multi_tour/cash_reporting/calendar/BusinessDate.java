package co.edu.corhuila.multi_tour.cash_reporting.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** A calendar day of a tenant: the day a cash belongs to. */
public record BusinessDate(LocalDate value) {

    public BusinessDate {
        Objects.requireNonNull(value, "value");
    }

    public static BusinessDate of(LocalDate value) {
        return new BusinessDate(value);
    }

    public static BusinessDate of(Instant instant, ZoneId zone) {
        return new BusinessDate(instant.atZone(zone).toLocalDate());
    }

    public boolean isBefore(BusinessDate other) {
        return value.isBefore(other.value);
    }

    public boolean isAfter(BusinessDate other) {
        return value.isAfter(other.value);
    }
}
