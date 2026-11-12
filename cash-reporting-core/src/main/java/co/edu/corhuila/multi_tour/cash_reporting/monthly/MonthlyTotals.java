package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.Collection;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public record MonthlyTotals(
        Cents cashIncome,
        Cents transferIncome,
        Cents operationalPayments,
        Cents operationalExpenses,
        Cents executedRefunds,
        int movementCount) {

    public static final MonthlyTotals EMPTY = null;

    public static MonthlyTotals ofMovements(Collection<RecordedMovement> movements) {
        throw new UnsupportedOperationException();
    }

    public Cents income() {
        throw new UnsupportedOperationException();
    }

    public Cents total() {
        throw new UnsupportedOperationException();
    }
}
