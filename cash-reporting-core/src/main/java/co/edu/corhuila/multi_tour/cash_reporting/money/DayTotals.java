package co.edu.corhuila.multi_tour.cash_reporting.money;

import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import java.util.List;

/** Pure function of the base and the movements of a day. Nothing is stored; it is computed on every read. */
public final class DayTotals {

    private DayTotals() {}

    /**
     * @param openingBase the base of the operational opening, or null when the day has none
     * @param movements the movements of the day
     */
    public static DayView compute(Cents openingBase, List<CashMovement> movements) {
        Cents income = sum(movements, MovementType.INCOME);
        Cents payments = sum(movements, MovementType.OPERATIONAL_PAYMENT);
        Cents expenses = sum(movements, MovementType.OPERATIONAL_EXPENSE);
        Cents refunds = sum(movements, MovementType.REFUND);

        Cents base = openingBase == null ? Cents.ZERO : openingBase;
        Cents total = base.plus(income).minus(payments).minus(expenses).minus(refunds);

        Cents cashEntries = sum(movements, PaymentMedium.CASH, true);
        Cents cashExits = sum(movements, PaymentMedium.CASH, false);
        Cents transferEntries = sum(movements, PaymentMedium.TRANSFER, true);
        Cents transferExits = sum(movements, PaymentMedium.TRANSFER, false);

        Cents expectedCash = openingBase == null ? null : openingBase.plus(cashEntries).minus(cashExits);
        Cents netTransfers = transferEntries.minus(transferExits);

        return new DayView(
                openingBase,
                income,
                payments,
                expenses,
                refunds,
                total,
                cashEntries,
                cashExits,
                transferEntries,
                transferExits,
                expectedCash,
                netTransfers);
    }

    private static Cents sum(List<CashMovement> movements, MovementType type) {
        return movements.stream()
                .filter(movement -> movement.type() == type)
                .map(CashMovement::amount)
                .reduce(Cents.ZERO, Cents::plus);
    }

    private static Cents sum(List<CashMovement> movements, PaymentMedium medium, boolean entries) {
        return movements.stream()
                .filter(movement -> movement.medium() == medium && movement.type().isEntry() == entries)
                .map(CashMovement::amount)
                .reduce(Cents.ZERO, Cents::plus);
    }
}
