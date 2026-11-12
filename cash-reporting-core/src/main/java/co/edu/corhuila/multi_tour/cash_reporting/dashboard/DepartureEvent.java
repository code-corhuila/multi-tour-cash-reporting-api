package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import java.time.Instant;
import java.util.Objects;

/** A state event of a departure: the full new state, its scheduled start when it has one, and when it happened. */
public record DepartureEvent(
        String eventId, String departureId, DepartureStatus status, Instant scheduledStart, Instant occurredAt) {

    public DepartureEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(departureId, "departureId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static DepartureEvent scheduled(
            String eventId, String departureId, Instant scheduledStart, Instant occurredAt) {
        return new DepartureEvent(eventId, departureId, DepartureStatus.SCHEDULED, scheduledStart, occurredAt);
    }

    /** A state change that does not carry a start: the one already known is kept. */
    public static DepartureEvent state(String eventId, String departureId, DepartureStatus status, Instant occurredAt) {
        return new DepartureEvent(eventId, departureId, status, null, occurredAt);
    }
}
