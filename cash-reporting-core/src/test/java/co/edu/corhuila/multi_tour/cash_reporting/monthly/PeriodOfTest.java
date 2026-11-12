package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.DEC_01;
import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.NOV_10;
import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.NOV_30;
import static co.edu.corhuila.multi_tour.cash_reporting.monthly.MonthlyMovements.movement;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.INCOME;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.MovementType.REFUND;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium.CASH;
import static co.edu.corhuila.multi_tour.cash_reporting.movement.PaymentMedium.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** D level, HU-CSH-003 T-02 and T-11: RN-05, RN-18, RN-20, RN-15 (the period format); AC-03.3, AC-03.14, AC-03.15, AC-03.16. */
class PeriodOfTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ReportedPeriod NOVEMBER = ReportedPeriod.of(YearMonth.of(2026, 11));
    private static final ReportedPeriod DECEMBER = ReportedPeriod.of(YearMonth.of(2026, 12));

    @Test
    void aMovementOfAnOpenDayCountsInItsMonth() {
        // The stored movement carries no state of its day: an open day is a day, and its month is the one of its date.
        assertThat(PeriodOf.movement(movement("m1", INCOME, CASH, 1_000))).isEqualTo(NOVEMBER);
    }

    @Test
    void aMovementBelongsToTheMonthOfTheBusinessDateInTheTenantTimeZone() {
        // 23:30 in Bogota on the last day of November is already December in UTC.
        Instant lateOnTheLastDay = Instant.parse("2026-12-01T04:30:00Z");

        assertThat(PeriodOf.moment(lateOnTheLastDay, BOGOTA)).isEqualTo(NOVEMBER);
        assertThat(PeriodOf.moment(lateOnTheLastDay, ZoneId.of("UTC"))).isEqualTo(DECEMBER);
    }

    @Test
    void aMovementBelongsToTheMonthOfItsCashWhateverTheStateOfTheDay() {
        assertThat(PeriodOf.movement(movement("m1", NOV_30, INCOME, CASH, 1_000, false))).isEqualTo(NOVEMBER);
        assertThat(PeriodOf.movement(movement("m2", NOV_30, INCOME, TRANSFER, 1_000, true))).isEqualTo(NOVEMBER);
        assertThat(PeriodOf.movement(movement("m3", DEC_01, INCOME, CASH, 1_000, false))).isEqualTo(DECEMBER);
    }

    @Test
    void aTransferAfterTheClosingCountsInTheMonthOfItsCash() {
        // Registered after the closing of the last day of November: it keeps the month of its cash.
        assertThat(PeriodOf.movement(movement("m1", NOV_30, INCOME, TRANSFER, 1_000, true))).isEqualTo(NOVEMBER);
    }

    @Test
    void aRefundCountsInThePeriodOfItsOutflow() {
        // The outflow is the refund movement itself: its month is the one of the cash where it was recorded.
        assertThat(PeriodOf.movement(movement("m1", DEC_01, REFUND, CASH, 1_000, false))).isEqualTo(DECEMBER);
        assertThat(PeriodOf.movement(movement("m2", NOV_30, REFUND, TRANSFER, 1_000, true))).isEqualTo(NOVEMBER);
    }

    @Test
    void aZoneChangeDoesNotMoveAMonthAlreadyReported() {
        var stored = movement("m1", NOV_30, INCOME, CASH, 1_000, false);
        Instant lateOnTheLastDay = Instant.parse("2026-12-01T04:30:00Z");

        // The stored date does not depend on any zone, while a new instant under another zone lands elsewhere.
        assertThat(PeriodOf.movement(stored)).isEqualTo(NOVEMBER);
        assertThat(PeriodOf.moment(lateOnTheLastDay, ZoneId.of("UTC"))).isNotEqualTo(PeriodOf.movement(stored));
    }

    @Test
    void periodsAreOrderedNewestFirstWhenSortedDescending() {
        List<ReportedPeriod> sorted = Stream.of(NOVEMBER, DECEMBER, ReportedPeriod.of(NOV_10))
                .sorted(Comparator.reverseOrder())
                .toList();

        assertThat(sorted).containsExactly(DECEMBER, NOVEMBER, NOVEMBER);
    }

    @Test
    void aPeriodIsWrittenAndReadAsYearAndMonth() {
        assertThat(NOVEMBER.toString()).isEqualTo("2026-11");
        assertThat(ReportedPeriod.parse("2026-11")).isEqualTo(NOVEMBER);
    }

    @Test
    void aPeriodThatIsNotYearAndMonthIsRefused() {
        for (String bad : new String[] {"2026-13", "2026-1", "26-11", "2026-11-01", "", " 2026-11", "nov-2026"}) {
            assertThatThrownBy(() -> ReportedPeriod.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> ReportedPeriod.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
