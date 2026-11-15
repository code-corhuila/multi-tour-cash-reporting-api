package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import java.util.Objects;

/**
 * Everything the service knows of one period: the cash components, the cancellations and the operational costs registered in
 * it, and the confirmed sales. The sales are null while no source gives a value (G-SALES): a false zero would be read as a
 * real one, so an unknown figure is never served as zero.
 */
public record MonthlyPeriod(
        ReportedPeriod period,
        MonthlyTotals totals,
        int registeredCancellations,
        Cents registeredOperationalCosts,
        Cents confirmedSales) {

    public MonthlyPeriod {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(registeredOperationalCosts, "registeredOperationalCosts");
        if (registeredCancellations < 0) {
            throw new IllegalArgumentException("A count cannot be below zero");
        }
        if (registeredOperationalCosts.value() < 0 || (confirmedSales != null && confirmedSales.value() < 0)) {
            throw new IllegalArgumentException("An amount of the month cannot be below zero");
        }
    }

    /** A period with no movements and no facts: zeros everywhere, and the sales still unknown. */
    public static MonthlyPeriod zero(ReportedPeriod period) {
        return new MonthlyPeriod(period, MonthlyTotals.EMPTY, 0, Cents.ZERO, null);
    }
}
