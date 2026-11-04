package co.edu.corhuila.multi_tour.cash_reporting.calendar;

import java.time.Instant;

/** The service clock, explicit so that tests choose the instant. */
@FunctionalInterface
public interface Clock {

    Instant now();
}
