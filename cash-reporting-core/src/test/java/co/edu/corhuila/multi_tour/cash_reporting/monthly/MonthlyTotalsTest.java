package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.movement;
import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.reversalOf;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.INCOME;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.OPERATIONAL_EXPENSE;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.OPERATIONAL_PAYMENT;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.REFUND;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium.CASH;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayTotals;
import co.edu.corhuila.multi_tour.cash_reporting.money.DayView;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D level, HU-CSH-003 T-01: RN-01, RN-02, RN-11, RN-17, RN-19 and the edge cases 7 and 15 (AC-03.1, AC-03.3, AC-03.7, AC-03.15). */
class MonthlyTotalsTest {

    @Test
    void consolidatedTotalIsIncomeMinusPaymentsExpensesAndRefunds() {
        MonthlyTotals totals = MonthlyTotals.ofMovements(List.of(
                movement("m1", INCOME, CASH, 100_000),
                movement("m2", INCOME, TRANSFER, 50_000),
                movement("m3", OPERATIONAL_PAYMENT, CASH, 20_000),
                movement("m4", OPERATIONAL_EXPENSE, CASH, 5_000),
                movement("m5", REFUND, TRANSFER, 10_000)));

        assertThat(totals.income()).isEqualTo(Cents.of(150_000));
        assertThat(totals.operationalPayments()).isEqualTo(Cents.of(20_000));
        assertThat(totals.operationalExpenses()).isEqualTo(Cents.of(5_000));
        assertThat(totals.executedRefunds()).isEqualTo(Cents.of(10_000));
        assertThat(totals.total()).isEqualTo(Cents.of(115_000));
        assertThat(totals.movementCount()).isEqualTo(5);
    }

    @Test
    void theDailyBaseIsNeverPartOfTheMonthlyTotal() {
        List<RecordedMovement> stored = List.of(
                movement("m1", INCOME, CASH, 100_000), movement("m2", OPERATIONAL_PAYMENT, CASH, 30_000));
        List<CashMovement> asTheDayReadsThem =
                stored.stream().map(RecordedMovement::movement).toList();
        DayView dayWithBase = DayTotals.compute(Cents.of(500_000), asTheDayReadsThem);

        MonthlyTotals month = MonthlyTotals.ofMovements(stored);

        assertThat(dayWithBase.totalCents()).isEqualTo(Cents.of(570_000));
        assertThat(month.total()).isEqualTo(Cents.of(70_000));
        assertThat(month.income()).isEqualTo(Cents.of(100_000));
    }

    @Test
    void aClosingTotalIsNeverAddedAgain() {
        RecordedMovement income = movement("m1", INCOME, CASH, 100_000);
        RecordedMovement expense = movement("m2", OPERATIONAL_EXPENSE, CASH, 10_000);
        // A closed and reopened day reads its movements again: the same rows come twice.
        MonthlyTotals month = MonthlyTotals.ofMovements(List.of(income, expense, income, expense));

        assertThat(month.movementCount()).isEqualTo(2);
        assertThat(month.income()).isEqualTo(Cents.of(100_000));
        assertThat(month.total()).isEqualTo(Cents.of(90_000));
    }

    @Test
    void cashAndTransferIncomeAddUpToThePeriodIncome() {
        MonthlyTotals totals = MonthlyTotals.ofMovements(List.of(
                movement("m1", INCOME, CASH, 70_000),
                movement("m2", INCOME, TRANSFER, 30_000),
                movement("m3", INCOME, TRANSFER, 5_000)));

        assertThat(totals.cashIncome()).isEqualTo(Cents.of(70_000));
        assertThat(totals.transferIncome()).isEqualTo(Cents.of(35_000));
        assertThat(totals.income()).isEqualTo(totals.cashIncome().plus(totals.transferIncome()));
        assertThat(totals.movementCount()).isEqualTo(3);
    }

    @Test
    void aRefundByTransferCountsAsARefundAndNotAsTransferIncome() {
        MonthlyTotals totals = MonthlyTotals.ofMovements(List.of(movement("m1", REFUND, TRANSFER, 8_000)));

        assertThat(totals.executedRefunds()).isEqualTo(Cents.of(8_000));
        assertThat(totals.transferIncome()).isEqualTo(Cents.ZERO);
        assertThat(totals.total()).isEqualTo(Cents.of(-8_000));
    }

    @Test
    void aMonthWithMoreRefundsThanIncomeHasANegativeTotal() {
        MonthlyTotals totals = MonthlyTotals.ofMovements(
                List.of(movement("m1", INCOME, CASH, 10_000), movement("m2", REFUND, CASH, 25_000)));

        assertThat(totals.total()).isEqualTo(Cents.of(-15_000));
    }

    @Test
    void aReversalIsCountedAsTheMovementItIs() {
        RecordedMovement income = movement("m1", INCOME, TRANSFER, 40_000);
        RecordedMovement reversal = reversalOf("m2", income);

        MonthlyTotals totals = MonthlyTotals.ofMovements(List.of(income, reversal));

        assertThat(totals.income()).isEqualTo(Cents.of(40_000));
        assertThat(totals.executedRefunds()).isEqualTo(Cents.of(40_000));
        assertThat(totals.movementCount()).isEqualTo(2);
    }

    @Test
    void anIncomeAndItsReversalLeaveTheTotalUnchanged() {
        RecordedMovement other = movement("m0", INCOME, CASH, 12_000);
        RecordedMovement income = movement("m1", INCOME, CASH, 40_000);

        MonthlyTotals before = MonthlyTotals.ofMovements(List.of(other));
        MonthlyTotals after = MonthlyTotals.ofMovements(List.of(other, income, reversalOf("m2", income)));

        assertThat(after.total()).isEqualTo(before.total());
    }

    @Test
    void aMonthWithoutMovementsIsZero() {
        MonthlyTotals totals = MonthlyTotals.ofMovements(List.of());

        assertThat(totals).isEqualTo(MonthlyTotals.EMPTY);
        assertThat(totals.total()).isEqualTo(Cents.ZERO);
        assertThat(totals.movementCount()).isZero();
    }

    @Test
    void anAmountOrACountBelowZeroIsRefused() {
        assertThatThrownBy(() -> new MonthlyTotals(Cents.of(-1), Cents.ZERO, Cents.ZERO, Cents.ZERO, Cents.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthlyTotals(Cents.ZERO, Cents.ZERO, Cents.of(-1), Cents.ZERO, Cents.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthlyTotals(Cents.ZERO, Cents.ZERO, Cents.ZERO, Cents.ZERO, Cents.ZERO, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthlyTotals(null, Cents.ZERO, Cents.ZERO, Cents.ZERO, Cents.ZERO, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
