package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.Map;
import java.util.Set;

/** Red-phase skeleton: the behavior arrives in the next commit. */
public record MonthlyProjection(
        MonthlyTotals totals, Set<String> cancelledReservations, Map<String, Cents> costs, Cents confirmedSales) {

    public static final int MAX_ELEMENTS = 10_000;

    public static final MonthlyProjection EMPTY = new MonthlyProjection(MonthlyTotals.EMPTY, Set.of(), Map.of(), null);

    public MonthlyProjection withMovement(RecordedMovement movement) {
        throw new UnsupportedOperationException();
    }

    public MonthlyProjection withCancellation(String reservationId) {
        throw new UnsupportedOperationException();
    }

    public MonthlyProjection withCost(String costId, Cents amount) {
        throw new UnsupportedOperationException();
    }

    public MonthlyProjection withSale(Cents commercialValue) {
        throw new UnsupportedOperationException();
    }

    public MonthlyPeriod toPeriod(ReportedPeriod period) {
        throw new UnsupportedOperationException();
    }
}
