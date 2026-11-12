package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;

/**
 * The four values of the summary (AC-03.4): confirmed sales, the income of the period, the operational costs and the
 * cancellations. The sales are null while there is no value (G-SALES), never a false zero.
 */
public record PeriodSummary(
        ReportedPeriod period, Cents confirmedSales, Cents periodIncome, Cents operationalCosts, int cancellations) {

    public static PeriodSummary of(MonthlyPeriod figures) {
        return new PeriodSummary(
                figures.period(),
                figures.confirmedSales(),
                figures.totals().income(),
                figures.registeredOperationalCosts(),
                figures.registeredCancellations());
    }
}
