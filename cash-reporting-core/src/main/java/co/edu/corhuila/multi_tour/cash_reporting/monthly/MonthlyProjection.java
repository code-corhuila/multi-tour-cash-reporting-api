package co.edu.corhuila.multi_tour.cash_reporting.monthly;

import co.edu.corhuila.multi_tour.cash_reporting.error.CashRuleViolation;
import co.edu.corhuila.multi_tour.cash_reporting.money.Cents;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The stored state of one month of one tenant, as the projection keeps it: the cash components, the cancelled reservations
 * and the costs as sets by id (a repeated id changes nothing, the second guard after the processed event), and the sales.
 * A set never passes {@link #MAX_ELEMENTS}: the event that would pass it is refused whole, never truncated (AC-03.18).
 */
public record MonthlyProjection(
        MonthlyTotals totals, Set<String> cancelledReservations, Map<String, Cents> costs, Cents confirmedSales) {

    public static final int MAX_ELEMENTS = 10_000;

    public static final MonthlyProjection EMPTY = new MonthlyProjection(MonthlyTotals.EMPTY, Set.of(), Map.of(), null);

    public MonthlyProjection {
        Objects.requireNonNull(totals, "totals");
        cancelledReservations = Set.copyOf(cancelledReservations);
        costs = Map.copyOf(costs);
        if (cancelledReservations.size() > MAX_ELEMENTS || costs.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("A set of the month passes " + MAX_ELEMENTS + " elements");
        }
    }

    public MonthlyProjection withMovement(RecordedMovement movement) {
        return new MonthlyProjection(totals.plus(movement), cancelledReservations, costs, confirmedSales);
    }

    public MonthlyProjection withCancellation(String reservationId) {
        if (cancelledReservations.contains(reservationId)) {
            return this;
        }
        requireRoom(cancelledReservations.size(), "cancellations");
        Set<String> next = new LinkedHashSet<>(cancelledReservations);
        next.add(reservationId);
        return new MonthlyProjection(totals, next, costs, confirmedSales);
    }

    public MonthlyProjection withCost(String costId, Cents amount) {
        if (costs.containsKey(costId)) {
            return this;
        }
        requireRoom(costs.size(), "costs");
        Map<String, Cents> next = new LinkedHashMap<>(costs);
        next.put(costId, amount);
        return new MonthlyProjection(totals, cancelledReservations, next, confirmedSales);
    }

    /** A confirmation adds its value to the sales; the first one turns the unknown sales into a known figure. */
    public MonthlyProjection withSale(Cents commercialValue) {
        Cents next = confirmedSales == null ? commercialValue : confirmedSales.plus(commercialValue);
        return new MonthlyProjection(totals, cancelledReservations, costs, next);
    }

    public MonthlyPeriod toPeriod(ReportedPeriod period) {
        Cents costsTotal = costs.values().stream().reduce(Cents.ZERO, Cents::plus);
        return new MonthlyPeriod(period, totals, cancelledReservations.size(), costsTotal, confirmedSales);
    }

    private static void requireRoom(int size, String what) {
        if (size >= MAX_ELEMENTS) {
            throw new CashRuleViolation.RuleViolation("The month holds the maximum of " + MAX_ELEMENTS + " " + what);
        }
    }
}
