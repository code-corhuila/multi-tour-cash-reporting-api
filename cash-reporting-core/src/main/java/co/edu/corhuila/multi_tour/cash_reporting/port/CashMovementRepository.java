package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import co.edu.corhuila.multi_tour.cash_reporting.movement.RecordedMovement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Outbound port of the movements. Every method takes the tenant first: no query without it. */
public interface CashMovementRepository {

    List<CashMovement> findByDate(String tenantId, BusinessDate date);

    /** The movements of the given days in one read, for the figures of a page of days. */
    Map<BusinessDate, List<CashMovement>> findByDates(String tenantId, List<BusinessDate> dates);

    /** The stored movements of a day, most recent first. */
    Page<RecordedMovement> list(String tenantId, BusinessDate date, PageRequest request);

    Optional<RecordedMovement> findById(String tenantId, String id);

    /** The movement that compensates the given one, if any: a movement is reversed once. */
    Optional<RecordedMovement> findReversalOf(String tenantId, String movementId);

    RecordedMovement insert(String tenantId, RecordedMovement movement);
}
