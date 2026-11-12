package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.time.Instant;

/** Red-phase skeleton: the validation of the facts arrives in the next commit. */
public sealed interface MonthlyFact {

    String eventId();

    record MovementRecorded(String eventId, RecordedMovement movement) implements MonthlyFact {}

    record ReservationCancelled(String eventId, String reservationId, Instant occurredAt) implements MonthlyFact {}

    record OperationalCostRecorded(String eventId, String costId, Cents amount, Instant occurredAt)
            implements MonthlyFact {}

    record ReservationConfirmed(String eventId, String reservationId, Cents commercialValue, Instant occurredAt)
            implements MonthlyFact {}
}
