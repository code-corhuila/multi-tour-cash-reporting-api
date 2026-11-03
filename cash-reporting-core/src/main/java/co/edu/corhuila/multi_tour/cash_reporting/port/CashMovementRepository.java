package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.List;
import java.util.Optional;

/** Outbound port of the movements. Every method takes the tenant first: no query without it. */
public interface CashMovementRepository {

    List<CashMovement> findByDate(String tenantId, BusinessDate date);

    Optional<RecordedMovement> findById(String tenantId, String id);

    RecordedMovement insert(String tenantId, RecordedMovement movement);
}
