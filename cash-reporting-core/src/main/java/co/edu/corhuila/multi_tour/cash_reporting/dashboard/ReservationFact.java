package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import java.time.Instant;
import java.util.Objects;

/**
 * The last known state of a reservation, built from events that arrive at least once and out of order. The state is the one
 * of the newest event (equal instants are decided by the event id, so the result is deterministic); the creation instant is
 * the one of the creation event whenever it arrives; the cancellation instant is when the reservation reached CANCELLED.
 * Applying the same event again, or an older one, changes nothing.
 */
public record ReservationFact(
        String reservationId,
        ReservationStatus status,
        String financialSituation,
        Instant createdAt,
        Instant lastEventOccurredAt,
        String lastEventId) {

    public ReservationFact {
        Objects.requireNonNull(reservationId, "reservationId");
    }

    public static ReservationFact empty(String reservationId) {
        return new ReservationFact(reservationId, null, null, null, null, null);
    }

    public ReservationFact apply(ReservationEvent event) {
        Instant created = createdAt;
        if (event.creation() && (created == null || event.occurredAt().isBefore(created))) {
            created = event.occurredAt();
        }
        if (!isNewer(event)) {
            return new ReservationFact(reservationId, status, financialSituation, created, lastEventOccurredAt, lastEventId);
        }
        return new ReservationFact(
                reservationId, event.status(), event.financialSituation(), created, event.occurredAt(), event.eventId());
    }

    /** When the reservation was cancelled: the instant of the event that left it CANCELLED, null otherwise. */
    public Instant cancelledAt() {
        return status == ReservationStatus.CANCELLED ? lastEventOccurredAt : null;
    }

    private boolean isNewer(ReservationEvent event) {
        if (lastEventOccurredAt == null) {
            return true;
        }
        int order = event.occurredAt().compareTo(lastEventOccurredAt);
        return order > 0 || (order == 0 && event.eventId().compareTo(lastEventId) > 0);
    }
}
