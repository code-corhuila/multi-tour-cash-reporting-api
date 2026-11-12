package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The cash components of one month. The total is computed, never received (RN-01), and there is no field for a daily base
 * or a daily closing total (RN-02, GAP-011). Income splits into cash and transfer, so the two always add up to it (RN-11).
 * A compensating movement is counted as the movement it is (DP-RM-B1).
 */
public record MonthlyTotals(
        Cents cashIncome,
        Cents transferIncome,
        Cents operationalPayments,
        Cents operationalExpenses,
        Cents executedRefunds,
        int movementCount) {

    public static final MonthlyTotals EMPTY =
            new MonthlyTotals(Cents.ZERO, Cents.ZERO, Cents.ZERO, Cents.ZERO, Cents.ZERO, 0);

    public MonthlyTotals {
        for (Cents amount : new Cents[] {cashIncome, transferIncome, operationalPayments, operationalExpenses, executedRefunds}) {
            Objects.requireNonNull(amount, "amount");
            if (amount.value() < 0) {
                throw new IllegalArgumentException("An amount of the month cannot be below zero");
            }
        }
        if (movementCount < 0) {
            throw new IllegalArgumentException("A count cannot be below zero");
        }
    }

    /** Adds each movement once, by its id, however many times the same row is presented. */
    public static MonthlyTotals ofMovements(Collection<RecordedMovement> movements) {
        Map<String, RecordedMovement> once = new LinkedHashMap<>();
        movements.forEach(movement -> once.putIfAbsent(movement.id(), movement));
        MonthlyTotals totals = EMPTY;
        for (RecordedMovement movement : once.values()) {
            totals = totals.plus(movement);
        }
        return totals;
    }

    /** One more movement: the type says which amount grows, the medium splits only the income. */
    public MonthlyTotals plus(RecordedMovement recorded) {
        Cents amount = recorded.movement().amount();
        MovementType type = recorded.movement().type();
        boolean byTransfer = recorded.movement().medium() == PaymentMedium.TRANSFER;
        return new MonthlyTotals(
                type == MovementType.INCOME && !byTransfer ? cashIncome.plus(amount) : cashIncome,
                type == MovementType.INCOME && byTransfer ? transferIncome.plus(amount) : transferIncome,
                type == MovementType.OPERATIONAL_PAYMENT ? operationalPayments.plus(amount) : operationalPayments,
                type == MovementType.OPERATIONAL_EXPENSE ? operationalExpenses.plus(amount) : operationalExpenses,
                type == MovementType.REFUND ? executedRefunds.plus(amount) : executedRefunds,
                movementCount + 1);
    }

    public Cents income() {
        return cashIncome.plus(transferIncome);
    }

    /** Income minus operational payments, operational expenses and executed refunds; it may be negative. */
    public Cents total() {
        return income().minus(operationalPayments).minus(operationalExpenses).minus(executedRefunds);
    }
}
