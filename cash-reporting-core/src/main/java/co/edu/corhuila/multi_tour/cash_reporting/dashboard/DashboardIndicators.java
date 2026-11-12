package co.edu.corhuila.multi_tour.cash_reporting.dashboard;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The six sets of the daily dashboard of a tenant, computed from the last known facts (RF-011; decided by default, DP-API-1):
 * pending payment, confirmed and confirmed with balance are the stock of the reservations in that state now; created and
 * cancelled are the flow of the local day of the tenant (the zone is the configured one, shared with the cash); the upcoming
 * services are the scheduled departures that start on that day (DP-DB4). A cancelled reservation is in none of the stock
 * sets. The numbers of the screen are the lengths of the sets.
 */
public record DashboardIndicators(
        BusinessDate reportDate,
        List<String> created,
        List<String> pendingPayment,
        List<String> confirmed,
        List<String> confirmedWithBalance,
        List<String> cancelled,
        List<String> upcomingServices) {

    private static final Set<String> WITH_BALANCE = Set.of("DEPOSIT_RECEIVED", "BALANCE_PENDING");

    public DashboardIndicators {
        Objects.requireNonNull(reportDate, "reportDate");
    }

    public static DashboardIndicators of(
            Collection<ReservationFact> reservations, Collection<DepartureFact> departures, BusinessDate day, ZoneId zone) {
        return new DashboardIndicators(
                day,
                ids(reservations, r -> r.createdAt() != null && BusinessDate.of(r.createdAt(), zone).equals(day)),
                ids(reservations, r -> r.status() == ReservationStatus.PENDING_PAYMENT),
                ids(reservations, r -> r.status() == ReservationStatus.CONFIRMED),
                ids(reservations, r -> r.status() == ReservationStatus.CONFIRMED && WITH_BALANCE.contains(r.financialSituation())),
                ids(reservations, r -> r.cancelledAt() != null && BusinessDate.of(r.cancelledAt(), zone).equals(day)),
                departures.stream()
                        .filter(d -> d.status() == DepartureStatus.SCHEDULED && d.scheduledStart() != null
                                && BusinessDate.of(d.scheduledStart(), zone).equals(day))
                        .map(DepartureFact::departureId)
                        .sorted()
                        .toList());
    }

    public DashboardCounts counts() {
        return new DashboardCounts(
                created.size(), pendingPayment.size(), confirmed.size(), confirmedWithBalance.size(), cancelled.size(),
                upcomingServices.size());
    }

    private static List<String> ids(Collection<ReservationFact> reservations, Predicate<ReservationFact> in) {
        return reservations.stream().filter(in).map(ReservationFact::reservationId).sorted().toList();
    }
}
