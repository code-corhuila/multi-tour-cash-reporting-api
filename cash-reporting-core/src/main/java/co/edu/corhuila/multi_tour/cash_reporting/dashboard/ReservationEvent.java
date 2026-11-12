package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import java.time.Instant;
import java.util.Objects;

/**
 * A state event of a reservation: it carries the full new state and when it happened (events.md: at least once, not
 * ordered). {@code creation} marks the event that registered the reservation, which fixes its creation instant.
 */
public record ReservationEvent(
        String eventId,
        String reservationId,
        ReservationStatus status,
        String financialSituation,
        Instant occurredAt,
        boolean creation) {

    public ReservationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The event of a new reservation: it starts pending payment. */
    public static ReservationEvent created(String eventId, String reservationId, Instant occurredAt) {
        return new ReservationEvent(eventId, reservationId, ReservationStatus.PENDING_PAYMENT, null, occurredAt, true);
    }

    public static ReservationEvent state(
            String eventId, String reservationId, ReservationStatus status, String financialSituation, Instant occurredAt) {
        return new ReservationEvent(eventId, reservationId, status, financialSituation, occurredAt, false);
    }
}
