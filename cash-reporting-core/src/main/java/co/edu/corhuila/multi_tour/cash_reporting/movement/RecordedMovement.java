package co.edu.corhuila.multi_tour.cash_reporting.movement;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.Instant;
import java.util.Objects;

/**
 * A movement as stored: the value that counts in the totals plus its id, its day, the concept, who and when, whether it
 * was recorded after the closing, and the refund detail when it is a REFUND. It carries no state: a stored row counts.
 */
public record RecordedMovement(
        String id,
        BusinessDate date,
        CashMovement movement,
        String concept,
        boolean afterClosing,
        Instant registeredAt,
        String registeredBy,
        RefundDetail refund) {

    public RecordedMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(concept, "concept");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(registeredBy, "registeredBy");
        if ((movement.type() == MovementType.REFUND) != (refund != null)) {
            throw new IllegalArgumentException("Only a REFUND carries a refund detail, and it always does");
        }
    }
}
