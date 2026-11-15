package co.edu.corhuila.multi_tour.cash_reporting.usecase.fake;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationFact;
import co.edu.corhuila.multi_tour.cash_reporting.port.DashboardFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The reservation and departure facts in memory, keyed by tenant first, as the tables are. */
public final class InMemoryDashboardFacts implements DashboardFacts {

    private final Map<String, Map<String, ReservationFact>> reservations = new HashMap<>();
    private final Map<String, Map<String, DepartureFact>> departures = new HashMap<>();

    @Override
    public Optional<ReservationFact> findReservation(String tenantId, String reservationId) {
        return Optional.ofNullable(reservations.getOrDefault(tenantId, Map.of()).get(reservationId));
    }

    @Override
    public void saveReservation(String tenantId, ReservationFact fact) {
        reservations.computeIfAbsent(tenantId, t -> new HashMap<>()).put(fact.reservationId(), fact);
    }

    @Override
    public List<ReservationFact> reservations(String tenantId) {
        return List.copyOf(reservations.getOrDefault(tenantId, Map.of()).values());
    }

    @Override
    public Optional<DepartureFact> findDeparture(String tenantId, String departureId) {
        return Optional.ofNullable(departures.getOrDefault(tenantId, Map.of()).get(departureId));
    }

    @Override
    public void saveDeparture(String tenantId, DepartureFact fact) {
        departures.computeIfAbsent(tenantId, t -> new HashMap<>()).put(fact.departureId(), fact);
    }

    @Override
    public List<DepartureFact> departures(String tenantId) {
        return List.copyOf(departures.getOrDefault(tenantId, Map.of()).values());
    }
}
