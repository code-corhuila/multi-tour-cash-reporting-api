package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.time.Instant;
import java.util.Objects;

/**
 * A fact that changes one month: a cash movement of this service or an event of another domain. Each carries the id of its
 * event, which is what makes the update idempotent (RN-03). The facts of other domains hold ids, amounts and instants only;
 * no personal datum (RN-16). The shape of the other domains' events is PROPUESTO until job 7 and their emitters exist.
 */
public sealed interface MonthlyFact {

    String eventId();

    /** A movement of the daily cash, counted in the month of the business date of its cash. */
    record MovementRecorded(String eventId, RecordedMovement movement) implements MonthlyFact {
        public MovementRecorded {
            requireId(eventId);
            Objects.requireNonNull(movement, "movement");
        }
    }

    /** A reservation cancelled; counted once per reservation in the month of the cancellation (DP-RM-A3). */
    record ReservationCancelled(String eventId, String reservationId, Instant occurredAt) implements MonthlyFact {
        public ReservationCancelled {
            requireId(eventId);
            requireId(reservationId);
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /** An operational cost of a service actually executed; counted once per cost id (DP-D3-1). */
    record OperationalCostRecorded(String eventId, String costId, Cents amount, Instant occurredAt)
            implements MonthlyFact {
        public OperationalCostRecorded {
            requireId(eventId);
            requireId(costId);
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (amount.value() < 0) {
                throw new IllegalArgumentException("A cost cannot be below zero");
            }
        }
    }

    /**
     * A reservation confirmed with its commercial value; it adds to the sales of the month of the confirmation and a later
     * cancellation removes none (DP-RM-B2). PROPUESTO (DP-RM-B3): the event does not carry the value yet.
     */
    record ReservationConfirmed(String eventId, String reservationId, Cents commercialValue, Instant occurredAt)
            implements MonthlyFact {
        public ReservationConfirmed {
            requireId(eventId);
            requireId(reservationId);
            Objects.requireNonNull(commercialValue, "commercialValue");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (commercialValue.value() < 0) {
                throw new IllegalArgumentException("A commercial value cannot be below zero");
            }
        }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("An id must not be blank");
        }
    }
}
