package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.movement;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.INCOME;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium.CASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D level, HU-CSH-003 T-01 and T-02: the figures of one period (AC-03.1, AC-03.2, AC-03.4, G-CONTRACT). */
class MonthlyPeriodTest {

    private static final ReportedPeriod NOVEMBER = ReportedPeriod.of(YearMonth.of(2026, 11));

    @Test
    void aPeriodWithoutMovementsIsZeroAndItsSalesAreUnknown() {
        MonthlyPeriod zero = MonthlyPeriod.zero(NOVEMBER);

        assertThat(zero.totals()).isEqualTo(MonthlyTotals.EMPTY);
        assertThat(zero.registeredCancellations()).isZero();
        assertThat(zero.registeredOperationalCosts()).isEqualTo(Cents.ZERO);
        // Decided by Fernanda: null when there is no value, never a false zero.
        assertThat(zero.confirmedSales()).isNull();
    }

    @Test
    void aPeriodKeepsTheValueOfItsSalesWhenThereIsOne() {
        MonthlyPeriod period = new MonthlyPeriod(
                NOVEMBER,
                MonthlyTotals.ofMovements(List.of(movement("m1", INCOME, CASH, 9_000))),
                2,
                Cents.of(3_000),
                Cents.of(70_000));

        assertThat(period.confirmedSales()).isEqualTo(Cents.of(70_000));
        assertThat(period.totals().income()).isEqualTo(Cents.of(9_000));
        assertThat(period.registeredCancellations()).isEqualTo(2);
        assertThat(period.registeredOperationalCosts()).isEqualTo(Cents.of(3_000));
    }

    @Test
    void aNegativeFigureOrAMissingPeriodIsRefused() {
        assertThatThrownBy(() -> new MonthlyPeriod(NOVEMBER, MonthlyTotals.EMPTY, 0, Cents.ZERO, Cents.of(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthlyPeriod(NOVEMBER, MonthlyTotals.EMPTY, -1, Cents.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthlyPeriod(NOVEMBER, MonthlyTotals.EMPTY, 0, Cents.of(-1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonthlyPeriod(null, MonthlyTotals.EMPTY, 0, Cents.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
