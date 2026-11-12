package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.dashboard.DepartureFact;
import co.edu.corhuila.multi_tour.cash_reporting.dashboard.ReservationFact;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: the last known state of the reservations and the departures of a tenant (tables {@code reservation_state}
 * and {@code departure_state}). Every method takes the tenant first; the ids belong to another domain and are plain text.
 */
public interface DashboardFacts {

    Optional<ReservationFact> findReservation(String tenantId, String reservationId);

    void saveReservation(String tenantId, ReservationFact fact);

    List<ReservationFact> reservations(String tenantId);

    Optional<DepartureFact> findDeparture(String tenantId, String departureId);

    void saveDeparture(String tenantId, DepartureFact fact);

    List<DepartureFact> departures(String tenantId);
}
