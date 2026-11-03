package co.edu.corhuila.multi_tour.cash_reporting.port;

import co.edu.corhuila.multi_tour.cash_reporting.calendar.BusinessDate;
import co.edu.corhuila.multi_tour.cash_reporting.movement.CashMovement;
import java.util.List;

/** Outbound port of the movements. Every method takes the tenant first: no query without it. */
public interface CashMovementRepository {

    List<CashMovement> findByDate(String tenantId, BusinessDate date);

    CashMovement save(String tenantId, BusinessDate date, CashMovement movement);
}
