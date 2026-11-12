package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import java.time.Instant;
import java.util.Objects;

/** The last known state of a departure, ordered like {@link ReservationFact}; a change without a start keeps the known one. */
public record DepartureFact(
        String departureId, DepartureStatus status, Instant scheduledStart, Instant lastEventOccurredAt, String lastEventId) {

    public DepartureFact {
        Objects.requireNonNull(departureId, "departureId");
    }

    public static DepartureFact empty(String departureId) {
        return new DepartureFact(departureId, null, null, null, null);
    }

    public DepartureFact apply(DepartureEvent event) {
        Instant start = event.scheduledStart() != null ? event.scheduledStart() : scheduledStart;
        if (!isNewer(event)) {
            return new DepartureFact(departureId, status, start, lastEventOccurredAt, lastEventId);
        }
        return new DepartureFact(departureId, event.status(), start, event.occurredAt(), event.eventId());
    }

    private boolean isNewer(DepartureEvent event) {
        if (lastEventOccurredAt == null) {
            return true;
        }
        int order = event.occurredAt().compareTo(lastEventOccurredAt);
        return order > 0 || (order == 0 && event.eventId().compareTo(lastEventId) > 0);
    }
}
