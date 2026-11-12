package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public record MonthlyPeriod(
        ReportedPeriod period,
        MonthlyTotals totals,
        int registeredCancellations,
        Cents registeredOperationalCosts,
        Cents confirmedSales) {

    public static MonthlyPeriod zero(ReportedPeriod period) {
        throw new UnsupportedOperationException();
    }
}
