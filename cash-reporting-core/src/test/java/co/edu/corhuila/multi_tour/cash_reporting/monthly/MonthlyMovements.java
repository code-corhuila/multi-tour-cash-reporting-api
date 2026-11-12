package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RefundDetail;
import java.time.Instant;
import java.time.LocalDate;

/** Builders of stored movements for the monthly report tests. */
final class MonthlyMovements {

    static final BusinessDate NOV_10 = BusinessDate.of(LocalDate.of(2026, 11, 10));
    static final BusinessDate NOV_30 = BusinessDate.of(LocalDate.of(2026, 11, 30));
    static final BusinessDate DEC_01 = BusinessDate.of(LocalDate.of(2026, 12, 1));
    private static final Instant AT = Instant.parse("2026-11-10T15:00:00Z");

    private MonthlyMovements() {}

    static RecordedMovement movement(
            String id, BusinessDate date, MovementType type, PaymentMedium medium, long cents, boolean afterClosing) {
        RefundDetail refund = type == MovementType.REFUND ? new RefundDetail("reservation-1") : null;
        return new RecordedMovement(
                id, date, new CashMovement(type, medium, Cents.amount(cents)), "concept", afterClosing, AT, "user-1", refund);
    }

    static RecordedMovement movement(String id, MovementType type, PaymentMedium medium, long cents) {
        return movement(id, NOV_10, type, medium, cents, false);
    }

    /** A compensating movement: a REFUND linked to the income it reverses. */
    static RecordedMovement reversalOf(String id, RecordedMovement reversed) {
        return new RecordedMovement(
                id,
                reversed.date(),
                new CashMovement(MovementType.REFUND, reversed.movement().medium(), reversed.movement().amount()),
                "reversal",
                false,
                AT,
                "saga",
                new RefundDetail("reservation-1"),
                "reservation-1",
                reversed.id());
    }
}
