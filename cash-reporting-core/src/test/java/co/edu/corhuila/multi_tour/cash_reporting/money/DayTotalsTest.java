package co.edu.corhuila.multi_tour.cash_reporting.money;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType;
import co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium;
import java.util.List;
import org.junit.jupiter.api.Test;

class DayTotalsTest {

    private static CashMovement movement(MovementType type, PaymentMedium medium, long cents) {
        return new CashMovement(type, medium, Cents.amount(cents));
    }

    @Test
    void totalIsBasePlusIncomeMinusPaymentsExpensesAndRefunds() {
        DayView simple = DayTotals.compute(
                Cents.of(1000),
                List.of(
                        movement(MovementType.INCOME, PaymentMedium.CASH, 500),
                        movement(MovementType.OPERATIONAL_PAYMENT, PaymentMedium.CASH, 100),
                        movement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.TRANSFER, 200),
                        movement(MovementType.REFUND, PaymentMedium.TRANSFER, 50)));
        // 1000 + 500 - 100 - 200 - 50
        assertThat(simple.totalCents()).isEqualTo(Cents.of(1150));
        assertThat(simple.incomeCents()).isEqualTo(Cents.of(500));
        assertThat(simple.operationalPaymentsCents()).isEqualTo(Cents.of(100));
        assertThat(simple.expensesCents()).isEqualTo(Cents.of(200));
        assertThat(simple.refundsCents()).isEqualTo(Cents.of(50));

        // The portal demo day, in cents.
        DayView demo = DayTotals.compute(
                Cents.of(30_000_000L),
                List.of(
                        movement(MovementType.INCOME, PaymentMedium.TRANSFER, 129_000_000L),
                        movement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 25_000_000L),
                        movement(MovementType.REFUND, PaymentMedium.TRANSFER, 15_000_000L)));
        assertThat(demo.totalCents()).isEqualTo(Cents.of(119_000_000L));
        assertThat(demo.expectedCashCents()).isEqualTo(Cents.of(5_000_000L));
        assertThat(demo.netTransfersCents()).isEqualTo(Cents.of(114_000_000L));
    }

    @Test
    void aDayWithoutBaseCountsTheBaseAsZero() {
        DayView view = DayTotals.compute(
                null,
                List.of(
                        movement(MovementType.INCOME, PaymentMedium.TRANSFER, 500),
                        movement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.TRANSFER, 100)));
        assertThat(view.openingBaseCents()).isNull();
        assertThat(view.totalCents()).isEqualTo(Cents.of(400));
    }

    @Test
    void expectedCashIsBasePlusCashEntriesMinusCashExits() {
        DayView view = DayTotals.compute(
                Cents.of(1000),
                List.of(
                        movement(MovementType.INCOME, PaymentMedium.CASH, 300),
                        movement(MovementType.OPERATIONAL_PAYMENT, PaymentMedium.CASH, 100),
                        movement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 50),
                        movement(MovementType.REFUND, PaymentMedium.CASH, 70),
                        movement(MovementType.INCOME, PaymentMedium.TRANSFER, 999)));
        // 1000 + 300 - (100 + 50 + 70)
        assertThat(view.expectedCashCents()).isEqualTo(Cents.of(1080));
        assertThat(view.cashEntriesCents()).isEqualTo(Cents.of(300));
        assertThat(view.cashExitsCents()).isEqualTo(Cents.of(220));
    }

    @Test
    void expectedCashIsNullWithoutOperationalOpening() {
        DayView view = DayTotals.compute(null, List.of(movement(MovementType.INCOME, PaymentMedium.CASH, 100)));
        assertThat(view.expectedCashCents()).isNull();
        assertThat(view.totalCents()).isEqualTo(Cents.of(100));
    }

    @Test
    void netTransfersNeverChangeTheExpectedCash() {
        DayView cashOnly = DayTotals.compute(Cents.of(1000), List.of());
        DayView withTransfers = DayTotals.compute(
                Cents.of(1000),
                List.of(
                        movement(MovementType.INCOME, PaymentMedium.TRANSFER, 500),
                        movement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.TRANSFER, 200),
                        movement(MovementType.REFUND, PaymentMedium.TRANSFER, 100)));
        assertThat(cashOnly.expectedCashCents()).isEqualTo(Cents.of(1000));
        assertThat(withTransfers.expectedCashCents()).isEqualTo(cashOnly.expectedCashCents());
        // 500 - 200 - 100
        assertThat(withTransfers.netTransfersCents()).isEqualTo(Cents.of(200));
        assertThat(withTransfers.transferEntriesCents()).isEqualTo(Cents.of(500));
        assertThat(withTransfers.transferExitsCents()).isEqualTo(Cents.of(300));
    }

    @Test
    void aRefundIsCountedInRefundsNeverInExpenses() {
        DayView view = DayTotals.compute(
                Cents.ZERO,
                List.of(
                        movement(MovementType.REFUND, PaymentMedium.TRANSFER, 400),
                        movement(MovementType.OPERATIONAL_EXPENSE, PaymentMedium.CASH, 100)));
        assertThat(view.refundsCents()).isEqualTo(Cents.of(400));
        assertThat(view.expensesCents()).isEqualTo(Cents.of(100));
        // 0 - 100 - 400
        assertThat(view.totalCents()).isEqualTo(Cents.of(-500));
    }
}
